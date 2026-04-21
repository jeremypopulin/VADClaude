package com.example.visualduress.integration

import android.util.Log
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * Input source that polls a Moxa ioLogik device via its HTTP REST API.
 *
 * @param ip  IP address of the Moxa unit
 * @param slotOffset  Added to each input index so the second Moxa unit
 *                    maps to slots 17-32 instead of 1-16.
 *                    Unit 1: slotOffset = 0  → slots 1-16
 *                    Unit 2: slotOffset = 16 → slots 17-32
 */
class MoxaRestInputSource(
    private var ip: String,
    private val slotOffset: Int = 0
) : InputSource {

    override val displayName = if (slotOffset == 0) "Moxa ioLogik REST (Unit 1)" else "Moxa ioLogik REST (Unit 2)"

    fun updateIp(newIp: String) {
        ip = newIp
    }

    override suspend fun poll(): Map<Int, Int> {
        val trimmedIp = ip.trim()
        val url = URL("http://$trimmedIp/api/slot/0/io/di")

        Log.d("MoxaRest", "Polling $url (slot offset: $slotOffset)")

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
                // Apply slot offset so unit 2 maps to slots 17-32
                (obj.getInt("diIndex") + 1 + slotOffset) to obj.getInt("diStatus")
            }
        } finally {
            conn.disconnect()
        }
    }
}