package com.example.visualduress.model

import androidx.compose.runtime.*

data class DeviceState(
    val id: Int,
    var name: MutableState<String> = mutableStateOf("Device"),
    var x: MutableState<Float> = mutableStateOf(0f),
    var y: MutableState<Float> = mutableStateOf(0f),
    var size: MutableState<Float> = mutableStateOf(50f),
    var isEnabled: MutableState<Boolean> = mutableStateOf(false),
    var isActive: MutableState<Boolean> = mutableStateOf(false),
    var acknowledged: MutableState<Boolean> = mutableStateOf(false),
    var isLicensed: MutableState<Boolean> = mutableStateOf(false),
    var streamUrl: MutableState<String> = mutableStateOf(""),
    var cameraEnabled: MutableState<Boolean> = mutableStateOf(false),
    var smsEnabled: MutableState<Boolean> = mutableStateOf(false),
    var labelColor: MutableState<String> = mutableStateOf("white"),
    // When true: only trigger on Inception alarm state (area must be armed)
    // When false: trigger on any input activity (unsealed/active)
    var alarmStateOnly: MutableState<Boolean> = mutableStateOf(false)
) {
    fun toSerializable(): SerializableDeviceState {
        return SerializableDeviceState(
            id = id,
            name = name.value,
            x = x.value,
            y = y.value,
            size = size.value,
            isEnabled = isEnabled.value,
            isActive = isActive.value,
            acknowledged = acknowledged.value,
            isLicensed = isLicensed.value,
            streamUrl = streamUrl.value,
            cameraEnabled = cameraEnabled.value,
            smsEnabled = smsEnabled.value,
            labelColor = labelColor.value,
            alarmStateOnly = alarmStateOnly.value
        )
    }

    companion object {
        fun fromSerializable(data: SerializableDeviceState): DeviceState {
            return DeviceState(
                id = data.id,
                name = mutableStateOf(data.name),
                x = mutableStateOf(data.x),
                y = mutableStateOf(data.y),
                size = mutableStateOf(data.size),
                isEnabled = mutableStateOf(data.isEnabled),
                isActive = mutableStateOf(data.isActive),
                acknowledged = mutableStateOf(data.acknowledged),
                isLicensed = mutableStateOf(data.isLicensed),
                streamUrl = mutableStateOf(data.streamUrl),
                cameraEnabled = mutableStateOf(data.cameraEnabled),
                smsEnabled = mutableStateOf(data.smsEnabled),
                labelColor = mutableStateOf(data.labelColor),
                alarmStateOnly = mutableStateOf(data.alarmStateOnly)
            )
        }
    }
}