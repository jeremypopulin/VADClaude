package com.example.visualduress.model

import androidx.compose.runtime.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

@Serializable
data class SerializableSmsNumber(
    val label: String = "",
    val number: String = ""
)

@Serializable
data class SerializableSmsConfig(
    val smsNumbers: List<SerializableSmsNumber> = List(5) { SerializableSmsNumber() },
    val gatewayUrl: String = "",
    val username: String = "",
    val password: String = "",
    val apiKey: String = "",
    val senderId: String = ""
)

/**
 * Non-serializable version used at runtime with Compose support.
 */
class SmsNumber(
    label: String = "",
    number: String = ""
) {
    var label: MutableState<String> = mutableStateOf(label)
    var number: MutableState<String> = mutableStateOf(number)
}

class SmsConfig(
    smsNumbers: List<SmsNumber> = List(5) { SmsNumber() },
    gatewayUrl: String = "",
    username: String = "",
    password: String = "",
    apiKey: String = "",
    senderId: String = ""
) {
    var smsNumbers: List<SmsNumber> = smsNumbers
    var gatewayUrl: MutableState<String> = mutableStateOf(gatewayUrl)
    var username: MutableState<String> = mutableStateOf(username)
    var password: MutableState<String> = mutableStateOf(password)
    var apiKey: MutableState<String> = mutableStateOf(apiKey)
    var senderId: MutableState<String> = mutableStateOf(senderId)

    fun toSerializable(): SerializableSmsConfig {
        return SerializableSmsConfig(
            smsNumbers = smsNumbers.map { SerializableSmsNumber(it.label.value, it.number.value) },
            gatewayUrl = gatewayUrl.value,
            username = username.value,
            password = password.value,
            apiKey = apiKey.value,
            senderId = senderId.value
        )
    }

    companion object {
        fun fromSerializable(data: SerializableSmsConfig): SmsConfig {
            return SmsConfig(
                smsNumbers = data.smsNumbers.map { SmsNumber(it.label, it.number) },
                gatewayUrl = data.gatewayUrl,
                username = data.username,
                password = data.password,
                apiKey = data.apiKey,
                senderId = data.senderId
            )
        }
    }
}
