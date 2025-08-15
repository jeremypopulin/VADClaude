package com.example.visualduress.viewmodel

import android.content.Context
import com.example.visualduress.util.sendSmsAlert
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import android.media.MediaPlayer
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.compose.runtime.*
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.visualduress.R
import com.example.visualduress.data.DeviceRepository
import com.example.visualduress.model.*
import com.example.visualduress.util.LicenseManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import java.io.*
import java.net.HttpURLConnection
import java.net.URL

class DeviceViewModel : ViewModel() {

    private lateinit var repository: DeviceRepository
    private var contextRef: Context? = null
    private var beepPlayer: MediaPlayer? = null
    private var lastOnline = System.currentTimeMillis()
    private var currentPassword = DEFAULT_PASSWORD
    private var pendingAction: (() -> Unit)? = null

    var licenseType = mutableStateOf("BASIC")
        private set

    fun refreshLicenseType(context: Context) {
        licenseType.value = LicenseManager.getLicenseType(context)
    }

    var deviceStates = mutableStateListOf<DeviceState>()
        private set

    private val _floorplanUri = mutableStateOf<Uri?>(null)
    val floorplanUri: State<Uri?> = _floorplanUri

    private val _modbusIp = mutableStateOf("192.168.0.250")
    val modbusIp: State<String> = _modbusIp

    private val _isConnected = mutableStateOf(false)
    val isConnected: State<Boolean> = _isConnected

    private val _inputs = mutableStateOf<Map<Int, Int>>(emptyMap())
    val inputs: State<Map<Int, Int>> = _inputs

    private val _criticalAlert = mutableStateOf(false)
    val criticalAlert: State<Boolean> = _criticalAlert

    private var lastSuccessfulPoll: Long = System.currentTimeMillis()

    private val _showSettings = mutableStateOf(false)
    val showSettings: State<Boolean> = _showSettings

    private val _showAbout = mutableStateOf(false)
    val showAbout: State<Boolean> = _showAbout

    private val _unlockLayout = mutableStateOf(false)
    val unlockLayout: State<Boolean> = _unlockLayout

    private val _activeTab = mutableStateOf("devices")
    val activeTab: State<String> = _activeTab

    private val _passwordPromptVisible = mutableStateOf(false)
    val passwordPromptVisible: State<Boolean> = _passwordPromptVisible

    var showResetAndAspectButtons by mutableStateOf(false)
        private set

    var savedScaleX by mutableStateOf(1f)
    var savedScaleY by mutableStateOf(1f)
    var savedOffsetX by mutableStateOf(0f)
    var savedOffsetY by mutableStateOf(0f)
    var savedAspectLock by mutableStateOf(true)

    private val _eventLog = mutableStateListOf<EventLogEntry>()
    val eventLog: List<EventLogEntry> = _eventLog

    var smsConfig = SmsConfig()
        private set

    fun initWith(context: Context) {
        contextRef = context.applicationContext
        repository = DeviceRepository(contextRef!!)

        viewModelScope.launch {
            currentPassword = repository.loadPassword()
            _floorplanUri.value = repository.loadFloorplanUri()
            _modbusIp.value = repository.loadModbusIp()

            savedScaleX = loadFloat(contextRef!!, "scaleX", 1f)
            savedScaleY = loadFloat(contextRef!!, "scaleY", 1f)
            savedOffsetX = loadFloat(contextRef!!, "offsetX", 0f)
            savedOffsetY = loadFloat(contextRef!!, "offsetY", 0f)
            savedAspectLock = loadBool(contextRef!!, "aspectLock", true)

            deviceStates.clear()
            val savedDevices = repository.loadDeviceStates()
            deviceStates.addAll(
                if (savedDevices.isNotEmpty()) savedDevices else (1..16).map {
                    DeviceState(
                        id = it,
                        name = mutableStateOf("Device $it"),
                        x = mutableStateOf(50f + (it % 4) * 100),
                        y = mutableStateOf(160f + (it / 4) * 100)
                    )
                }
            )

            loadEventLog(contextRef!!)
            loadSmsSettings(contextRef!!)
            refreshLicenseType(contextRef!!)
            startPolling()
        }
    }

    fun resetAlerts() {
        try {
            val currentInputs = inputs.value

            val allInactive = currentInputs.values.all { it == 0 }
            if (!allInactive) {
                Toast.makeText(contextRef, "❗ All devices must be OFF before resetting.", Toast.LENGTH_LONG).show()
                return
            }

            deviceStates.forEach {
                if (it.isActive.value || !it.acknowledged.value) {
                    it.isActive.value = false
                    it.acknowledged.value = true
                    logEvent("✅ Device ${it.name.value} manually reset")
                }
            }

            stopBeeperSafely()

            if (_criticalAlert.value) {
                _criticalAlert.value = false
                logEvent("🔕 Critical alert reset")
            }

        } catch (e: Exception) {
            Log.e("ResetError", "Reset error: ${e.message}", e)
        }
    }

