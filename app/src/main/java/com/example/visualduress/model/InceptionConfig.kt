package com.example.visualduress.model

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import kotlinx.serialization.Serializable

/**
 * Serializable form of InceptionConfig — stored in SharedPreferences as JSON.
 */
@Serializable
data class SerializableInceptionConfig(
    val host: String = "",
    val username: String = "",
    val password: String = "",
    val pollIntervalMs: Long = 3000L,
    /**
     * Maps Inception Input GUIDs to VAD device slot IDs (1-based).
     * Key = Inception Input GUID string
     * Value = device slot index (matches DeviceState.id)
     *
     * Example: "07590f0f-e958-4c25-917f-62cf5e46214c" -> 1
     *
     * This allows the user to map any Inception input to any VAD device.
     */
    val inputMappings: Map<String, Int> = emptyMap()
)

/**
 * Runtime (Compose-observable) version of InceptionConfig.
 */
class InceptionConfig(
    host: String = "",
    username: String = "",
    password: String = "",
    pollIntervalMs: Long = 3000L,
    inputMappings: Map<String, Int> = emptyMap()
) {
    var host: MutableState<String> = mutableStateOf(host)
    var username: MutableState<String> = mutableStateOf(username)
    var password: MutableState<String> = mutableStateOf(password)
    var pollIntervalMs: MutableState<Long> = mutableStateOf(pollIntervalMs)
    var inputMappings: MutableState<Map<String, Int>> = mutableStateOf(inputMappings)

    fun toSerializable() = SerializableInceptionConfig(
        host = host.value.trim(),
        username = username.value,
        password = password.value,
        pollIntervalMs = pollIntervalMs.value,
        inputMappings = inputMappings.value
    )

    companion object {
        fun fromSerializable(data: SerializableInceptionConfig) = InceptionConfig(
            host = data.host,
            username = data.username,
            password = data.password,
            pollIntervalMs = data.pollIntervalMs,
            inputMappings = data.inputMappings
        )
    }
}
