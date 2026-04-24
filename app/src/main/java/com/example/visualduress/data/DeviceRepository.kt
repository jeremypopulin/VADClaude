package com.example.visualduress.data

import android.content.Context
import android.media.MediaPlayer
import android.net.Uri
import android.util.Log
import com.example.visualduress.R
import com.example.visualduress.model.DeviceState
import com.example.visualduress.model.SerializableDeviceState
import com.example.visualduress.model.toSerializable
import com.example.visualduress.model.toDeviceState
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

class DeviceRepository(private val context: Context) {

    private val prefs = context.getSharedPreferences("duress_prefs", Context.MODE_PRIVATE)
    private val gson = Gson()

    suspend fun saveDeviceStates(devices: List<DeviceState>) = withContext(Dispatchers.IO) {
        val serializable = devices.map { it.toSerializable() }
        val json = gson.toJson(serializable)
        prefs.edit().putString("device_states", json).apply()
    }

    suspend fun loadDeviceStates(): List<DeviceState> = withContext(Dispatchers.IO) {
        val json = prefs.getString("device_states", null) ?: return@withContext emptyList()
        Log.d("DeviceRepo", "Loaded JSON: $json")

        return@withContext try {
            val type = object : TypeToken<List<SerializableDeviceState>>() {}.type
            val list = gson.fromJson<List<SerializableDeviceState>>(json, type)
            list.map { it.toDeviceState() }
        } catch (e: Exception) {
            Log.e("DeviceRepo", "Failed to parse device states", e)
            emptyList()
        }
    }

    fun savePassword(password: String) {
        prefs.edit().putString("app_password", password).apply()
    }

    fun loadPassword(): String {
        return prefs.getString("app_password", "admin") ?: "admin"
    }

    fun saveModbusIp(ip: String) {
        prefs.edit().putString("modbus_ip", ip).apply()
    }

    fun loadModbusIp(): String {
        return prefs.getString("modbus_ip", "192.168.0.250") ?: "192.168.0.250"
    }

    fun saveFloorplanUri(uri: Uri) {
        prefs.edit().putString("floorplan_uri", uri.toString()).apply()
    }

    fun loadFloorplanUri(): Uri? {
        return prefs.getString("floorplan_uri", null)?.let { Uri.parse(it) }
    }

    fun clearFloorplanUri() {
        prefs.edit().remove("floorplan_uri").apply()
    }

    suspend fun fetchDigitalInputs(ip: String): Map<Int, Int> = withContext(Dispatchers.IO) {
        val trimmedIp = ip.trim()
        val url = URL("http://$trimmedIp/api/slot/0/io/di")

        Log.d("ModbusFetch", "Fetching from $url")

        val conn = url.openConnection() as HttpURLConnection
        conn.apply {
            requestMethod = "GET"
            setRequestProperty("Accept", "vdn.dac.v1")
            connectTimeout = 3000
            readTimeout = 3000
        }

        try {
            if (conn.responseCode != HttpURLConnection.HTTP_OK) {
                Log.e("ModbusFetch", "HTTP error: ${conn.responseCode}")
                throw IOException("HTTP error: ${conn.responseCode}")
            }

            val json = JSONObject(conn.inputStream.bufferedReader().readText())
            val arr = json.getJSONObject("io").getJSONArray("di")
            return@withContext (0 until arr.length()).associate { i ->
                val obj = arr.getJSONObject(i)
                (obj.getInt("diIndex") + 1) to obj.getInt("diStatus")
            }
        } finally {
            conn.disconnect()
        }
    }

    fun playCriticalBeep(existingPlayer: MediaPlayer?): MediaPlayer {
        existingPlayer?.release()
        return MediaPlayer.create(context, R.raw.beep).apply {
            isLooping = true
            start()
        }
    }
}