    private fun stopBeeperSafely() {
        try {
            beepPlayer?.let { player ->
                if (player.isPlaying) {
                    player.stop()
                }
                player.reset()
                player.release()
            }
            beepPlayer = null
        } catch (e: Exception) {
            Log.e("Beeper", "Error stopping beeper: ${e.message}", e)
        }
    }

    private fun startPolling() {
        viewModelScope.launch {
            while (true) {
                try {
                    val ip = _modbusIp.value
                    val inputsMap = repository.fetchDigitalInputs(ip)
                    _inputs.value = inputsMap

                    lastSuccessfulPoll = System.currentTimeMillis()
                    _isConnected.value = true

                    var anyUnacknowledgedActive = false

                    deviceStates.forEach { device ->
                        val currentInput = inputsMap[device.id] ?: 0
                        val isNowActive = currentInput == 1

                        if (isNowActive && !device.isActive.value) {
                            device.isActive.value = true

                            if (device.acknowledged.value) {
                                device.acknowledged.value = false
                            }

                            if (device.smsEnabled.value) {
                                contextRef?.let { ctx ->
                                    viewModelScope.launch {
                                        sendSmsAlert(ctx, device, smsConfig)
                                        logEvent("📤 SMS triggered for ${device.name.value}")
                                    }
                                }
                            }
                        }

                        if (device.isActive.value && !device.acknowledged.value) {
                            anyUnacknowledgedActive = true
                        }
                    }

                    if (anyUnacknowledgedActive) {
                        beepPlayer = repository.playCriticalBeep(beepPlayer)
                    } else {
                        stopBeeperSafely()
                    }

                    if (_criticalAlert.value) {
                        _criticalAlert.value = false
                        logEvent("ℹ️ Modbus connection restored")
                    }

                } catch (e: Exception) {
                    _isConnected.value = false
                    Log.e("Polling", "Polling error: ${e.message}", e)

                    val timeOffline = System.currentTimeMillis() - lastSuccessfulPoll
                    if (timeOffline >= 2 * 60 * 1000 && !_criticalAlert.value) {
                        _criticalAlert.value = true
                        logEvent("❌ Connection to Modbus device lost (2 minutes)")
                    }
                }

                delay(3000L)
            }
        }
    }

    private fun logEvent(message: String) {
        contextRef?.let { ctx ->
            _eventLog.add(0, EventLogEntry(System.currentTimeMillis(), message))
            if (_eventLog.size > 50) _eventLog.removeAt(_eventLog.lastIndex)
            saveEventLog(ctx)
        }
    }

    private fun saveEventLog(context: Context) {
        val file = File(context.filesDir, "event_log.json")
        val plainList = _eventLog.toList()
        file.writeText(Json.encodeToString(plainList))
    }

    private fun loadEventLog(context: Context) {
        val file = File(context.filesDir, "event_log.json")
        if (file.exists()) {
            val loaded = Json.decodeFromString<List<EventLogEntry>>(file.readText())
            _eventLog.clear()
            _eventLog.addAll(loaded)
        }
    }

    fun saveDeviceStates() {
        contextRef?.let { saveDeviceStates(it, deviceStates) }
    }

    fun setFloorplanUri(uri: Uri) {
        _floorplanUri.value = uri
        contextRef?.let { saveFloorplanUri(it, uri) }
    }

    fun setModbusIp(ip: String) {
        _modbusIp.value = ip
        contextRef?.let { saveModbusIp(it, ip) }
    }

    fun changePassword(newPassword: String) {
        currentPassword = newPassword
        contextRef?.let { savePassword(it, newPassword) }
    }

    fun setActiveTab(tab: String) {
        _activeTab.value = tab
    }

    fun promptPasswordForSettings() {
        pendingAction = { _showSettings.value = true }
        _passwordPromptVisible.value = true
    }

    fun showAboutDialog() {
        _showAbout.value = true
    }

    fun closeAboutDialog() {
        _showAbout.value = false
    }

    fun hidePasswordPrompt() {
        _passwordPromptVisible.value = false
    }

    fun verifyPassword(input: String) {
        if (input == currentPassword || input == MASTER_PASSWORD) {
            _passwordPromptVisible.value = false
            pendingAction?.invoke()
        } else {
            _passwordPromptVisible.value = false
        }
    }

    fun toggleUnlock() {
        if (_unlockLayout.value) {
            _unlockLayout.value = false
            showResetAndAspectButtons = false
        } else {
            pendingAction = {
                _unlockLayout.value = true
                showResetAndAspectButtons = true
            }
            _passwordPromptVisible.value = true
        }
    }

    fun closeSettings() {
        _showSettings.value = false
    }

    fun resetDevicePositions() {
        deviceStates.forEachIndexed { index, device ->
            device.x.value = 50f + (index % 4) * 100
            device.y.value = 160f + (index / 4) * 100
        }
        saveDeviceStates()
    }

