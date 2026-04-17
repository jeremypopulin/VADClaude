package com.example.visualduress.integration

import android.util.Log
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * Input source that polls a Moxa ioLogik device via its HTTP REST API.
 *
 * Endpoint: GET http://{ip}/api/slot/0/io/di
 * Header:   Accept: vdn.dac.v1
 *
 * Returns digital input states mapped as:
 *   diIndex + 1  ->  diStatus  (0 = off, 1 = on)
 *
 * This is a direct extraction of the existing DeviceRepository.fetchDigitalInputs()
 * logic, moved into the modular input source architecture.
 */
class MoxaRestInputSource(
    private var ip: String
) : InputSource {

    override val displayName = "Moxa ioLogik REST"

    fun updateIp(newIp: String) {
        ip = newIp
    }

    override suspend fun poll(): Map<Int, Int> {
        val trimmedIp = ip.trim()
        val url = URL("http://$trimmedIp/api/slot/0/io/di")

        Log.d("MoxaRest", "Polling $url")

        val conn = url.openConnection() as HttpURLConnection
        conn.apply {
            requestMethod = "GET"
            setRequestProperty("Accept", "vdn.dac.v1")
            connectTimeout = 3000
            readTimeout = 3000
        }

        try {
            if (conn.responseCode != HttpURLConnection.HTTP_OK) {
                throw IOException("HTTP ${conn.responseCode}")
            }

            val json = JSONObject(conn.inputStream.bufferedReader().readText())
            val arr = json.getJSONObject("io").getJSONArray("di")

            return (0 until arr.length()).associate { i ->
                val obj = arr.getJSONObject(i)
                (obj.getInt("diIndex") + 1) to obj.getInt("diStatus")
            }
        } finally {
            conn.disconnect()
        }
    }
}
