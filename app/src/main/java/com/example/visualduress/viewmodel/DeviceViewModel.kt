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
import com.example.visualduress.integration.InputSource
import com.example.visualduress.integration.InputSourceFactory
import com.example.visualduress.integration.InputSourceType
import com.example.visualduress.integration.InceptionInputSource
import com.example.visualduress.integration.WmsProInputSource
import com.example.visualduress.integration.WmsProDeviceInfo
import com.example.visualduress.model.*
import com.example.visualduress.util.LicenseManager
import kotlinx.coroutines.Job
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
    private var currentPassword = DEFAULT_PASSWORD
    private var pendingAction: (() -> Unit)? = null

    // -------------------------------------------------------------------------
    // License
    // -------------------------------------------------------------------------

    private val _licenseType = mutableStateOf("BASIC")
    val licenseType: State<String> = _licenseType

    fun refreshLicenseType(context: Context) {
        // Use full LicenceState so grace period and locked state trigger recompose
        val state = LicenseManager.getLicenceState(context)
        val newType = when (state) {
            LicenseManager.LicenceState.LOCKED -> "LOCKED"
            LicenseManager.LicenceState.GRACE  -> "GRACE"
            LicenseManager.LicenceState.NONE   -> "NONE"
            LicenseManager.LicenceState.ACTIVE -> LicenseManager.getLicenseType(context)
        }
        Log.d("DeviceViewModel", "Licence state: $state, type: $newType")
        _licenseType.value = newType
    }

    fun forceRefreshLicense(context: Context, onComplete: (() -> Unit)? = null) {
        Log.d("DeviceViewModel", "Force refreshing license")
        refreshLicenseType(context)
        viewModelScope.launch {
            delay(150)
            refreshLicenseType(context)
            delay(50)
            withContext(Dispatchers.Main) { onComplete?.invoke() }
        }
    }

    // -------------------------------------------------------------------------
    // Device states & floor plan
    // -------------------------------------------------------------------------

    var deviceStates = mutableStateListOf<DeviceState>()
        private set

    private val _floorplanUri = mutableStateOf<Uri?>(null)
    val floorplanUri: State<Uri?> = _floorplanUri

    // -------------------------------------------------------------------------
    // Input source management
    // -------------------------------------------------------------------------

    /** Currently active input source type */
    private val _inputSourceType = mutableStateOf(InputSourceType.MOXA_REST)
    val inputSourceType: State<InputSourceType> = _inputSourceType

    /** Inception config (Compose-observable) */
    var inceptionConfig = InceptionConfig()
        private set

    var wmsProConfig = WmsProConfig()
        private set

    /** Cached input names from the last "Load Inputs from Controller" call.
     *  Key = Inception GUID, Value = input name.
     *  Used to sync device names immediately on Save & Apply. */
    private val _cachedInceptionInputNames = mutableMapOf<String, String>()

    /** IP used for Moxa REST unit 1 and Modbus TCP */
    private val _modbusIp = mutableStateOf("192.168.0.250")
    val modbusIp: State<String> = _modbusIp

    /** IP used for Moxa REST unit 2 (slots 17-32) */
    private val _moxa2Ip = mutableStateOf("192.168.0.251")
    val moxa2Ip: State<String> = _moxa2Ip

    /** The active InputSource instance */
    private var activeInputSource: InputSource? = null

    /** Current polling Job — cancelled and restarted when source changes */
    private var pollingJob: Job? = null

    // -------------------------------------------------------------------------
    // Connection status
    // -------------------------------------------------------------------------

    private val _isConnected = mutableStateOf(false)
    val isConnected: State<Boolean> = _isConnected

    private val _connectionStatusText = mutableStateOf("")
    val connectionStatusText: State<String> = _connectionStatusText

    private val _inputs = mutableStateOf<Map<Int, Int>>(emptyMap())
    val inputs: State<Map<Int, Int>> = _inputs

    private val _criticalAlert = mutableStateOf(false)
    val criticalAlert: State<Boolean> = _criticalAlert

    private var lastSuccessfulPoll: Long = System.currentTimeMillis()

    // -------------------------------------------------------------------------
    // UI state
    // -------------------------------------------------------------------------

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

    // -------------------------------------------------------------------------
    // Initialisation
    // -------------------------------------------------------------------------

    fun initWith(context: Context) {
        contextRef = context.applicationContext
        repository = DeviceRepository(contextRef!!)
        refreshLicenseType(contextRef!!)

        viewModelScope.launch {
            currentPassword = repository.loadPassword()
            _floorplanUri.value = repository.loadFloorplanUri()
            _modbusIp.value = repository.loadModbusIp()
            _moxa2Ip.value = loadMoxa2Ip(contextRef!!)

            savedScaleX = loadFloat(contextRef!!, "scaleX", 1f)
            savedScaleY = loadFloat(contextRef!!, "scaleY", 1f)
            savedOffsetX = loadFloat(contextRef!!, "offsetX", 0f)
            savedOffsetY = loadFloat(contextRef!!, "offsetY", 0f)
            savedAspectLock = loadBool(contextRef!!, "aspectLock", true)

            deviceStates.clear()
            val savedDevices = repository.loadDeviceStates()
            deviceStates.addAll(
                if (savedDevices.isNotEmpty()) savedDevices else (1..32).map {
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
            loadInputSourceSettings(contextRef!!)
            loadInceptionConfig(contextRef!!)
            loadWmsProConfig(contextRef!!)

            startPollingWithCurrentSource()
        }
    }

    // -------------------------------------------------------------------------
    // Input source switching
    // -------------------------------------------------------------------------

    /**
     * Switch to a new input source type.
     * Stops the current polling job, disconnects the old source,
     * creates a new source, and restarts polling.
     *
     * Called from Settings when the user changes the source selector.
     */
    fun setInputSourceType(type: InputSourceType) {
        if (_inputSourceType.value == type) return

        Log.i("ViewModel", "Switching input source: ${_inputSourceType.value} -> $type")
        _inputSourceType.value = type
        contextRef?.let { saveInputSourceType(it, type) }

        restartPolling()
    }

    /**
     * Called when connectivity settings change (IP, Inception credentials, etc.)
     * Restarts polling so the new settings take effect immediately.
     */
    fun restartPolling() {
        viewModelScope.launch {
            pollingJob?.cancel()
            pollingJob?.join()

            activeInputSource?.let {
                try { it.disconnect() } catch (e: Exception) { /* ignore */ }
            }
            activeInputSource = null

            _isConnected.value = false
            _connectionStatusText.value = "Connecting to ${_inputSourceType.value.displayName}..."

            startPollingWithCurrentSource()
        }
    }

    private fun startPollingWithCurrentSource() {
        val source = InputSourceFactory.create(
            type = _inputSourceType.value,
            modbusIp = _modbusIp.value,
            moxa2Ip = _moxa2Ip.value,
            inceptionConfig = inceptionConfig,
            wmsProConfig = wmsProConfig
        )
        activeInputSource = source

        Log.i("ViewModel", "Starting polling with: ${source.displayName}")
        _connectionStatusText.value = source.displayName

        pollingJob = viewModelScope.launch {
            // Connect once before polling begins
            try {
                source.connect()
            } catch (e: Exception) {
                Log.e("ViewModel", "Connect failed: ${e.message}")
                _isConnected.value = false
            }

            startPollingLoop(source)
        }
    }

    // -------------------------------------------------------------------------
    // Polling loop (source-agnostic)
    // -------------------------------------------------------------------------

    private suspend fun startPollingLoop(source: InputSource) {
        var lastSlotNames: Map<Int, String> = emptyMap()

        while (true) {
            try {
                val inputsMap = source.poll()

                // After Inception discovers inputs, sync device names and enabled states
                if (source is InceptionInputSource) {
                    val currentSlotNames = source.slotNames
                    if (currentSlotNames.isNotEmpty() && currentSlotNames != lastSlotNames) {
                        lastSlotNames = currentSlotNames
                        syncDevicesFromInception(currentSlotNames)
                    }
                }

                if (source is WmsProInputSource) {
                    val currentSlotNames = source.slotNames
                    if (currentSlotNames.isNotEmpty() && currentSlotNames != lastSlotNames) {
                        lastSlotNames = currentSlotNames
                        syncDevicesFromInception(currentSlotNames)  // same logic — name sync only
                    }
                }

                // Empty map from long polling = no changes, keep existing state
                if (inputsMap.isNotEmpty()) {
                    _inputs.value = inputsMap
                    processInputUpdates(inputsMap)
                }

                lastSuccessfulPoll = System.currentTimeMillis()
                _isConnected.value = true
                _connectionStatusText.value = source.displayName

                if (_criticalAlert.value) {
                    _criticalAlert.value = false
                    logEvent("ℹ️ Connection to ${source.displayName} restored")
                }

            } catch (e: Exception) {
                _isConnected.value = false
                Log.e("Polling", "${source.displayName} poll error: ${e.message}")

                val timeOffline = System.currentTimeMillis() - lastSuccessfulPoll
                if (timeOffline >= 2 * 60 * 1000L && !_criticalAlert.value) {
                    _criticalAlert.value = true
                    logEvent("❌ Connection to ${source.displayName} lost (2+ minutes)")
                }

                // Back-off before retrying to avoid hammering offline devices
                delay(5000L)
            }

            // For non-long-poll sources (Moxa REST, Modbus), wait between polls.
            // For Inception long polling, poll() already blocks for up to 60s,
            // so the delay here is just a short guard between reconnects.
            if (_inputSourceType.value != InputSourceType.INCEPTION) {
                delay(3000L)
            } else {
                // Small gap between long-poll cycles to avoid hammering on error
                delay(200L)
            }
        }
    }

    /**
     * Called after Inception input discovery.
     * For each mapped slot:
     *  - Enables the device so it shows on the main screen
     *  - Sets the device name from the Inception input name
     *    (only if the user hasn't already renamed it away from the default)
     * Devices NOT in the Inception mapping are left unchanged.
     */
    private fun syncDevicesFromInception(slotNames: Map<Int, String>) {
        Log.i("ViewModel", "Syncing ${slotNames.size} devices from Inception input names")
        slotNames.forEach { (slotId, inceptionName) ->
            val device = deviceStates.find { it.id == slotId } ?: return@forEach

            // Enable the device so its icon appears on the floor plan
            if (!device.isEnabled.value) {
                device.isEnabled.value = true
                Log.d("ViewModel", "Auto-enabled slot $slotId for Inception input '$inceptionName'")
            }

            // Set name from Inception if it still has the generic default name
            val currentName = device.name.value
            val isDefaultName = currentName == "Device $slotId" || currentName == "Device"
            if (isDefaultName) {
                device.name.value = inceptionName
                Log.d("ViewModel", "Auto-named slot $slotId -> '$inceptionName'")
            }
        }
        saveDeviceStates()
    }

    // -------------------------------------------------------------------------
    // Input processing (shared across all sources)
    // -------------------------------------------------------------------------

    private fun processInputUpdates(inputsMap: Map<Int, Int>) {
        var anyUnacknowledgedActive = false
        val isInception = _inputSourceType.value == InputSourceType.INCEPTION
        val isWmsPro = _inputSourceType.value == InputSourceType.WMS_PRO

        deviceStates.forEach { device ->
            val rawValue = inputsMap[device.id] ?: 0

            // For Inception: rawValue is the PublicState bitmask
            // For Moxa/Modbus: rawValue is simply 0 or 1
            val isNowActive = if (isInception || isWmsPro) {
                if (device.alarmStateOnly.value) {
                    if (isWmsPro) {
                        // WMS Pro alarm state only — duress/alarm codes only (not tamper)
                        rawValue in setOf(3, 11, 20, 37, 123, 173, 177, 195, 217)
                    } else {
                        // Inception alarm state only — bit 3 (8) = input in alarm
                        (rawValue and (8 or 2)) != 0
                    }
                } else {
                    if (isWmsPro) {
                        rawValue != 0  // any alarm eventCode = active
                    } else {
                        // Inception — any activity bits
                        (rawValue and (1 or 2 or 8 or 2048)) != 0
                    }
                }
            } else {
                rawValue == 1
            }

            if (isNowActive && !device.isActive.value) {
                device.isActive.value = true
                device.acknowledged.value = false

                logEvent("🚨 ${device.name.value} ACTIVATED")

                if (device.smsEnabled.value) {
                    contextRef?.let { ctx ->
                        viewModelScope.launch {
                            sendSmsAlert(ctx, device, smsConfig)
                            logEvent("📤 SMS sent for ${device.name.value}")
                        }
                    }
                }
            }

            if (device.isActive.value && !device.acknowledged.value) {
                anyUnacknowledgedActive = true
            }
        }

        if (anyUnacknowledgedActive) {
            if (beepPlayer == null || beepPlayer?.isPlaying == false) {
                beepPlayer = repository.playCriticalBeep(beepPlayer)
            }
        } else {
            stopBeeperSafely()
        }
    }

    // -------------------------------------------------------------------------
    // Reset
    // -------------------------------------------------------------------------

    fun resetAlerts() {
        try {
            val currentInputs = inputs.value

            // Only allow reset if all mapped inputs are currently inactive
            val allInactive = currentInputs.values.all { it == 0 }
            if (!allInactive) {
                Toast.makeText(contextRef, "❗ All devices must be OFF before resetting.", Toast.LENGTH_LONG).show()
                return
            }

            deviceStates.forEach {
                if (it.isActive.value || !it.acknowledged.value) {
                    it.isActive.value = false
                    it.acknowledged.value = true
                    logEvent("✅ ${it.name.value} manually reset")
                }
            }

            stopBeeperSafely()

            if (_criticalAlert.value) {
                _criticalAlert.value = false
                logEvent("🔕 Critical alert cleared")
            }

        } catch (e: Exception) {
            Log.e("ResetError", "Reset error: ${e.message}", e)
        }
    }

    private fun stopBeeperSafely() {
        try {
            beepPlayer?.let { player ->
                if (player.isPlaying) player.stop()
                player.reset()
                player.release()
            }
            beepPlayer = null
        } catch (e: Exception) {
            Log.e("Beeper", "Error stopping beeper: ${e.message}", e)
        }
    }

    // -------------------------------------------------------------------------
    // Event log
    // -------------------------------------------------------------------------

    private fun logEvent(message: String) {
        contextRef?.let { ctx ->
            _eventLog.add(0, EventLogEntry(System.currentTimeMillis(), message))
            if (_eventLog.size > 50) _eventLog.removeAt(_eventLog.lastIndex)
            saveEventLog(ctx)
        }
    }

    private fun saveEventLog(context: Context) {
        val file = File(context.filesDir, "event_log.json")
        file.writeText(Json.encodeToString(_eventLog.toList()))
    }

    private fun loadEventLog(context: Context) {
        val file = File(context.filesDir, "event_log.json")
        if (file.exists()) {
            try {
                val loaded = Json.decodeFromString<List<EventLogEntry>>(file.readText())
                _eventLog.clear()
                _eventLog.addAll(loaded)
            } catch (e: Exception) {
                Log.e("ViewModel", "Failed to load event log: ${e.message}")
            }
        }
    }

    // -------------------------------------------------------------------------
    // Persistence helpers
    // -------------------------------------------------------------------------

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

    fun setMoxa2Ip(ip: String) {
        _moxa2Ip.value = ip
        contextRef?.let { saveMoxa2Ip(it, ip) }
    }

    fun changePassword(newPassword: String) {
        currentPassword = newPassword
        contextRef?.let { savePassword(it, newPassword) }
    }

    fun saveFloorplanTransform(context: Context, sx: Float, sy: Float, ox: Float, oy: Float, aspectLock: Boolean) {
        saveFloat(context, "scaleX", sx)
        saveFloat(context, "scaleY", sy)
        saveFloat(context, "offsetX", ox)
        saveFloat(context, "offsetY", oy)
        saveBool(context, "aspectLock", aspectLock)
    }

    // -------------------------------------------------------------------------
    // Inception config persistence
    // -------------------------------------------------------------------------

    fun saveInceptionConfig(context: Context) {
        val prefs = context.getSharedPreferences("inception_config", Context.MODE_PRIVATE)
        prefs.edit().putString("config", Json.encodeToString(inceptionConfig.toSerializable())).apply()
        // Reset discovery so next poll immediately re-syncs input names
        (activeInputSource as? InceptionInputSource)?.resetDiscovery()
    }

    private fun loadInceptionConfig(context: Context) {
        val prefs = context.getSharedPreferences("inception_config", Context.MODE_PRIVATE)
        val json = prefs.getString("config", null)
        if (json != null) {
            try {
                inceptionConfig = InceptionConfig.fromSerializable(Json.decodeFromString(json))
            } catch (e: Exception) {
                Log.e("ViewModel", "Failed to load Inception config: ${e.message}")
            }
        }
    }

    private fun saveMoxa2Ip(context: Context, ip: String) {
        context.getSharedPreferences("duress_prefs", Context.MODE_PRIVATE)
            .edit().putString("moxa2_ip", ip).apply()
    }

    private fun loadMoxa2Ip(context: Context): String {
        return context.getSharedPreferences("duress_prefs", Context.MODE_PRIVATE)
            .getString("moxa2_ip", "192.168.0.251") ?: "192.168.0.251"
    }

    // Input source type persistence
    private fun saveInputSourceType(context: Context, type: InputSourceType) {
        context.getSharedPreferences("duress_prefs", Context.MODE_PRIVATE)
            .edit().putString("input_source_type", type.name).apply()
    }

    private fun loadInputSourceSettings(context: Context) {
        val prefs = context.getSharedPreferences("duress_prefs", Context.MODE_PRIVATE)
        val typeName = prefs.getString("input_source_type", InputSourceType.MOXA_REST.name)
        _inputSourceType.value = try {
            InputSourceType.valueOf(typeName ?: InputSourceType.MOXA_REST.name)
        } catch (e: Exception) {
            InputSourceType.MOXA_REST
        }
    }

    // -------------------------------------------------------------------------
    // Inception config update methods (called from Settings UI)
    // -------------------------------------------------------------------------

    fun updateInceptionHost(value: String) { inceptionConfig.host.value = value }
    fun updateInceptionUsername(value: String) { inceptionConfig.username.value = value }
    fun updateInceptionPassword(value: String) { inceptionConfig.password.value = value }

    fun updateInceptionInputMapping(guid: String, slotId: Int?) {
        val current = inceptionConfig.inputMappings.value.toMutableMap()
        if (slotId == null) current.remove(guid) else current[guid] = slotId
        inceptionConfig.inputMappings.value = current
    }

    fun clearInceptionInputMappings() {
        inceptionConfig.inputMappings.value = emptyMap()
    }

    /**
     * Fetch available inputs from the Inception controller for the Settings UI.
     * Returns null on failure (caller shows error toast).
     */
    suspend fun fetchInceptionInputs(): List<com.example.visualduress.integration.InceptionInputInfo>? {
        return try {
            val source = activeInputSource
            val result = if (source is InceptionInputSource) {
                source.fetchAvailableInputs()
            } else {
                val tempSource = InceptionInputSource(inceptionConfig)
                tempSource.connect()
                tempSource.fetchAvailableInputs()
            }
            // Cache the names so we can sync immediately on Save & Apply
            result?.forEach { info ->
                _cachedInceptionInputNames[info.id] = info.name
            }
            result
        } catch (e: Exception) {
            Log.e("ViewModel", "Failed to fetch Inception inputs: ${e.message}")
            null
        }
    }

    /**
     * Sync device names and enable states immediately from the current
     * Inception input mappings and cached input names.
     * Called when Save & Apply is tapped — no poll cycle needed.
     */
    fun syncDevicesFromMappingsNow() {
        val mappings = inceptionConfig.inputMappings.value
        if (mappings.isEmpty()) return

        mappings.forEach { (guid, slotId) ->
            val device = deviceStates.find { it.id == slotId } ?: return@forEach
            val inceptionName = _cachedInceptionInputNames[guid] ?: return@forEach

            // Update name if still default
            val isDefaultName = device.name.value == "Device $slotId" || device.name.value == "Device"
            if (isDefaultName) {
                device.name.value = inceptionName
                Log.d("ViewModel", "Synced name slot $slotId -> '$inceptionName'")
            }
        }
        saveDeviceStates()
    }

    // -------------------------------------------------------------------------
    // WMS Pro config
    // -------------------------------------------------------------------------

    fun updateWmsProHost(value: String)        { wmsProConfig.host.value = value }
    fun updateWmsProToken(value: String)        { wmsProConfig.bearerToken.value = value }

    fun updateWmsProDeviceMapping(uid: String, slotId: Int?) {
        val current = wmsProConfig.deviceMappings.value.toMutableMap()
        if (slotId == null) current.remove(uid) else current[uid] = slotId
        wmsProConfig.deviceMappings.value = current
    }

    fun clearWmsProDeviceMappings() {
        wmsProConfig.deviceMappings.value = emptyMap()
    }

    fun saveWmsProConfig(context: Context) {
        val prefs = context.getSharedPreferences("wmspro_config", Context.MODE_PRIVATE)
        prefs.edit().putString("config", Json.encodeToString(wmsProConfig.toSerializable())).apply()
        (activeInputSource as? WmsProInputSource)?.resetDiscovery()
    }

    private fun loadWmsProConfig(context: Context) {
        val prefs = context.getSharedPreferences("wmspro_config", Context.MODE_PRIVATE)
        val json = prefs.getString("config", null) ?: return
        try {
            wmsProConfig.loadFrom(Json.decodeFromString<SerializableWmsProConfig>(json))
        } catch (e: Exception) {
            Log.e("ViewModel", "Failed to load WMS Pro config: ${e.message}")
        }
    }

    /** Cached device names from last Load Devices call — for immediate name sync */
    private val _cachedWmsProDeviceNames = mutableMapOf<String, String>()

    suspend fun fetchWmsProDevices(): List<WmsProDeviceInfo>? {
        return try {
            val source = activeInputSource
            val result = if (source is WmsProInputSource) {
                source.fetchAvailableDevices()
            } else {
                val tempSource = WmsProInputSource(wmsProConfig)
                tempSource.connect()
                tempSource.fetchAvailableDevices()
            }
            result?.forEach { info -> _cachedWmsProDeviceNames[info.uid] = info.name }
            result
        } catch (e: Exception) {
            Log.e("ViewModel", "Failed to fetch WMS Pro devices: ${e.message}")
            null
        }
    }

    fun syncDevicesFromWmsMappingsNow() {
        val mappings = wmsProConfig.deviceMappings.value
        if (mappings.isEmpty()) return
        mappings.forEach { (uid, slotId) ->
            val device = deviceStates.find { it.id == slotId } ?: return@forEach
            val name = _cachedWmsProDeviceNames[uid] ?: return@forEach
            val isDefault = device.name.value == "Device $slotId" || device.name.value == "Device"
            if (isDefault) {
                device.name.value = name
                Log.d("ViewModel", "WMS Pro synced name slot $slotId -> '$name'")
            }
        }
        saveDeviceStates()
    }

    // -------------------------------------------------------------------------
    // SMS
    // -------------------------------------------------------------------------

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
                Log.e("ViewModel", "Failed to load SMS config: ${e.message}")
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

            val recipients = smsConfig.smsNumbers.filter { it.number.value.trim().isNotEmpty() }
            if (recipients.isEmpty()) {
                Toast.makeText(context, "⚠️ Please enter at least one phone number.", Toast.LENGTH_LONG).show()
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
                        Toast.makeText(context, "✅ Test SMS sent to ${entry.label.value}", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "❌ SMS failed to ${entry.label.value}", Toast.LENGTH_SHORT).show()
                        Log.e("SMS", "Error [$responseCode]: $response")
                    }

                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "❌ Error sending SMS to ${entry.label.value}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    // -------------------------------------------------------------------------
    // UI actions
    // -------------------------------------------------------------------------

    fun setActiveTab(tab: String) { _activeTab.value = tab }

    fun promptPasswordForSettings() {
        pendingAction = { _showSettings.value = true }
        _passwordPromptVisible.value = true
    }

    fun showAboutDialog() { _showAbout.value = true }
    fun closeAboutDialog() { _showAbout.value = false }
    fun hidePasswordPrompt() { _passwordPromptVisible.value = false }

    fun verifyPassword(input: String) {
        if (input == currentPassword || verifyMasterPassword(input)) {
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

    fun closeSettings() { _showSettings.value = false }

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
}