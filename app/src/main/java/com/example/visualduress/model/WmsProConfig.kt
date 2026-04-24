package com.example.visualduress.model

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import kotlinx.serialization.Serializable

class WmsProConfig {
    val host: MutableState<String> = mutableStateOf("")
    val username: MutableState<String> = mutableStateOf("")
    val password: MutableState<String> = mutableStateOf("")
    val apiKey: MutableState<String> = mutableStateOf("")
    val deviceMappings: MutableState<Map<String, Int>> = mutableStateOf(emptyMap())

    fun toSerializable() = SerializableWmsProConfig(
        host = host.value,
        username = username.value,
        password = password.value,
        apiKey = apiKey.value,
        deviceMappings = deviceMappings.value
    )

    fun loadFrom(data: SerializableWmsProConfig) {
        host.value = data.host
        username.value = data.username
        password.value = data.password
        apiKey.value = data.apiKey
        deviceMappings.value = data.deviceMappings
    }
}

@Serializable
data class SerializableWmsProConfig(
    val host: String = "",
    val username: String = "",
    val password: String = "",
    val apiKey: String = "",
    val deviceMappings: Map<String, Int> = emptyMap()
)