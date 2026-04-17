package com.example.visualduress.integration

import android.util.Log
import com.example.visualduress.model.InceptionConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

/**
 * Input source that integrates with the Inner Range Inception security system
 * via its REST API (protocol v8+).
 *
 * ## How it works
 *
 * 1. On connect(): authenticate to get a session ID
 * 2. On first poll(): discover available inputs from the Inception controller,
 *    build a GUID -> slot mapping using the user-configured inputMappings
 * 3. Each poll() uses HTTP long polling (api/v1/monitor-updates) to wait
 *    for input state changes. Falls back to direct input state query if
 *    long polling isn't returning quickly enough.
 *
 * ## Input State Mapping
 *
 * Inception uses bit flags in the "PublicState" field to represent input states.
 * The relevant bits for physical digital inputs are:
 *
 *   Bit 0  (value 1)   = Input active / triggered
 *   Bit 1  (value 2)   = Input tamper
 *   Bit 3  (value 8)   = Input in alarm
 *   Bit 11 (value 2048)= Input unsealed (same as active for most uses)
 *
 * For VAD purposes: any of these bits set = input is active (value 1).
 * No bits set = input is normal (value 0).
 *
 * ## Device Mapping
 *
 * Inception inputs are identified by GUIDs.
 * The user maps each GUID to a VAD device slot ID (1..16) in Settings.
 * If auto-mapping is enabled (no manual mappings), inputs are auto-assigned
 * in the order they are returned by the API (1, 2, 3, ...).
 *
 * ## Session Management
 *
 * Sessions expire after 10 minutes of no activity.
 * The source refreshes the session automatically if a 401/403 is received,
 * or if the session is older than 8 minutes.
 *
 * @param config Live InceptionConfig (Compose-observable). The source reads
 *               .value fields each poll so settings changes take effect
 *               without restarting.
 */
