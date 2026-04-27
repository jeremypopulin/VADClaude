package com.example.visualduress.util

import android.content.Context
import android.net.Uri
import android.util.Log
import com.example.visualduress.model.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import java.io.*

/**
 * Handles export and import of all VAD configuration to/from a .vad JSON file.
 */
object BackupManager {

    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true; encodeDefaults = true }

    @Serializable
    data class VadBackup(
        val version: Int = 2,
        val exportedAt: Long = System.currentTimeMillis(),
        val appVersion: String = "",
        val devices: List<SerializableDeviceState> = emptyList(),
        val floorplanUri: String? = null,
        val modbusIp: String = "",
        val moxa2Ip: String = "",
        val inputSourceType: String = "MOXA_REST",
        val inceptionConfig: SerializableInceptionConfig? = null,
        val wmsProConfig: SerializableWmsProConfig? = null,
        val smsConfig: SerializableSmsConfig? = null,
        val scaleX: Float = 1f,
        val scaleY: Float = 1f,
        val offsetX: Float = 0f,
        val offsetY: Float = 0f,
        val aspectLock: Boolean = true,
        val password: String = ""
    )

    // -------------------------------------------------------------------------
    // Export
    // -------------------------------------------------------------------------

    fun exportToUri(
        context: Context,
        uri: Uri,
        devices: List<DeviceState>,
        floorplanUri: Uri?,
        modbusIp: String,
        moxa2Ip: String,
        inputSourceType: String,
        inceptionConfig: InceptionConfig,
        wmsProConfig: WmsProConfig,
        smsConfig: SmsConfig,
        scaleX: Float,
        scaleY: Float,
        offsetX: Float,
        offsetY: Float,
        aspectLock: Boolean,
        password: String,
        appVersion: String
    ): Boolean {
        return try {
            val backup = VadBackup(
                version = 2,
                exportedAt = System.currentTimeMillis(),
                appVersion = appVersion,
                devices = devices.map { it.toSerializable() },
                floorplanUri = floorplanUri?.toString(),
                modbusIp = modbusIp,
                moxa2Ip = moxa2Ip,
                inputSourceType = inputSourceType,
                inceptionConfig = inceptionConfig.toSerializable(),
                wmsProConfig = wmsProConfig.toSerializable(),
                smsConfig = smsConfig.toSerializable(),
                scaleX = scaleX,
                scaleY = scaleY,
                offsetX = offsetX,
                offsetY = offsetY,
                aspectLock = aspectLock,
                password = password
            )

            val jsonString = json.encodeToString(backup)
            context.contentResolver.openOutputStream(uri)?.use { os ->
                os.bufferedWriter().use { it.write(jsonString) }
            }
            Log.i("BackupManager", "✅ Exported ${devices.size} devices to $uri")
            true
        } catch (e: Exception) {
            Log.e("BackupManager", "Export failed: ${e.message}", e)
            false
        }
    }

    // -------------------------------------------------------------------------
    // Import
    // -------------------------------------------------------------------------

    fun importFromUri(context: Context, uri: Uri): VadBackup? {
        return try {
            val jsonString = context.contentResolver.openInputStream(uri)?.use { ins ->
                ins.bufferedReader().readText()
            } ?: return null

            val backup = json.decodeFromString<VadBackup>(jsonString)
            Log.i("BackupManager", "✅ Imported backup v${backup.version} with ${backup.devices.size} devices")
            backup
        } catch (e: Exception) {
            Log.e("BackupManager", "Import failed: ${e.message}", e)
            null
        }
    }

    // -------------------------------------------------------------------------
    // Generate filename
    // -------------------------------------------------------------------------

    fun generateFilename(): String {
        val sdf = java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.getDefault())
        return "VAD_Backup_${sdf.format(java.util.Date())}.vad"
    }
}