package com.example.visualduress.ui

import android.app.Activity
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import android.content.Intent
import android.net.Uri
import android.view.WindowManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.consumeAllChanges
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.visualduress.R
import com.example.visualduress.model.EventLogEntry
import com.example.visualduress.ui.components.CameraPreviewPopup
import com.example.visualduress.ui.components.EventLogPopup
import com.example.visualduress.ui.components.FullscreenCameraPlayer
import com.example.visualduress.ui.components.LicensePromptDialog
import com.example.visualduress.ui.components.PasswordDialog
import com.example.visualduress.viewmodel.DeviceViewModel
import com.example.visualduress.util.LicenseManager

@Composable
fun MainScreen(viewModel: DeviceViewModel) {
    val context = LocalContext.current
    val activity = context as? Activity
    LaunchedEffect(Unit) {
        activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    var licensePromptVisible by remember { mutableStateOf(!LicenseManager.isLicenseValid(context)) }
    if (licensePromptVisible) {
        LicensePromptDialog(onLicenseValidated = { licensePromptVisible = false })
        return
    }

    val floorplanUri by viewModel.floorplanUri
    val isConnected by viewModel.isConnected
    val criticalAlert by viewModel.criticalAlert
    val showSettings by viewModel.showSettings
    val unlockLayout by viewModel.unlockLayout
    val passwordPromptVisible by viewModel.passwordPromptVisible

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri?.let {
            context.contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            viewModel.setFloorplanUri(it)
        }
    }

    var scaleX by remember { mutableStateOf(viewModel.savedScaleX) }
    var scaleY by remember { mutableStateOf(viewModel.savedScaleY) }
    var offsetX by remember { mutableStateOf(viewModel.savedOffsetX) }
    var offsetY by remember { mutableStateOf(viewModel.savedOffsetY) }
    var lockAspectRatio by remember { mutableStateOf(viewModel.savedAspectLock) }

    var showFullscreen by remember { mutableStateOf(false) }
    var fullscreenUrl by remember { mutableStateOf("") }

    val showEventLog = remember { mutableStateOf(false) }
    val eventLog = viewModel.eventLog

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(unlockLayout) {
                if (unlockLayout) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        offsetX += pan.x
                        offsetY += pan.y
                        if (lockAspectRatio) {
                            scaleX = (scaleX * zoom).coerceIn(0.5f, 4f)
                            scaleY = scaleX
                        } else {
                            scaleX += pan.x * 0.005f
                            scaleY += pan.y * 0.005f
                            scaleX = scaleX.coerceIn(0.5f, 4f)
                            scaleY = scaleY.coerceIn(0.5f, 4f)
                        }
                        viewModel.saveFloorplanTransform(context, scaleX, scaleY, offsetX, offsetY, lockAspectRatio)
                    }
                }
            }
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp)
                .zIndex(5f),
            contentAlignment = Alignment.TopCenter
        ) {
            Image(
                painter = painterResource(id = R.drawable.logo),
                contentDescription = "App Logo",
                modifier = Modifier.height(96.dp)
            )
        }

        floorplanUri?.let {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer(
                        scaleX = scaleX,
                        scaleY = scaleY,
                        translationX = offsetX,
                        translationY = offsetY
                    )
            ) {
                AsyncImage(
                    model = ImageRequest.Builder(context).data(it).build(),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        Text(
            text = if (isConnected) stringResource(R.string.status_online) else stringResource(R.string.status_offline),
            color = if (isConnected) Color.Green else Color.Red,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(12.dp)
                .zIndex(2f)
        )

        if (criticalAlert) {
            val flashAlpha = remember { Animatable(1f) }
            LaunchedEffect(Unit) {
                while (true) {
                    flashAlpha.animateTo(0.3f)
                    flashAlpha.animateTo(1f)
                }
            }
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 48.dp)
                    .background(Color.Red)
                    .alpha(flashAlpha.value)
                    .zIndex(3f)
            ) {
                Text(
                    text = stringResource(R.string.critical_connection_lost),
                    color = Color.White,
                    fontSize = 24.sp,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
        }

        viewModel.deviceStates.forEach { device ->
            if (!device.isEnabled.value) return@forEach

            Box(
                modifier = Modifier
                    .offset(device.x.value.dp, device.y.value.dp)
                    .zIndex(2f)
                    .pointerInput(unlockLayout) {
                        if (unlockLayout) {
                            detectDragGestures { change, dragAmount ->
                                change.consumeAllChanges()
                                device.x.value += dragAmount.x
                                device.y.value += dragAmount.y
                                viewModel.saveDeviceStates()
                            }
                        }
                    }
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    val flashAlpha = remember { Animatable(1f) }
                    LaunchedEffect(device.isActive.value, device.acknowledged.value) {
                        if (device.isActive.value && !device.acknowledged.value) {
                            while (true) {
                                flashAlpha.animateTo(0.3f)
                                flashAlpha.animateTo(1f)
                            }
                        } else {
                            flashAlpha.snapTo(1f)
                        }
                    }

                    AsyncImage(
                        model = if (device.isActive.value && !device.acknowledged.value)
                            R.drawable.icon_alert else R.drawable.icon_normal,
                        contentDescription = null,
                        modifier = Modifier
                            .size(device.size.value.dp)
                            .alpha(flashAlpha.value)
                    )
                    Text(
                        text = device.name.value,
                        color = Color.White,
                        fontSize = (device.size.value / 5).coerceAtLeast(10f).sp
                    )

                    if (device.isActive.value && device.cameraEnabled.value && device.streamUrl.value.isNotEmpty()) {
                        var showPopup by remember { mutableStateOf(true) }

                        if (showPopup) {
                            CameraPreviewPopup(
                                streamUrl = device.streamUrl.value,
                                onTap = {
                                    fullscreenUrl = device.streamUrl.value
                                    showFullscreen = true
                                },
                                onClose = {
                                    showPopup = false
                                }
                            )
                        }
                    }

                }
            }
        }

        if (showFullscreen) {
            FullscreenCameraPlayer(
                streamUrl = fullscreenUrl,
                onExit = { showFullscreen = false }
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, top = 64.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { viewModel.promptPasswordForSettings() }) {
                    Icon(Icons.Filled.Settings, contentDescription = stringResource(R.string.settings_button), tint = Color.Gray)
                }
                Spacer(modifier = Modifier.width(8.dp))
                IconButton(onClick = { viewModel.toggleUnlock() }) {
                    Icon(
                        imageVector = if (unlockLayout) Icons.Filled.LockOpen else Icons.Filled.Lock,
                        contentDescription = if (unlockLayout) stringResource(R.string.unlock_button) else stringResource(R.string.lock_button),
                        tint = Color.Gray
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                IconButton(onClick = { showEventLog.value = true }) {
                    Icon(Icons.Default.List, contentDescription = "Event Log", tint = Color.Gray)
                }
            }

            Button(
                onClick = { viewModel.resetAlerts() },
                colors = ButtonDefaults.buttonColors(backgroundColor = Color.Red, contentColor = Color.White)
            ) {
                Text(stringResource(R.string.reset_button))
            }
        }

        if (showSettings) {
            SettingsDialog(viewModel = viewModel, launcher = launcher)
        }

        PasswordDialog(
            visible = passwordPromptVisible,
            onDismiss = { viewModel.hidePasswordPrompt() },
            onConfirm = { viewModel.verifyPassword(it) }
        )

        if (showEventLog.value) {
            EventLogPopup(
                log = eventLog,
                onClose = { showEventLog.value = false }
            )
        }
    }
}