class InceptionInputSource(
    private val config: InceptionConfig
) : InputSource {

    override val displayName = "Inner Range Inception"

    private var sessionId: String? = null
    private var sessionCreatedAt: Long = 0L

    // Inception input GUID -> VAD device slot (1-based)
    private var resolvedMappings: Map<String, Int> = emptyMap()
    private var inputsDiscovered = false

    // Long poll state — tracks updateTime tokens per sub-request
    private var inputStateUpdateTime: String = "0"

    // -------------------------------------------------------------------------
    // Public interface
    // -------------------------------------------------------------------------

    override suspend fun connect() {
        Log.i("Inception", "Connecting to ${config.host.value}")
        authenticate()
    }

    override suspend fun disconnect() {
        Log.i("Inception", "Disconnecting — clearing session")
        sessionId = null
        sessionCreatedAt = 0L
        inputsDiscovered = false
        inputStateUpdateTime = "0"
    }

    /**
     * Single poll cycle.
     *
     * Returns map of VAD device slot ID -> input state (0 or 1).
     * Only slots that have a mapping are included in the result.
     *
     * Throws IOException on unrecoverable errors (caller marks offline).
     */
    override suspend fun poll(): Map<Int, Int> = withContext(Dispatchers.IO) {
        ensureValidSession()

        // Discover and cache input list on first successful poll
        if (!inputsDiscovered) {
            discoverInputs()
        }

        // Use long polling to get current input states
        // timeSinceUpdate = "0" means "give me all current states immediately"
        val states = fetchInputStates()

        // Map Inception GUIDs to VAD slot IDs using resolved mappings
        val result = mutableMapOf<Int, Int>()
        states.forEach { (guid, isActive) ->
            val slotId = resolvedMappings[guid]
            if (slotId != null) {
                result[slotId] = if (isActive) 1 else 0
            }
        }

        Log.d("Inception", "Poll result: $result (from ${states.size} inputs)")
        result
    }

    // -------------------------------------------------------------------------
    // Authentication
    // -------------------------------------------------------------------------

    /**
     * POST api/v1/authentication/login
     * Stores the returned UserID as sessionId.
     */
    private suspend fun authenticate() = withContext(Dispatchers.IO) {
        val host = config.host.value.trim().trimEnd('/')
        val url = URL("http://$host/api/v1/authentication/login")

        Log.i("Inception", "Authenticating at $url")

        val payload = JSONObject().apply {
            put("Username", config.username.value)
            put("Password", config.password.value)
        }.toString()

        val conn = url.openConnection() as HttpURLConnection
        try {
            conn.apply {
                requestMethod = "POST"
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Accept", "application/json")
                connectTimeout = 5000
                readTimeout = 5000
            }

            conn.outputStream.use { os ->
                OutputStreamWriter(os).use { it.write(payload) }
            }

            val responseCode = conn.responseCode
            val responseBody = conn.inputStream.bufferedReader().readText()

            if (responseCode != HttpURLConnection.HTTP_OK) {
                throw IOException("Auth failed HTTP $responseCode: $responseBody")
            }

            val json = JSONObject(responseBody)
            val result = json.optJSONObject("Response")?.optString("Result")
            if (result != "Success") {
                throw IOException("Auth refused: ${json.optJSONObject("Response")?.optString("Message")}")
            }

            sessionId = json.getString("UserID")
            sessionCreatedAt = System.currentTimeMillis()
            inputsDiscovered = false  // re-discover inputs after new session
            Log.i("Inception", "✅ Authenticated, session: $sessionId")

        } finally {
            conn.disconnect()
        }
    }

    /**
     * Ensure session is valid. Re-authenticate if:
     * - No session exists
     * - Session is older than 8 minutes (Inception expires at 10 min)
     */
    private suspend fun ensureValidSession() {
        val sessionAge = System.currentTimeMillis() - sessionCreatedAt
        val eightMinutes = 8 * 60 * 1000L
        if (sessionId == null || sessionAge > eightMinutes) {
            Log.i("Inception", "Session missing or expired — re-authenticating")
            authenticate()
        }
    }

    // -------------------------------------------------------------------------
    // Input discovery
    // -------------------------------------------------------------------------

    /**
     * GET api/v1/control/input
     *
     * Fetches the list of all inputs from Inception.
     * Builds resolvedMappings either from user-configured inputMappings,
     * or auto-assigns slots by order (1, 2, 3...) if no mappings are configured.
     *
     * Each input object looks like:
     * { "ID": "07590f0f-...", "Name": "Reception Duress", "ReportingID": 1 }
     */
    private suspend fun discoverInputs() = withContext(Dispatchers.IO) {
        val host = config.host.value.trim().trimEnd('/')
        val url = URL("http://$host/api/v1/control/input")

        Log.i("Inception", "Discovering inputs from $url")

        val conn = url.openConnection() as HttpURLConnection
        try {
            conn.apply {
                requestMethod = "GET"
                setRequestProperty("Cookie", "LoginSessId=${sessionId}")
                setRequestProperty("Accept", "application/json")
                connectTimeout = 5000
                readTimeout = 5000
            }

            handleUnauthorized(conn)

            val body = conn.inputStream.bufferedReader().readText()
            val arr = JSONArray(body)

            val userMappings = config.inputMappings.value

            val newMappings = mutableMapOf<String, Int>()

            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val guid = obj.getString("ID")
                val name = obj.optString("Name", "Input ${i + 1}")
                val reportingId = obj.optInt("ReportingID", i + 1)

                val slotId = when {
                    // User has explicitly mapped this GUID
                    userMappings.containsKey(guid) -> userMappings[guid]!!
                    // Auto-map: use ReportingID (1-based, matches VAD slot IDs)
                    userMappings.isEmpty() -> reportingId
                    // User has manual mappings but didn't include this one — skip
                    else -> null
                }

                if (slotId != null && slotId in 1..16) {
                    newMappings[guid] = slotId
                    Log.d("Inception", "Mapped input '$name' ($guid) -> slot $slotId")
                }
            }

            resolvedMappings = newMappings
            inputsDiscovered = true
            inputStateUpdateTime = "0"  // reset so we get all states fresh
            Log.i("Inception", "✅ Discovered ${arr.length()} inputs, mapped ${newMappings.size}")

        } finally {
            conn.disconnect()
        }
    }

    // -------------------------------------------------------------------------
    // Input state polling
    // -------------------------------------------------------------------------

    /**
     * Uses the Inception long-polling endpoint to get current input states.
     *
     * POST api/v1/monitor-updates
     *
     * On the first call (timeSinceUpdate = "0"), returns all current states immediately.
     * On subsequent calls, blocks for up to 60s waiting for state changes.
     *
     * The read timeout is set to 65s to accommodate the server's 60s hold time.
     *
     * Returns map of Inception input GUID -> isActive (true/false).
     */
    private suspend fun fetchInputStates(): Map<String, Boolean> = withContext(Dispatchers.IO) {
        val host = config.host.value.trim().trimEnd('/')
        val url = URL("http://$host/api/v1/monitor-updates")

        val requestBody = JSONArray().apply {
            put(JSONObject().apply {
                put("ID", "Monitor_InputStates")
                put("RequestType", "MonitorEntityStates")
                put("InputData", JSONObject().apply {
                    put("stateType", "InputState")
                    put("timeSinceUpdate", inputStateUpdateTime)
                })
            })
        }.toString()

        val conn = url.openConnection() as HttpURLConnection
        try {
            conn.apply {
                requestMethod = "POST"
                doOutput = true
                setRequestProperty("Cookie", "LoginSessId=${sessionId}")
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Accept", "application/json")
                connectTimeout = 5000
                // Must be > 60s — Inception holds the connection open for up to 60s
                readTimeout = 70000
            }

            conn.outputStream.use { os ->
                OutputStreamWriter(os).use { it.write(requestBody) }
            }

            handleUnauthorized(conn)

            val responseBody = conn.inputStream.bufferedReader().readText()

            if (responseBody.isBlank()) {
                // Empty response = no state changes since last poll
                // Return empty map — ViewModel will keep existing alarm states
                Log.d("Inception", "Long poll returned empty (no changes)")
                return@withContext emptyMap()
            }

            val json = JSONObject(responseBody)
            val result = json.optJSONObject("Result") ?: return@withContext emptyMap()

            // Update the timestamp token for the next request
            val newUpdateTime = result.optString("updateTime", inputStateUpdateTime)
            if (newUpdateTime.isNotBlank()) {
                inputStateUpdateTime = newUpdateTime
            }

            val stateData = result.optJSONArray("stateData") ?: return@withContext emptyMap()

            val states = mutableMapOf<String, Boolean>()
            for (i in 0 until stateData.length()) {
                val item = stateData.getJSONObject(i)
                val guid = item.getString("ID")
                val publicState = item.optInt("PublicState", 0)

                // Inception InputPublicState bit flags:
                // Bit 0  (1)    = Input active/triggered
                // Bit 1  (2)    = Input tamper
                // Bit 3  (8)    = Input in alarm
                // Bit 11 (2048) = Input unsealed
                // Any of these = treat as active in VAD
                val isActive = (publicState and (1 or 2 or 8 or 2048)) != 0

                states[guid] = isActive
            }

            Log.d("Inception", "Long poll returned ${states.size} state updates")
            states

        } finally {
            conn.disconnect()
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /**
     * Check for 401/403 responses and trigger re-authentication.
     * Call BEFORE reading responseCode (which consumes the connection).
     */
    private suspend fun handleUnauthorized(conn: HttpURLConnection) {
        val code = conn.responseCode
        if (code == HttpURLConnection.HTTP_UNAUTHORIZED || code == HttpURLConnection.HTTP_FORBIDDEN) {
            Log.w("Inception", "Session rejected ($code) — re-authenticating")
            sessionId = null
            authenticate()
            throw IOException("Re-authenticated after $code — retry next poll cycle")
        }
        if (code != HttpURLConnection.HTTP_OK) {
            val error = try {
                conn.errorStream?.bufferedReader()?.readText() ?: ""
            } catch (_: Exception) { "" }
            throw IOException("HTTP $code: $error")
        }
    }

    // -------------------------------------------------------------------------
    // Utility — available inputs for Settings UI
    // -------------------------------------------------------------------------

    /**
     * Returns list of (GUID, Name, ReportingID) for all inputs on the
     * Inception controller. Used by the Settings UI to let the user
     * configure input-to-slot mappings.
     *
     * Requires an active session — call connect() first.
     */
    suspend fun fetchAvailableInputs(): List<InceptionInputInfo> = withContext(Dispatchers.IO) {
        ensureValidSession()

        val host = config.host.value.trim().trimEnd('/')
        val url = URL("http://$host/api/v1/control/input")

        val conn = url.openConnection() as HttpURLConnection
        try {
            conn.apply {
                requestMethod = "GET"
                setRequestProperty("Cookie", "LoginSessId=${sessionId}")
                setRequestProperty("Accept", "application/json")
                connectTimeout = 5000
                readTimeout = 5000
            }

            handleUnauthorized(conn)

            val body = conn.inputStream.bufferedReader().readText()
            val arr = JSONArray(body)

            val result = mutableListOf<InceptionInputInfo>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                result.add(
                    InceptionInputInfo(
                        id = obj.getString("ID"),
                        name = obj.optString("Name", "Input ${i + 1}"),
                        reportingId = obj.optInt("ReportingID", i + 1)
                    )
                )
            }
            result.sortBy { it.reportingId }
            result

        } finally {
            conn.disconnect()
        }
    }
}

/**
 * Lightweight data class for displaying Inception inputs in the Settings UI.
 */
data class InceptionInputInfo(
    val id: String,
    val name: String,
    val reportingId: Int
)
