/*package com.example.visualduress.ui

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
}*/

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.consumeAllChanges
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
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
import com.example.visualduress.ui.theme.backgroundGradient
import com.example.visualduress.ui.theme.onlineColor
import com.example.visualduress.ui.theme.resetButtonColor
import com.example.visualduress.ui.theme.topBarColor
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
        LicensePromptDialog(
            viewModel = viewModel,
            onLicenseValidated = { licensePromptVisible = false })
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

    // Define color scheme from Figma
   // Teal for online status

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundGradient)
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
        // Top Navigation Bar
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(72.dp)
                .zIndex(10f),
            color = topBarColor,
            elevation = 4.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left side - Logo and Title
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.vadlogo),
                        contentDescription = "App Logo",
                        modifier = Modifier.height(40.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Visual Alert Display",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                // Right side - Controls
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Online Status Badge
                    /*Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = if (isConnected) onlineColor.copy(alpha = 0.2f) else Color.Red.copy(alpha = 0.2f),
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            // Status indicator dot
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .background(
                                        color = if (isConnected) Color(0xFF00E676) else Color.Red,
                                        shape = RoundedCornerShape(4.dp)
                                    )
                            )
                            Text(
                                text = if (isConnected) "Online" else "Offline",
                                color = if (isConnected) Color.White else Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }*/
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (isConnected)
                            onlineColor.copy(alpha = 0.2f)
                        else
                            Color.Red.copy(alpha = 0.2f),
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {

                            // Status indicator dot
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .background(
                                        color = if (isConnected) Color(0xFF00E676) else Color.Red,
                                        shape = CircleShape
                                    )
                            )

                            Text(
                                text = if (isConnected) "Online" else "Offline",
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    // Settings Icon
                    IconButton(onClick = { viewModel.promptPasswordForSettings() }) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_settings),
                            contentDescription = stringResource(R.string.settings_button),
                            tint = Color.White
                        )
                    }

                    // Lock/Unlock Icon
                    IconButton(onClick = { viewModel.toggleUnlock() }) {
                        Icon(
                            imageVector = if (unlockLayout) Icons.Filled.LockOpen else Icons.Filled.Lock,
                            contentDescription = if (unlockLayout) stringResource(R.string.unlock_button) else stringResource(R.string.lock_button),
                            tint = Color.White
                        )
                    }

                    // Menu Icon
                    IconButton(onClick = { showEventLog.value = true }) {
                        Icon(
                            Icons.Default.Menu,
                            contentDescription = "Menu",
                            tint = Color.White
                        )
                    }
                }
            }
        }

        // Main content area with floorplan
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 72.dp, bottom = 100.dp) // Space for top bar and bottom button
        ) {
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

            // Critical Alert Banner
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
                        .padding(top = 16.dp)
                        .background(Color.Red, shape = RoundedCornerShape(8.dp))
                        .alpha(flashAlpha.value)
                        .zIndex(3f)
                ) {
                    Text(
                        text = stringResource(R.string.critical_connection_lost),
                        color = Color.White,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp)
                    )
                }
            }

            // Device markers
            /*viewModel.deviceStates.forEach { device ->
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
                                R.drawable.icon_alert else R.drawable.icon_normal_new,
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
            }*/
            // Get screen dimensions - add this before the forEach loop
            val configuration = LocalConfiguration.current
            val screenWidth = configuration.screenWidthDp.dp.value
            val screenHeight = configuration.screenHeightDp.dp.value

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

                                    // Calculate new position
                                    val newX = device.x.value + dragAmount.x
                                    val newY = device.y.value + dragAmount.y

                                    // Get device icon size
                                    val iconSize = device.size.value

                                    // Constrain within screen bounds
                                    device.x.value = newX.coerceIn(0f, screenWidth - iconSize)
                                    device.y.value = newY.coerceIn(0f, screenHeight - iconSize - 72f) // 72f accounts for top bar

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
                                R.drawable.icon_alert else R.drawable.icon_normal_new,
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
        }

        // Fullscreen camera player
        if (showFullscreen) {
            FullscreenCameraPlayer(
                streamUrl = fullscreenUrl,
                onExit = { showFullscreen = false }
            )
        }

        // Bottom Reset Button
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 50.dp, vertical = 24.dp)
                .zIndex(5f)
        ) {
            Button(
                onClick = { viewModel.resetAlerts() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                colors = ButtonDefaults.buttonColors(
                    backgroundColor = resetButtonColor,
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(28.dp),
                elevation = ButtonDefaults.elevation(
                    defaultElevation = 8.dp,
                    pressedElevation = 12.dp
                )
            ) {
                Row(
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Reset",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "→",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // Settings Dialog
        if (showSettings) {
            SettingsDialog(viewModel = viewModel, launcher = launcher)
        }

        // Password Dialog
        PasswordDialog(
            visible = passwordPromptVisible,
            onDismiss = { viewModel.hidePasswordPrompt() },
            onConfirm = { viewModel.verifyPassword(it) }
        )

        // Event Log Popup
        if (showEventLog.value) {
            EventLogPopup(
                log = eventLog,
                onClose = { showEventLog.value = false }
            )
        }
    }
}
