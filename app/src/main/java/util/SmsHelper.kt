package com.example.visualduress.util

import android.content.Context
import android.util.Log
import com.example.visualduress.model.DeviceState
import com.example.visualduress.model.SmsConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import android.util.Base64

suspend fun sendSmsAlert(
    context: Context,
    device: DeviceState,
    smsConfig: SmsConfig
): Boolean {
    val message = "🚨 Alarm triggered: ${device.name.value} at ${System.currentTimeMillis()}"
    val sender = smsConfig.senderId.value.trim()

    smsConfig.smsNumbers.filter { it.number.value.trim().isNotEmpty() }.forEach { entry ->
        try {
            val payload = """
                {
                  "messages": [{
                    "to": "${entry.number.value.trim()}",
                    "message": "${message.replace("\"", "\\\"")}",
                    "sender": "$sender"
                  }]
                }
            """.trimIndent()

            val (responseCode, responseText) = withContext(Dispatchers.IO) {
                val url = URL(smsConfig.gatewayUrl.value.trim())
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/json")

                val auth = "${smsConfig.username.value}:${smsConfig.password.value}"
                val encoded = Base64.encodeToString(auth.toByteArray(), Base64.NO_WRAP)
                conn.setRequestProperty("Authorization", "Basic $encoded")

                conn.outputStream.use { os ->
                    OutputStreamWriter(os).use { it.write(payload) }
                }

                val response = try {
                    conn.inputStream.bufferedReader().use { it.readText() }
                } catch (e: Exception) {
                    conn.errorStream?.bufferedReader()?.use { it.readText() } ?: "No response body"
                }

                conn.responseCode to response
            }

            Log.d("SmsHelper", "SMS sent to ${entry.label.value} for ${device.name.value}: [$responseCode] $responseText")

        } catch (e: Exception) {
            Log.e("SmsHelper", "Failed to send SMS for ${device.name.value}: ${e.message}", e)
            return false
        }
    }
    return true
}
