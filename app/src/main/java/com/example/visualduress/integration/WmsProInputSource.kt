package com.example.visualduress.integration

import android.util.Log
import com.example.visualduress.model.WmsProConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * Input source for Tecom WMS Pro security systems via REST API v2.
 *
 * ## How it works
 *
 * 1. On connect(): validates the bearer token with a test call
 * 2. On first poll(): discovers available devices and builds UID → slot mapping
 * 3. Each poll(): calls GetDevicesStatus for all mapped device UIDs
 *    and evaluates the returned eventCodes to determine active/normal state
 *
 * ## Authentication
 * WMS Pro uses a pre-generated Bearer token — no login call required.
 * The token is entered directly by the installer in Settings → Comms.
 *
 * ## Device mapping
 * WMS Pro devices are identified by UIDs (e.g. "2.1812.1").
 * These must be manually mapped to VAD device slots (1–32) by the installer.
 * Device names are automatically pulled from the WMS Pro device name field.
 *
 * ## Alarm event codes
 * WMS Pro uses integer eventCodes to describe device state.
 * VAD treats the following as ACTIVE (alarm):
 *   3   = Alarm
 *   9   = Tamper
 *   11  = Secure alarm
 *   20  = Multibreak alarm
 *   37  = User duress
 *   123 = Local alarm
 *   173 = Exit alarm
 *   177 = Suspicion button activated
 *   195 = Duress code entered
 *   217 = Dead man alarm
 *
 * All other codes (including 4=Alarm restored, 5=Secured, 126=Sealed,
 * 128=Normal) are treated as NORMAL (not active).
 *
 * ## Polling
 * WMS Pro has no long-poll/push mechanism. VAD polls every 3 seconds
 * using GetDevicesStatus for all mapped device UIDs in a single call.
 */
