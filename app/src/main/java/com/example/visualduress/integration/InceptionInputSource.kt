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
import java.security.SecureRandom
import java.security.cert.X509Certificate
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

/**
 * Input source that integrates with the Inner Range Inception security system
 * via its REST API (protocol v8+).
 *
 * Supports both local IP and SkyTunnel URLs:
 *   Local:     192.168.1.100  or  http://192.168.1.100
 *   SkyTunnel: https://skytunnel.com.au/Inception/IN81819021
 *
 * SkyTunnel 307 redirects are followed automatically.
 */
class InceptionInputSource(
    private val config: InceptionConfig
) : InputSource {

    override val displayName = "Inner Range Inception"

    private var sessionId: String? = null
    private var sessionCreatedAt: Long = 0L
    private var resolvedMappings: Map<String, Int> = emptyMap()
    private var inputsDiscovered = false
    private var inputStateUpdateTime: String = "0"

    // Maps slot ID -> Inception input name for ViewModel to use
    var slotNames: Map<Int, String> = emptyMap()
        private set

    // -------------------------------------------------------------------------
    // URL helper — supports local IP or full https:// SkyTunnel URL
    // -------------------------------------------------------------------------

    private fun baseUrl(): String {
        val host = config.host.value.trim().trimEnd('/')
        return when {
            host.startsWith("http://")  -> host
            host.startsWith("https://") -> host
            else -> "http://$host"
        }
    }

    // -------------------------------------------------------------------------
    // SSL — trust all certificates for SkyTunnel HTTPS
    // -------------------------------------------------------------------------

    /**
     * Trust manager that accepts all certificates.
     * Required for SkyTunnel which uses a certificate chain that Android
     * doesn't trust by default.
     * This is acceptable here because:
     * 1. We are connecting to a known fixed domain (skytunnel.com.au)
     * 2. The Inception API uses username/password authentication
     * 3. This is a commercial kiosk app on a controlled network
     */
    private val trustAllCerts = arrayOf<TrustManager>(object : X509TrustManager {
        override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {}
        override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {}
        override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
    })

    private val trustAllSslContext: SSLContext by lazy {
        SSLContext.getInstance("TLS").apply {
            init(null, trustAllCerts, SecureRandom())
        }
    }

    private val trustAllHostnameVerifier = javax.net.ssl.HostnameVerifier { _, _ -> true }

    private fun applyTrustAll(conn: HttpURLConnection) {
        if (conn is HttpsURLConnection) {
            conn.sslSocketFactory = trustAllSslContext.socketFactory
            conn.hostnameVerifier = trustAllHostnameVerifier
        }
    }

    /**
     * Resolve SkyTunnel redirects using a HEAD request with trust-all SSL.
     */
    private fun resolveUrl(urlString: String): String {
        return try {
            var currentUrl = urlString
            var redirectCount = 0
            while (redirectCount < 5) {
                val conn = URL(currentUrl).openConnection() as HttpURLConnection
                conn.apply {
                    requestMethod = "HEAD"
                    instanceFollowRedirects = false
                    connectTimeout = 8000
                    readTimeout = 5000
                }
                applyTrustAll(conn)
                val code = conn.responseCode
                if (code == 301 || code == 302 || code == 307 || code == 308) {
                    val location = conn.getHeaderField("Location") ?: break
                    conn.disconnect()
                    currentUrl = location
                    redirectCount++
                    Log.d("Inception", "Resolved redirect -> $currentUrl")
                } else {
                    conn.disconnect()
                    break
                }
            }
            currentUrl
        } catch (e: Exception) {
            Log.w("Inception", "Redirect resolution failed, using original URL: ${e.message}")
            urlString
        }
    }

    private fun openConnection(urlString: String): HttpURLConnection {
        val conn = URL(urlString).openConnection() as HttpURLConnection
        applyTrustAll(conn)
        conn.instanceFollowRedirects = true
        return conn
    }

    fun resetDiscovery() {
        inputsDiscovered = false
        inputStateUpdateTime = "0"
        Log.d("Inception", "Discovery reset — will re-sync on next poll")
    }

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

    override suspend fun poll(): Map<Int, Int> = withContext(Dispatchers.IO) {
        ensureValidSession()

        if (!inputsDiscovered) {
            discoverInputs()
        }

        val states = fetchInputStates()

        // Return raw PublicState values so ViewModel can apply
        // per-device alarm logic (alarmStateOnly toggle)
        val result = mutableMapOf<Int, Int>()
        states.forEach { (guid, publicState) ->
            val slotId = resolvedMappings[guid]
            if (slotId != null) {
                result[slotId] = publicState
            }
        }

        Log.d("Inception", "Poll result: $result (from ${states.size} inputs)")
        result
    }

    // -------------------------------------------------------------------------
    // Authentication
    // -------------------------------------------------------------------------

    private suspend fun authenticate() = withContext(Dispatchers.IO) {
        val url = resolveUrl("${baseUrl()}/api/v1/authentication/login")
        Log.i("Inception", "Authenticating at $url")

        val payload = JSONObject().apply {
            put("Username", config.username.value)
            put("Password", config.password.value)
        }.toString()

        val conn = openConnection(url)
        try {
            conn.apply {
                requestMethod = "POST"
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Accept", "application/json")
                connectTimeout = 8000
                readTimeout = 8000
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
            inputsDiscovered = false
            Log.i("Inception", "✅ Authenticated, session: $sessionId")

        } finally {
            conn.disconnect()
        }
    }

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

    private suspend fun discoverInputs() = withContext(Dispatchers.IO) {
        val url = "${baseUrl()}/api/v1/control/input"
        Log.i("Inception", "Discovering inputs from $url")

        val conn = openConnection(url)
        try {
            conn.apply {
                requestMethod = "GET"
                setRequestProperty("Cookie", "LoginSessId=${sessionId}")
                setRequestProperty("Accept", "application/json")
                readTimeout = 8000
            }

            handleUnauthorized(conn)

            val body = conn.inputStream.bufferedReader().readText()
            val arr = JSONArray(body)
            val userMappings = config.inputMappings.value

            val newMappings = mutableMapOf<String, Int>()
            val newSlotNames = mutableMapOf<Int, String>()

            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val guid = obj.getString("ID")
                val name = obj.optString("Name", "Input ${i + 1}")

                // Inception NEVER auto-maps — all inputs must be manually assigned
                val slotId = userMappings[guid]

                if (slotId != null && slotId in 1..32) {
                    newMappings[guid] = slotId
                    newSlotNames[slotId] = name
                    Log.d("Inception", "Mapped '$name' ($guid) -> slot $slotId")
                }
            }

            resolvedMappings = newMappings
            slotNames = newSlotNames
            inputsDiscovered = true
            inputStateUpdateTime = "0"
            Log.i("Inception", "✅ Discovered ${arr.length()} inputs, mapped ${newMappings.size}")

        } finally {
            conn.disconnect()
        }
    }

    // -------------------------------------------------------------------------
    // Input state polling (long poll)
    // -------------------------------------------------------------------------

    private suspend fun fetchInputStates(): Map<String, Int> = withContext(Dispatchers.IO) {
        val url = "${baseUrl()}/api/v1/monitor-updates"

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

        val conn = openConnection(url)
        try {
            conn.apply {
                requestMethod = "POST"
                doOutput = true
                setRequestProperty("Cookie", "LoginSessId=${sessionId}")
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Accept", "application/json")
                connectTimeout = 8000
                // Must be > 60s — Inception holds the connection open for up to 60s
                readTimeout = 70000
            }

            conn.outputStream.use { os ->
                OutputStreamWriter(os).use { it.write(requestBody) }
            }

            handleUnauthorized(conn)

            val responseBody = conn.inputStream.bufferedReader().readText()

            if (responseBody.isBlank()) {
                Log.d("Inception", "Long poll returned empty (no changes)")
                return@withContext emptyMap()
            }

            val json = JSONObject(responseBody)
            val result = json.optJSONObject("Result") ?: return@withContext emptyMap()

            val newUpdateTime = result.optString("updateTime", inputStateUpdateTime)
            if (newUpdateTime.isNotBlank()) {
                inputStateUpdateTime = newUpdateTime
            }

            val stateData = result.optJSONArray("stateData") ?: return@withContext emptyMap()

            val states = mutableMapOf<String, Int>()
            for (i in 0 until stateData.length()) {
                val item = stateData.getJSONObject(i)
                val guid = item.getString("ID")
                val publicState = item.optInt("PublicState", 0)
                // Return raw PublicState — ViewModel applies per-device alarm logic
                states[guid] = publicState
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

    suspend fun fetchAvailableInputs(): List<InceptionInputInfo> = withContext(Dispatchers.IO) {
        ensureValidSession()

        val url = "${baseUrl()}/api/v1/control/input"
        val conn = openConnection(url)
        try {
            conn.apply {
                requestMethod = "GET"
                setRequestProperty("Cookie", "LoginSessId=${sessionId}")
                setRequestProperty("Accept", "application/json")
                readTimeout = 8000
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

data class InceptionInputInfo(
    val id: String,
    val name: String,
    val reportingId: Int
)