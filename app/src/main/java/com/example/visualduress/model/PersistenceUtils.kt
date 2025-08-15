package com.example.visualduress.model

import com.example.visualduress.model.toDeviceState
import android.content.Context
import android.net.Uri
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

const val DEFAULT_PASSWORD = "admin"
const val MASTER_PASSWORD = "JBP23!"

fun savePassword(context: Context, password: String) {
    context.getSharedPreferences("duress_prefs", Context.MODE_PRIVATE)
        .edit().putString("app_password", password).apply()
}

fun loadPassword(context: Context): String {
    return context.getSharedPreferences("duress_prefs", Context.MODE_PRIVATE)
        .getString("app_password", DEFAULT_PASSWORD) ?: DEFAULT_PASSWORD
}

fun saveFloorplanUri(context: Context, uri: Uri) {
    context.getSharedPreferences("duress_prefs", Context.MODE_PRIVATE)
        .edit().putString("floorplan_uri", uri.toString()).apply()
}

fun loadFloorplanUri(context: Context): Uri? {
    return context.getSharedPreferences("duress_prefs", Context.MODE_PRIVATE)
        .getString("floorplan_uri", null)?.let { Uri.parse(it) }
}

fun clearFloorplanUri(context: Context) {
    context.getSharedPreferences("duress_prefs", Context.MODE_PRIVATE)
        .edit().remove("floorplan_uri").apply()
}

fun saveDeviceStates(context: Context, devices: List<DeviceState>) {
    val json = Gson().toJson(devices.map { it.toSerializable() })
    context.getSharedPreferences("duress_prefs", Context.MODE_PRIVATE)
        .edit().putString("device_states", json).apply()
}

fun loadDeviceStates(context: Context): List<DeviceState> {
    val json = context.getSharedPreferences("duress_prefs", Context.MODE_PRIVATE)
        .getString("device_states", null) ?: return emptyList()
    val type = object : TypeToken<List<SerializableDeviceState>>() {}.type
    val rawList: List<SerializableDeviceState> = Gson().fromJson(json, type)
    return rawList.map { it.toDeviceState() }
}

fun saveModbusIp(context: Context, ip: String) {
    context.getSharedPreferences("duress_prefs", Context.MODE_PRIVATE)
        .edit().putString("modbus_ip", ip).apply()
}

fun loadModbusIp(context: Context): String {
    return context.getSharedPreferences("duress_prefs", Context.MODE_PRIVATE)
        .getString("modbus_ip", "192.168.0.250") ?: "192.168.0.250"
}

fun saveFloat(context: Context, key: String, value: Float) {
    context.getSharedPreferences("duress_prefs", Context.MODE_PRIVATE)
        .edit().putFloat(key, value).apply()
}

fun loadFloat(context: Context, key: String, defaultValue: Float): Float {
    return context.getSharedPreferences("duress_prefs", Context.MODE_PRIVATE)
        .getFloat(key, defaultValue)
}

fun saveBool(context: Context, key: String, value: Boolean) {
    context.getSharedPreferences("duress_prefs", Context.MODE_PRIVATE)
        .edit().putBoolean(key, value).apply()
}

fun loadBool(context: Context, key: String, defaultValue: Boolean): Boolean {
    return context.getSharedPreferences("duress_prefs", Context.MODE_PRIVATE)
        .getBoolean(key, defaultValue)
}
