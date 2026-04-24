package com.example.visualduress.integration

import android.util.Log
import com.example.visualduress.model.WmsProConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import javax.net.ssl.*
import java.security.cert.X509Certificate

class WmsProInputSource(
    private val config: WmsProConfig
) : InputSource {

    override val displayName = "Tecom WMS Pro"

    private var accessToken: String? = null
    private var tokenExpiryMs: Long = 0L
    private var devicesDiscovered = false
    private var resolvedMappings: Map<String, Int> = emptyMap()

    var slotNames: Map<Int, String> = emptyMap()
        private set

    // Confirmed event codes from WMS Pro testing:
    // 3  = Alarm (area armed + input triggered) ← primary alarm
    // 9  = Tamper
    // 11 = Alarm Acknowledged
    // 20, 37, 123, 173, 177, 195, 217 = other alarm states
    private val alarmEventCodes = setOf(3, 9, 11, 20, 37, 123, 173, 177, 195, 217)
    private val alarmOnlyEventCodes = setOf(3, 11, 20, 37, 123, 173, 177, 195, 217)

    // -------------------------------------------------------------------------
    // SSL — trust all for local network installs with self-signed certs
    // -------------------------------------------------------------------------

    private fun trustAllSslContext(): SSLSocketFactory {
        val trustAll = arrayOf<TrustManager>(object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {}
            override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {}
            override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
        })
        val sc = javax.net.ssl.SSLContext.getInstance("TLS")
        sc.init(null, trustAll, java.security.SecureRandom())
        return sc.socketFactory
    }

    private fun openConnection(urlString: String): HttpURLConnection {
        val url = URL(urlString)
        val conn = url.openConnection() as HttpURLConnection
        if (conn is HttpsURLConnection) {
            conn.sslSocketFactory = trustAllSslContext()
            conn.hostnameVerifier = HostnameVerifier { _, _ -> true }
        }
        conn.apply {
            setRequestProperty("Authorization", "Bearer ${accessToken ?: ""}")
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Content-Type", "application/json")
            connectTimeout = 10000
            readTimeout = 10000
            instanceFollowRedirects = true
        }
        return conn
    }

    private fun baseUrl(): String {
        var host = config.host.value.trim().trimEnd('/')
        if (!host.startsWith("http://") && !host.startsWith("https://")) {
            host = "https://$host"
        }
        return host
    }

    // -------------------------------------------------------------------------
    // Step 1 — ApiAuthenticate
    // -------------------------------------------------------------------------

    private suspend fun authenticate() = withContext(Dispatchers.IO) {
        val url = "${baseUrl()}/api/TokenAuth/ApiAuthenticate"
        Log.i("WmsPro", "Authenticating at $url")

        val body = """{"userNameOrEmailAddress":"${config.username.value.trim()}","password":"${config.password.value}"}"""

        val conn = URL(url).openConnection() as HttpURLConnection
        if (conn is HttpsURLConnection) {
            conn.sslSocketFactory = trustAllSslContext()
            conn.hostnameVerifier = HostnameVerifier { _, _ -> true }
        }
        try {
            conn.apply {
                requestMethod = "POST"
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Accept", "application/json")
                connectTimeout = 10000
                instanceFollowRedirects = true
            }
            conn.outputStream.bufferedWriter().use { it.write(body) }

            val code = conn.responseCode
            if (code != HttpURLConnection.HTTP_OK) {
                val err = conn.errorStream?.bufferedReader()?.readText() ?: ""
                throw IOException("Authentication failed HTTP $code: $err")
            }

            val response = conn.inputStream.bufferedReader().readText()
            val json = JSONObject(response)

            if (!json.optBoolean("success", false)) {
                val error = json.optJSONObject("error")?.optString("message") ?: "unknown"
                throw IOException("Authentication error: $error")
            }

            val result = json.getJSONObject("result")
            accessToken = result.getString("accessToken")
            val expireInSeconds = result.optInt("expireInSeconds", 86400)
            tokenExpiryMs = System.currentTimeMillis() + (expireInSeconds - 3600) * 1000L

            Log.i("WmsPro", "✅ Authenticated OK, token valid for ${expireInSeconds / 3600}h")

        } finally {
            conn.disconnect()
        }
    }

    // -------------------------------------------------------------------------
    // Step 2 — RegisterApiKey
    // -------------------------------------------------------------------------

    private suspend fun registerApiKey() = withContext(Dispatchers.IO) {
        val url = "${baseUrl()}/api/services/app/WmsproExternalAPIs/RegisterApiKey"
        Log.i("WmsPro", "Registering API key")

        val body = """{"apiKey":"${config.apiKey.value.trim()}"}"""
        val conn = openConnection(url)
        try {
            conn.requestMethod = "POST"
            conn.doOutput = true
            conn.outputStream.bufferedWriter().use { it.write(body) }

            val code = conn.responseCode
            if (code != HttpURLConnection.HTTP_OK) {
                throw IOException("RegisterApiKey failed HTTP $code")
            }

            val response = conn.inputStream.bufferedReader().readText()
            val json = JSONObject(response)

            if (!json.optBoolean("success", false)) {
                val error = json.optJSONObject("error")?.optString("message") ?: "unknown"
                throw IOException("RegisterApiKey error: $error")
            }

            Log.i("WmsPro", "✅ API key registered OK")

        } finally {
            conn.disconnect()
        }
    }

    private suspend fun ensureAuthenticated() {
        if (accessToken == null || System.currentTimeMillis() >= tokenExpiryMs) {
            Log.i("WmsPro", "Token missing or expired — re-authenticating")
            authenticate()
            registerApiKey()
        }
    }

    // -------------------------------------------------------------------------
    // Public interface
    // -------------------------------------------------------------------------

    override suspend fun connect(): Unit = withContext(Dispatchers.IO) {
        authenticate()
        registerApiKey()
        Log.i("WmsPro", "✅ Connected to WMS Pro")
    }

    override suspend fun disconnect() {
        Log.i("WmsPro", "Disconnecting WMS Pro")
        accessToken = null
        tokenExpiryMs = 0L
        devicesDiscovered = false
        resolvedMappings = emptyMap()
        slotNames = emptyMap()
    }

    override suspend fun poll(): Map<Int, Int> = withContext(Dispatchers.IO) {
        ensureAuthenticated()

        if (!devicesDiscovered) {
            discoverDevices()
        }

        if (resolvedMappings.isEmpty()) {
            return@withContext emptyMap()
        }

        fetchDeviceStatuses()
    }

    // -------------------------------------------------------------------------
    // Device discovery — POST with JSON body
    // -------------------------------------------------------------------------

    private suspend fun discoverDevices() = withContext(Dispatchers.IO) {
        val url = "${baseUrl()}/api/v2/services/app/Wmspro3rdParty/GetDevicesForControl"
        Log.i("WmsPro", "Discovering devices via POST")

        val body = """{"maxResultCount":500,"skipCount":0,"sorting":"","filter":"","parentIdFilter":null,"siteIdFilter":0,"regionIdFilter":0,"statusFilters":[],"deviceTypeFilter":"0"}"""

        val conn = openConnection(url)
        try {
            conn.requestMethod = "POST"
            conn.doOutput = true
            conn.outputStream.bufferedWriter().use { it.write(body) }
            conn.readTimeout = 10000

            val code = conn.responseCode
            if (code == HttpURLConnection.HTTP_UNAUTHORIZED) {
                accessToken = null
                throw IOException("Token rejected — will re-authenticate")
            }
            if (code != HttpURLConnection.HTTP_OK) {
                throw IOException("GetDevicesForControl failed HTTP $code")
            }

            val responseBody = conn.inputStream.bufferedReader().readText()
            val json = JSONObject(responseBody)

            if (!json.optBoolean("success", false)) {
                throw IOException("WMS Pro returned success=false during discovery")
            }

            // Response is nested: result.items
            val resultObj = json.optJSONObject("result")
            val itemsArr = resultObj?.optJSONArray("items") ?: run {
                devicesDiscovered = true
                return@withContext
            }

            val userMappings = config.deviceMappings.value
            val newMappings = mutableMapOf<String, Int>()
            val newSlotNames = mutableMapOf<Int, String>()

            for (i in 0 until itemsArr.length()) {
                val obj = itemsArr.getJSONObject(i)
                val uid = obj.optString("deviceUid", "")
                val name = obj.optString("name", "Device ${i + 1}")
                val slotId = userMappings[uid]
                if (slotId != null && slotId in 1..32) {
                    newMappings[uid] = slotId
                    newSlotNames[slotId] = name
                }
            }

            resolvedMappings = newMappings
            slotNames = newSlotNames
            devicesDiscovered = true
            Log.i("WmsPro", "✅ Discovered ${itemsArr.length()} devices, mapped ${newMappings.size}")

        } finally {
            conn.disconnect()
        }
    }

    // -------------------------------------------------------------------------
    // Status polling
    // -------------------------------------------------------------------------

    private suspend fun fetchDeviceStatuses(): Map<Int, Int> = withContext(Dispatchers.IO) {
        val uids = resolvedMappings.keys.toList()
        if (uids.isEmpty()) return@withContext emptyMap()

        val queryParams = uids.joinToString("&") { "DeviceUids=${it}" }
        val url = "${baseUrl()}/api/v2/services/app/Wmspro3rdParty/GetDevicesStatus?$queryParams"

        val conn = openConnection(url)
        try {
            conn.requestMethod = "GET"
            conn.readTimeout = 10000

            val code = conn.responseCode
            if (code == HttpURLConnection.HTTP_UNAUTHORIZED) {
                accessToken = null
                devicesDiscovered = false
                throw IOException("Token expired — will re-authenticate")
            }
            if (code != HttpURLConnection.HTTP_OK) {
                throw IOException("GetDevicesStatus failed HTTP $code")
            }

            val responseBody = conn.inputStream.bufferedReader().readText()
            val json = JSONObject(responseBody)

            if (!json.optBoolean("success", false)) {
                throw IOException("WMS Pro error: ${json.optJSONObject("error")?.optString("message")}")
            }

            // Response: result is a direct array
            val resultArr = json.optJSONArray("result") ?: return@withContext emptyMap()
            val result = mutableMapOf<Int, Int>()

            for (i in 0 until resultArr.length()) {
                val deviceObj = resultArr.getJSONObject(i)
                val uid = deviceObj.optString("deviceUid", "")
                val slotId = resolvedMappings[uid] ?: continue
                val statuses = deviceObj.optJSONArray("statuses")

                var activeCode = 0
                if (statuses != null) {
                    for (j in 0 until statuses.length()) {
                        val eventCode = statuses.getJSONObject(j).optInt("eventCode", 0)
                        if (eventCode in alarmEventCodes) {
                            activeCode = eventCode
                            break
                        }
                    }
                }
                result[slotId] = activeCode
            }

            result

        } finally {
            conn.disconnect()
        }
    }

    // -------------------------------------------------------------------------
    // Settings UI helper
    // -------------------------------------------------------------------------

    suspend fun fetchAvailableDevices(): List<WmsProDeviceInfo> = withContext(Dispatchers.IO) {
        ensureAuthenticated()
        val url = "${baseUrl()}/api/v2/services/app/Wmspro3rdParty/GetDevicesForControl"
        val body = """{"maxResultCount":500,"skipCount":0,"sorting":"","filter":"","parentIdFilter":null,"siteIdFilter":0,"regionIdFilter":0,"statusFilters":[],"deviceTypeFilter":"0"}"""

        val conn = openConnection(url)
        try {
            conn.requestMethod = "POST"
            conn.doOutput = true
            conn.outputStream.bufferedWriter().use { it.write(body) }
            conn.readTimeout = 10000

            val code = conn.responseCode
            if (code == HttpURLConnection.HTTP_UNAUTHORIZED) throw IOException("Invalid credentials")
            if (code != HttpURLConnection.HTTP_OK) throw IOException("HTTP $code")

            val responseBody = conn.inputStream.bufferedReader().readText()
            val json = JSONObject(responseBody)
            if (!json.optBoolean("success", false)) throw IOException("success=false")

            val items = json.optJSONObject("result")?.optJSONArray("items")
                ?: return@withContext emptyList()

            val list = mutableListOf<WmsProDeviceInfo>()
            for (i in 0 until items.length()) {
                val obj = items.getJSONObject(i)
                val uid = obj.optString("deviceUid", "")
                val type = obj.optInt("type", 0)
                // Only include inputs (type 3) — excludes controllers, areas, relays etc
                if (uid.isNotEmpty() && type == 3) {
                    list.add(WmsProDeviceInfo(
                        uid = uid,
                        name = obj.optString("name", "Device ${i + 1}"),
                        type = type
                    ))
                }
            }
            list.sortBy { it.name }
            list
        } finally {
            conn.disconnect()
        }
    }

    fun resetDiscovery() {
        devicesDiscovered = false
        Log.d("WmsPro", "Discovery reset")
    }

    fun clearToken() {
        accessToken = null
        tokenExpiryMs = 0L
        Log.d("WmsPro", "Token cleared — will re-authenticate on next poll")
    }
}

data class WmsProDeviceInfo(
    val uid: String,
    val name: String,
    val type: Int
)