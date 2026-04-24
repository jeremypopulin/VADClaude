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
    var alarmStateOnly: MutableState<Boolean> = mutableStateOf(false),
    /** Show a camera icon next to the device on the floor plan for manual live view */
    var showCameraIcon: MutableState<Boolean> = mutableStateOf(false),
    /**
     * Force-acknowledged by operator via 10s long press.
     * Device is silenced even though input is still active.
     * Automatically clears when the input restores to inactive.
     * NOT persisted — always resets to false on app restart.
     */
    var isForceAcknowledged: MutableState<Boolean> = mutableStateOf(false)
)