class WmsProInputSource(
    private val config: WmsProConfig
) : InputSource {

    override val displayName = "Tecom WMS Pro"

    // Maps WMS Pro device UID -> VAD slot (1-based)
    private var resolvedMappings: Map<String, Int> = emptyMap()
    private var devicesDiscovered = false

    // Maps slot ID -> device name (from WMS Pro)
    var slotNames: Map<Int, String> = emptyMap()
        private set

    // Event codes that mean the device is in an ACTIVE/ALARM state
    private val alarmEventCodes = setOf(
        3,   // Alarm
        9,   // Tamper
        11,  // Secure alarm
        20,  // Multibreak alarm
        37,  // User duress
        123, // Local alarm
        173, // Exit alarm
        177, // Suspicion button activated
        195, // Duress code entered
        217  // Dead man alarm
    )

    // -------------------------------------------------------------------------
    // URL helper
    // -------------------------------------------------------------------------

    private fun baseUrl(): String = config.host.value.trim().trimEnd('/')

    private fun openConnection(urlString: String): HttpURLConnection {
        val conn = URL(urlString).openConnection() as HttpURLConnection
        conn.apply {
            setRequestProperty("Authorization", "Bearer ${config.bearerToken.value.trim()}")
            setRequestProperty("Accept", "application/json")
            connectTimeout = 8000
        }
        return conn
    }

    // -------------------------------------------------------------------------
    // Public interface
    // -------------------------------------------------------------------------

    override suspend fun connect(): Unit = withContext(Dispatchers.IO) {
        Log.i("WmsPro", "Connecting to ${config.host.value}")
        val url = "${baseUrl()}/api/v2/services/app/Wmspro3rdParty/GetDevicesForControl?type=3"
        val conn = openConnection(url)
        try {
            conn.requestMethod = "GET"
            conn.readTimeout = 8000
            val code = conn.responseCode
            if (code == HttpURLConnection.HTTP_UNAUTHORIZED) {
                throw IOException("Invalid bearer token — check credentials")
            }
            if (code != HttpURLConnection.HTTP_OK) {
                throw IOException("Connect failed HTTP $code")
            }
            Log.i("WmsPro", "✅ Connected to WMS Pro")
        } finally {
            conn.disconnect()
        }
    }

    override suspend fun disconnect() {
        Log.i("WmsPro", "Disconnecting")
        devicesDiscovered = false
        resolvedMappings = emptyMap()
        slotNames = emptyMap()
    }

    override suspend fun poll(): Map<Int, Int> = withContext(Dispatchers.IO) {
        if (!devicesDiscovered) {
            discoverDevices()
        }

        if (resolvedMappings.isEmpty()) {
            Log.d("WmsPro", "No mapped devices — nothing to poll")
            return@withContext emptyMap()
        }

        fetchDeviceStatuses()
    }

    // -------------------------------------------------------------------------
    // Device discovery
    // -------------------------------------------------------------------------

    /**
     * Fetches all Input devices from WMS Pro (type=3) and builds
     * the UID -> slot mapping from user-configured mappings.
     * Also populates slotNames for the ViewModel to use.
     *
     * GET /api/v2/services/app/Wmspro3rdParty/GetDevicesForControl?type=3
     *
     * Response:
     * {
     *   "result": [
     *     { "id": "2.1812.1", "name": "Reception Duress", "type": 3 }
     *   ],
     *   "success": true
     * }
     */
    private suspend fun discoverDevices() = withContext(Dispatchers.IO) {
        val url = "${baseUrl()}/api/v2/services/app/Wmspro3rdParty/GetDevicesForControl?type=3"
        Log.i("WmsPro", "Discovering devices from $url")

        val conn = openConnection(url)
        try {
            conn.requestMethod = "GET"
            conn.readTimeout = 8000

            val code = conn.responseCode
            if (code == HttpURLConnection.HTTP_UNAUTHORIZED) {
                throw IOException("Invalid bearer token")
            }
            if (code != HttpURLConnection.HTTP_OK) {
                throw IOException("Discovery failed HTTP $code")
            }

            val body = conn.inputStream.bufferedReader().readText()
            val json = JSONObject(body)

            if (!json.optBoolean("success", false)) {
                throw IOException("WMS Pro returned success=false during discovery")
            }

            val resultArr = json.optJSONArray("result") ?: run {
                Log.w("WmsPro", "No result array in discovery response")
                devicesDiscovered = true
                return@withContext
            }

            val userMappings = config.deviceMappings.value
            val newMappings  = mutableMapOf<String, Int>()
            val newSlotNames = mutableMapOf<Int, String>()

            for (i in 0 until resultArr.length()) {
                val obj  = resultArr.getJSONObject(i)
                val uid  = obj.optString("id",   "").ifEmpty { obj.optString("deviceUid", "") }
                val name = obj.optString("name", "Device ${i + 1}")

                val slotId = userMappings[uid]
                if (slotId != null && slotId in 1..32) {
                    newMappings[uid]      = slotId
                    newSlotNames[slotId]  = name
                    Log.d("WmsPro", "Mapped '$name' ($uid) -> slot $slotId")
                }
            }

            resolvedMappings = newMappings
            slotNames        = newSlotNames
            devicesDiscovered = true
            Log.i("WmsPro", "✅ Discovered ${resultArr.length()} devices, mapped ${newMappings.size}")

        } finally {
            conn.disconnect()
        }
    }

    // -------------------------------------------------------------------------
    // Status polling
    // -------------------------------------------------------------------------

    /**
     * GET GetDevicesStatus for all mapped device UIDs in one call.
     *
     * GET /api/v2/services/app/Wmspro3rdParty/GetDevicesStatus?DeviceUids=uid1&DeviceUids=uid2...
     *
     * Returns Map<slot, publicState> where publicState is the raw eventCode
     * bitmask equivalent — we return the highest-priority eventCode so the
     * ViewModel can apply the same per-device alarmStateOnly logic.
     *
     * For WMS Pro the "publicState" equivalent we return is:
     *   - The lowest eventCode present in the statuses array (most alarm-relevant)
     *   - If any alarmEventCode is present → return that code
     *   - Otherwise → return 0 (normal)
     */
    private suspend fun fetchDeviceStatuses(): Map<Int, Int> = withContext(Dispatchers.IO) {
        val uids = resolvedMappings.keys.toList()
        if (uids.isEmpty()) return@withContext emptyMap()

        // Build query string: ?DeviceUids=x&DeviceUids=y&...
        val queryParams = uids.joinToString("&") { "DeviceUids=${it}" }
        val url = "${baseUrl()}/api/v2/services/app/Wmspro3rdParty/GetDevicesStatus?$queryParams"

        Log.d("WmsPro", "Polling status for ${uids.size} devices")

        val conn = openConnection(url)
        try {
            conn.requestMethod = "GET"
            conn.readTimeout = 8000

            val code = conn.responseCode
            if (code == HttpURLConnection.HTTP_UNAUTHORIZED) {
                devicesDiscovered = false  // force re-auth on next poll
                throw IOException("Bearer token rejected — will retry")
            }
            if (code != HttpURLConnection.HTTP_OK) {
                throw IOException("GetDevicesStatus failed HTTP $code")
            }

            val body = conn.inputStream.bufferedReader().readText()
            val json = JSONObject(body)

            if (!json.optBoolean("success", false)) {
                val error = json.optJSONObject("error")?.optString("message") ?: "unknown"
                throw IOException("WMS Pro error: $error")
            }

            val resultArr = json.optJSONArray("result") ?: return@withContext emptyMap()
            val result = mutableMapOf<Int, Int>()

            for (i in 0 until resultArr.length()) {
                val deviceObj = resultArr.getJSONObject(i)
                val uid       = deviceObj.optString("deviceUid", "")
                val slotId    = resolvedMappings[uid] ?: continue
                val statuses  = deviceObj.optJSONArray("statuses")

                // Find the most alarm-relevant eventCode
                var activeCode = 0
                if (statuses != null) {
                    for (j in 0 until statuses.length()) {
                        val eventCode = statuses.getJSONObject(j).optInt("eventCode", 0)
                        if (eventCode in alarmEventCodes) {
                            activeCode = eventCode
                            break  // found an alarm code — no need to check further
                        }
                    }
                }

                // Return the raw eventCode so ViewModel can apply alarmStateOnly logic
                // 0 = normal, any alarmEventCode = active
                result[slotId] = activeCode
                Log.d("WmsPro", "Slot $slotId ($uid): eventCode=$activeCode")
            }

            Log.d("WmsPro", "Poll complete — ${result.size} device states")
            result

        } finally {
            conn.disconnect()
        }
    }

    // -------------------------------------------------------------------------
    // Settings UI helper — fetch available devices for manual mapping
    // -------------------------------------------------------------------------

    /**
     * Returns all Input devices (type=3) from WMS Pro for the mapping UI.
     * Called when the user taps "Load Devices from Controller".
     */
    suspend fun fetchAvailableDevices(): List<WmsProDeviceInfo> = withContext(Dispatchers.IO) {
        val url = "${baseUrl()}/api/v2/services/app/Wmspro3rdParty/GetDevicesForControl?type=3"
        val conn = openConnection(url)
        try {
            conn.requestMethod = "GET"
            conn.readTimeout = 8000

            val code = conn.responseCode
            if (code == HttpURLConnection.HTTP_UNAUTHORIZED) throw IOException("Invalid bearer token")
            if (code != HttpURLConnection.HTTP_OK) throw IOException("HTTP $code")

            val body = conn.inputStream.bufferedReader().readText()
            val json = JSONObject(body)

            if (!json.optBoolean("success", false)) throw IOException("success=false")

            val arr = json.optJSONArray("result") ?: return@withContext emptyList()
            val list = mutableListOf<WmsProDeviceInfo>()

            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                list.add(WmsProDeviceInfo(
                    uid  = obj.optString("id", "").ifEmpty { obj.optString("deviceUid", "") },
                    name = obj.optString("name", "Device ${i + 1}"),
                    type = obj.optInt("type", 3)
                ))
            }
            list.sortBy { it.name }
            list
        } finally {
            conn.disconnect()
        }
    }

    fun resetDiscovery() {
        devicesDiscovered = false
        Log.d("WmsPro", "Discovery reset — will re-sync on next poll")
    }
}

data class WmsProDeviceInfo(
    val uid:  String,
    val name: String,
    val type: Int
)