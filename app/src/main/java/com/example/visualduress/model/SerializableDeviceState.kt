package com.example.visualduress.model

import androidx.compose.runtime.mutableStateOf

data class SerializableDeviceState(
    val id: Int,
    val name: String,
    val x: Float,
    val y: Float,
    val size: Float,
    val isEnabled: Boolean,
    val isActive: Boolean,
    val acknowledged: Boolean,
    val isLicensed: Boolean,
    val streamUrl: String,
    val cameraEnabled: Boolean,
    val smsEnabled: Boolean,
    val labelColor: String = "white"   // default keeps existing saved data compatible
)

fun DeviceState.toSerializable(): SerializableDeviceState {
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
        labelColor = labelColor.value
    )
}

fun SerializableDeviceState.toDeviceState(): DeviceState {
    return DeviceState(
        id = id,
        name = mutableStateOf(name),
        x = mutableStateOf(x),
        y = mutableStateOf(y),
        size = mutableStateOf(size),
        isEnabled = mutableStateOf(isEnabled),
        isActive = mutableStateOf(isActive),
        acknowledged = mutableStateOf(acknowledged),
        isLicensed = mutableStateOf(isLicensed),
        streamUrl = mutableStateOf(streamUrl),
        cameraEnabled = mutableStateOf(cameraEnabled),
        smsEnabled = mutableStateOf(smsEnabled),
        labelColor = mutableStateOf(labelColor)
    )
}