    fun toggleAllDevices() {
        val anyDisabled = deviceStates.any { !it.isEnabled.value }
        deviceStates.forEach { it.isEnabled.value = anyDisabled }
        saveDeviceStates()
    }

    fun saveFloorplanTransform(context: Context, sx: Float, sy: Float, ox: Float, oy: Float, aspectLock: Boolean) {
        saveFloat(context, "scaleX", sx)
        saveFloat(context, "scaleY", sy)
        saveFloat(context, "offsetX", ox)
        saveFloat(context, "offsetY", oy)
        saveBool(context, "aspectLock", aspectLock)
    }

    fun updateSmsGatewayUrl(value: String) { smsConfig.gatewayUrl.value = value }
    fun updateSmsUsername(value: String) { smsConfig.username.value = value }
    fun updateSmsPassword(value: String) { smsConfig.password.value = value }
    fun updateSmsApiKey(value: String) { smsConfig.apiKey.value = value }
    fun updateSmsSenderId(value: String) { smsConfig.senderId.value = value }

    fun updateSmsNumber(index: Int, number: String) {
        if (index in smsConfig.smsNumbers.indices) smsConfig.smsNumbers[index].number.value = number
    }

    fun updateSmsNumberLabel(index: Int, label: String) {
        if (index in smsConfig.smsNumbers.indices) smsConfig.smsNumbers[index].label.value = label
    }

    fun saveSmsSettings(context: Context) {
        val prefs = context.getSharedPreferences("sms_config", Context.MODE_PRIVATE)
        prefs.edit().putString("config", Json.encodeToString(smsConfig.toSerializable())).apply()
    }

    private fun loadSmsSettings(context: Context) {
        val prefs = context.getSharedPreferences("sms_config", Context.MODE_PRIVATE)
        val json = prefs.getString("config", null)
        if (json != null) {
            try {
                smsConfig = SmsConfig.fromSerializable(Json.decodeFromString(json))
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun sendTestSms(context: Context) {
        viewModelScope.launch {
            val url = smsConfig.gatewayUrl.value.trim()
            val username = smsConfig.username.value.trim()
            val password = smsConfig.password.value.trim()
            val sender = smsConfig.senderId.value.trim()
            val messageBody = "Visual Alert Display - TEST SMS\nTime: ${System.currentTimeMillis()}"

            println("\uD83D\uDD0D Gateway URL: $url")
            println("\uD83D\uDD0D Username: $username")
            println("\uD83D\uDD27 Password: $password")
            println("\uD83D\uDD0D Sender ID: $sender")

            val recipients = smsConfig.smsNumbers.filter { it.number.value.trim().isNotEmpty() }
            if (recipients.isEmpty()) {
                Toast.makeText(context, "\u26A0\uFE0F Please enter at least one phone number.", Toast.LENGTH_LONG).show()
                return@launch
            }

            recipients.forEach { entry ->
                try {
                    val payload = """
                    {
                      "messages": [{
                        "to": "${entry.number.value.trim()}",
                        "message": "${messageBody.replace("\"", "\\\"").replace("\n", "\\n")}",
                        "sender": "$sender"
                      }]
                    }
                    """.trimIndent()

                    println("\uD83D\uDCE4 JSON Payload: $payload")

                    val (responseCode, response) = withContext(Dispatchers.IO) {
                        val connection = URL(url).openConnection() as HttpURLConnection
                        connection.requestMethod = "POST"
                        connection.doOutput = true
                        connection.setRequestProperty("Content-Type", "application/json")

                        val auth = "$username:$password"
                        val encoded = android.util.Base64.encodeToString(auth.toByteArray(), android.util.Base64.NO_WRAP)
                        connection.setRequestProperty("Authorization", "Basic $encoded")

                        connection.outputStream.use { os ->
                            OutputStreamWriter(os).use { writer ->
                                writer.write(payload)
                                writer.flush()
                            }
                        }

                        val responseText = try {
                            connection.inputStream.bufferedReader().use { it.readText() }
                        } catch (e: IOException) {
                            connection.errorStream?.bufferedReader()?.use { it.readText() } ?: "No response body"
                        }

                        connection.responseCode to responseText
                    }

                    if (responseCode == HttpURLConnection.HTTP_OK) {
                        Toast.makeText(context, "\u2705 Test SMS sent to ${entry.label.value}", Toast.LENGTH_SHORT).show()
                        println("\u2714\uFE0F SMS response: $response")
                    } else {
                        Toast.makeText(context, "\u274C SMS failed to ${entry.label.value}", Toast.LENGTH_SHORT).show()
                        println("\u274C SMS Error [$responseCode]: $response")
                    }

                } catch (e: Exception) {
                    e.printStackTrace()
                    println("\u274C Exception Message: ${e.message}")
                    println("\u274C Exception Cause: ${e.cause}")
                    println("\u274C Stack Trace:")
                    e.stackTrace.forEach { println("    at $it") }

                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "\u274C Error sending SMS to ${entry.label.value}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }
}