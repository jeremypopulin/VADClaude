package com.example.visualduress.model

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import kotlinx.serialization.Serializable

/**
 * Live Compose-observable config for Tecom WMS Pro.
 * Fields are MutableState so changes in Settings immediately
 * propagate to the polling source without restart.
 */
class WmsProConfig {
    /** Base URL of WMS Pro server e.g. http://192.168.1.100 */
    val host: MutableState<String> = mutableStateOf("")

    /** Pre-generated Bearer token from WMS Pro Operator settings */
    val bearerToken: MutableState<String> = mutableStateOf("")

    /**
     * Manual device UID -> VAD slot mappings.
     * Key   = WMS Pro deviceUid string e.g. "2.1812.1"
     * Value = VAD device slot (1–32)
     */
    val deviceMappings: MutableState<Map<String, Int>> = mutableStateOf(emptyMap())

    fun toSerializable() = SerializableWmsProConfig(
        host          = host.value,
        bearerToken   = bearerToken.value,
        deviceMappings = deviceMappings.value
    )

    fun loadFrom(data: SerializableWmsProConfig) {
        host.value          = data.host
        bearerToken.value   = data.bearerToken
        deviceMappings.value = data.deviceMappings
    }
}

@Serializable
data class SerializableWmsProConfig(
    val host:           String           = "",
    val bearerToken:    String           = "",
    val deviceMappings: Map<String, Int> = emptyMap()
)