package com.example.visualduress.ui

import android.content.Context
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.example.visualduress.R
import com.example.visualduress.integration.InceptionInputInfo
import com.example.visualduress.integration.InputSourceType
import com.example.visualduress.ui.components.CameraUrlBuilder
import com.example.visualduress.ui.theme.AccentBlue
import com.example.visualduress.ui.theme.AccentOrange
import com.example.visualduress.ui.theme.ActiveTabColor
import com.example.visualduress.ui.theme.CloseButtonColor
import com.example.visualduress.ui.theme.DarkBlue
import com.example.visualduress.ui.theme.DialogBackground
import com.example.visualduress.ui.theme.InputFieldBackground
import com.example.visualduress.ui.theme.TabIconBackground
import com.example.visualduress.ui.theme.TextPrimary
import com.example.visualduress.ui.theme.TextSecondary
import com.example.visualduress.viewmodel.DeviceViewModel
import com.example.visualduress.util.*
import kotlinx.coroutines.launch

@Composable
fun SettingsDialog(
    viewModel: DeviceViewModel,
    launcher: ActivityResultLauncher<Array<String>>
) {
    val context = LocalContext.current
    val licenseType by viewModel.licenseType
    val isBasic = licenseType != "PREMIUM"
    val licenceState = LicenseManager.getLicenceState(context)
    val initialTab = when (licenceState) {
        LicenseManager.LicenceState.NONE,
        LicenseManager.LicenceState.LOCKED -> "license"
        else -> "ip"
    }
    val activeTabState = remember { mutableStateOf(initialTab) }
    val activeTab by activeTabState
    viewModel.setActiveTab(activeTab)

    val floorplanUri by viewModel.floorplanUri
    val modbusIp by viewModel.modbusIp
    var newPassword by remember { mutableStateOf("") }

    val companyName = remember { loadCompanyName(context) }
    val websiteUrl = remember { loadWebsiteUrl(context) }
    val appVersion = remember { loadAppVersion(context) }

    var licenseKey by remember { mutableStateOf("") }
    val deviceId = remember { LicenseManager.getDeviceId(context) }

    val licenseFileLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            val inputStream = context.contentResolver.openInputStream(it)
            val reader = inputStream?.bufferedReader()
            licenseKey = reader?.readLine().orEmpty()
        }
    }

    Dialog(
        onDismissRequest = { viewModel.closeSettings() },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.9f),
            color = DialogBackground,
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                ModernDialogHeader()
                ModernTabNavigation(
                    activeTab = activeTab,
                    isBasic = isBasic,
                    onTabChange = { activeTabState.value = it }
                )
                Spacer(modifier = Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                ) {
                    when (activeTab) {
                        "ip"        -> IpContent(viewModel, modbusIp, context)
                        "devices"   -> DeviceSettingsContent(viewModel, isBasic)
                        "floorplan" -> FloorplanContent(viewModel, launcher, floorplanUri)
                        "password"  -> PasswordContent(
                            viewModel, newPassword, context,
                            onPasswordChange = { newPassword = it }
                        )
                        "sms"       -> { if (!isBasic) SmsSettings(viewModel) }
                        "license"   -> LicenseContent(
                            viewModel, context, licenseKey,
                            onLicenseKeyChange = { licenseKey = it },
                            deviceId, licenseType
                        )
                        "about"     -> AboutContent(appVersion, companyName, websiteUrl)
                    }
                }
                ModernCloseButton(onClick = { viewModel.closeSettings() })
            }
        }
    }
}

@Composable
private fun ModernDialogHeader() {
    Row(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            painter = painterResource(id = R.drawable.vadlogo),
            contentDescription = "App Logo",
            modifier = Modifier.height(30.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text("Visual Alert Display", color = TextPrimary, fontSize = 16.sp)
    }
}

@Composable
private fun ModernTabNavigation(
    activeTab: String,
    isBasic: Boolean,
    onTabChange: (String) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        // Comms first
        ModernIconTabDrawable("ip",        activeTab, R.drawable.ic_ip,       "Comms",     { onTabChange("ip") })
        ModernIconTabDrawable("devices",   activeTab, R.drawable.ic_devices,  "Devices",   { onTabChange("devices") })
        ModernIconTabDrawable("floorplan", activeTab, R.drawable.ic_floorplan,"Floorplan", { onTabChange("floorplan") })
        ModernIconTabDrawable("password",  activeTab, R.drawable.ic_password, "Password",  { onTabChange("password") })
        if (!isBasic) {
            ModernIconTabDrawable("sms",   activeTab, R.drawable.ic_sms,      "SMS",       { onTabChange("sms") })
        }
        ModernIconTabDrawable("license",   activeTab, R.drawable.ic_license,  "Licence",   { onTabChange("license") })
        ModernIconTabDrawable("about",     activeTab, R.drawable.ic_about,    "About",     { onTabChange("about") })
    }
}

@Composable
fun ModernIconTabDrawable(
    tabId: String,
    activeTab: String,
    iconRes: Int,
    label: String,
    onClick: () -> Unit
) {
    val isActive = activeTab == tabId
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(62.dp)
    ) {
        IconButton(
            onClick = onClick,
            modifier = Modifier
                .size(56.dp)
                .background(
                    color = if (isActive) ActiveTabColor else TabIconBackground,
                    shape = CircleShape
                )
        ) {
            Icon(
                painter = painterResource(id = iconRes),
                contentDescription = label,
                tint = Color.White,
                modifier = Modifier.size(24.dp)
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = label,
            color = if (isActive) ActiveTabColor else TextSecondary,
            fontSize = 10.sp,
            fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
            maxLines = 1
        )
    }
}

@Composable
fun ModernIconTab(
    tabId: String, activeTab: String, icon: ImageVector,
    label: String, onClick: () -> Unit
) {
    val isActive = activeTab == tabId
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(62.dp)
    ) {
        IconButton(
            onClick = onClick,
            modifier = Modifier
                .size(56.dp)
                .background(
                    color = if (isActive) ActiveTabColor else TabIconBackground,
                    shape = CircleShape
                )
        ) {
            Icon(imageVector = icon, contentDescription = label, tint = Color.White, modifier = Modifier.size(24.dp))
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = label,
            color = if (isActive) ActiveTabColor else TextSecondary,
            fontSize = 10.sp,
            fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
            maxLines = 1
        )
    }
}

@Composable
private fun ModernCloseButton(onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().padding(16.dp).height(52.dp),
        colors = ButtonDefaults.buttonColors(backgroundColor = Color.White, contentColor = CloseButtonColor),
        shape = RoundedCornerShape(26.dp),
        elevation = ButtonDefaults.elevation(defaultElevation = 0.dp, pressedElevation = 2.dp)
    ) {
        Text("Close", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Devices tab
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun DeviceSettingsContent(viewModel: DeviceViewModel, isBasic: Boolean) {
    // Observe all device names — recomposes immediately when Inception syncs names
    val deviceNames by remember { derivedStateOf { viewModel.deviceStates.map { it.name.value } } }

    Column(modifier = Modifier.fillMaxSize()) {
        Text("Device Settings", fontSize = 20.sp, color = TextPrimary, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(16.dp))

        LazyColumn(
            modifier = Modifier.fillMaxWidth().weight(1f),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items(viewModel.deviceStates) { device ->
                var advancedExpanded by remember { mutableStateOf(false) }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(InputFieldBackground, RoundedCornerShape(12.dp))
                        .padding(16.dp)
                ) {
                    Text(
                        "Device ${viewModel.deviceStates.indexOf(device) + 1}",
                        color = TextSecondary, fontSize = 14.sp, fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = device.name.value,
                        onValueChange = { device.name.value = it; viewModel.saveDeviceStates() },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(8.dp),
                        colors = TextFieldDefaults.outlinedTextFieldColors(
                            backgroundColor = TabIconBackground, textColor = Color.White,
                            cursorColor = Color.White, focusedBorderColor = AccentBlue, unfocusedBorderColor = Color.Transparent
                        ),
                        leadingIcon = {
                            Icon(painter = painterResource(id = R.drawable.ic_device), contentDescription = null,
                                tint = Color.White.copy(alpha = 0.6f), modifier = Modifier.size(20.dp))
                        }
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Enabled", color = TextPrimary)
                        Switch(
                            checked = device.isEnabled.value,
                            onCheckedChange = { device.isEnabled.value = it; viewModel.saveDeviceStates() },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = DarkBlue, checkedTrackColor = Color.White,
                                uncheckedThumbColor = Color.White, uncheckedTrackColor = Color.Black
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Label Text Colour", color = TextPrimary)
                            Text("Use black text on light floor plans", fontSize = 11.sp, color = TextSecondary.copy(alpha = 0.7f))
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Box(
                                modifier = Modifier.size(36.dp)
                                    .background(if (device.labelColor.value == "white") AccentOrange else TabIconBackground, RoundedCornerShape(8.dp))
                                    .clickable { device.labelColor.value = "white"; viewModel.saveDeviceStates() },
                                contentAlignment = Alignment.Center
                            ) {
                                Box(modifier = Modifier.size(20.dp).background(Color.White, RoundedCornerShape(4.dp)))
                            }
                            Box(
                                modifier = Modifier.size(36.dp)
                                    .background(if (device.labelColor.value == "black") AccentOrange else TabIconBackground, RoundedCornerShape(8.dp))
                                    .clickable { device.labelColor.value = "black"; viewModel.saveDeviceStates() },
                                contentAlignment = Alignment.Center
                            ) {
                                Box(modifier = Modifier.size(20.dp).background(Color.Black, RoundedCornerShape(4.dp))
                                    .border(1.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(4.dp)))
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text("Size: ${device.size.value.toInt()}%", color = TextPrimary)
                        Slider(
                            value = device.size.value,
                            onValueChange = { device.size.value = it; viewModel.saveDeviceStates() },
                            valueRange = 30f..250f,
                            modifier = Modifier.fillMaxWidth(),
                            colors = SliderDefaults.colors(thumbColor = DarkBlue, activeTrackColor = DarkBlue, inactiveTrackColor = Color.Black)
                        )
                    }

                    if (advancedExpanded) {
                        Spacer(modifier = Modifier.height(12.dp))

                        // Alarm State Only toggle — Inception only
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Alarm State Only", color = TextPrimary)
                                Text(
                                    "Only trigger when Inception area is armed and input activates. " +
                                            "Prevents false triggers from motion sensors during business hours.",
                                    fontSize = 11.sp,
                                    color = TextSecondary.copy(alpha = 0.7f),
                                    lineHeight = 15.sp
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Switch(
                                checked = device.alarmStateOnly.value,
                                onCheckedChange = { device.alarmStateOnly.value = it; viewModel.saveDeviceStates() },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = DarkBlue, checkedTrackColor = Color.White,
                                    uncheckedThumbColor = Color.White, uncheckedTrackColor = Color.Black
                                )
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        Text("Camera Stream", color = TextSecondary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                        Spacer(modifier = Modifier.height(6.dp))
                        CameraUrlBuilder(
                            currentUrl = device.streamUrl.value,
                            onUrlChanged = { device.streamUrl.value = it; viewModel.saveDeviceStates() }
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Enable Camera Link", color = TextPrimary)
                            Switch(
                                checked = device.cameraEnabled.value,
                                onCheckedChange = if (isBasic) null else { { device.cameraEnabled.value = it; viewModel.saveDeviceStates() } },
                                enabled = !isBasic,
                                colors = SwitchDefaults.colors(checkedThumbColor = DarkBlue, checkedTrackColor = Color.White, uncheckedThumbColor = Color.White, uncheckedTrackColor = Color.Black)
                            )
                        }
                        // Show camera icon on floor plan — only visible when camera is enabled
                        if (device.cameraEnabled.value && !isBasic) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Show Camera Icon on Floor Plan", color = TextPrimary)
                                    Text(
                                        "Tap the camera icon on the floor plan to view live stream at any time",
                                        fontSize = 11.sp,
                                        color = TextSecondary.copy(alpha = 0.7f),
                                        lineHeight = 15.sp
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Switch(
                                    checked = device.showCameraIcon.value,
                                    onCheckedChange = { device.showCameraIcon.value = it; viewModel.saveDeviceStates() },
                                    colors = SwitchDefaults.colors(checkedThumbColor = DarkBlue, checkedTrackColor = Color.White, uncheckedThumbColor = Color.White, uncheckedTrackColor = Color.Black)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Enable SMS Alerts", color = TextPrimary)
                            Switch(
                                checked = device.smsEnabled.value,
                                onCheckedChange = if (isBasic) null else { { device.smsEnabled.value = it; viewModel.saveDeviceStates() } },
                                enabled = !isBasic,
                                colors = SwitchDefaults.colors(checkedThumbColor = DarkBlue, checkedTrackColor = Color.White, uncheckedThumbColor = Color.White, uncheckedTrackColor = Color.Black)
                            )
                        }
                    }

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = { advancedExpanded = !advancedExpanded }) {
                            Text(
                                if (advancedExpanded) "Hide Advanced" else "Show Advanced",
                                color = Color.White, fontSize = 14.sp,
                                textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline
                            )
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(8.dp))
                Button(onClick = { viewModel.resetDevicePositions() }, modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(backgroundColor = TabIconBackground, contentColor = Color.White),
                    shape = RoundedCornerShape(8.dp)
                ) { Text("Reset Device Positions") }
                Spacer(modifier = Modifier.height(8.dp))
                Button(onClick = { viewModel.toggleAllDevices() }, modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(backgroundColor = TabIconBackground, contentColor = Color.White),
                    shape = RoundedCornerShape(8.dp)
                ) { Text("Toggle All Devices") }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Comms tab
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun IpContent(viewModel: DeviceViewModel, modbusIp: String, context: Context) {
    val scope = rememberCoroutineScope()
    val selectedSource by viewModel.inputSourceType
    val moxa2Ip by viewModel.moxa2Ip

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        item {
            Text("Input Source", fontSize = 24.sp, color = TextPrimary, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(4.dp))
            Text("Choose which hardware integration supplies alarm inputs to VAD.",
                fontSize = 13.sp, color = TextSecondary.copy(alpha = 0.8f))
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                InputSourceType.values().forEach { type ->
                    SourceSelectorCard(type = type, isSelected = selectedSource == type, onClick = { viewModel.setInputSourceType(type) })
                }
            }
        }

        item { Divider(color = Color.White.copy(alpha = 0.08f), thickness = 1.dp) }

        when (selectedSource) {
            InputSourceType.MOXA_REST   -> { item { MoxaRestConfig(viewModel, modbusIp, context, isUnit2 = false) } }
            InputSourceType.MOXA_REST_2 -> { item { MoxaRestConfig(viewModel, modbusIp, context, isUnit2 = true) } }
            InputSourceType.MODBUS_TCP  -> { item { ModbusTcpConfig(viewModel, modbusIp, context) } }
            InputSourceType.INCEPTION   -> {
                item { InceptionConnectionConfig(viewModel, context) }
                item { InceptionInputMappingConfig(viewModel, scope) }
            }
            InputSourceType.WMS_PRO -> {
                item { WmsProConnectionConfig(viewModel, context) }
                item { WmsProDeviceMappingConfig(viewModel, scope) }
            }
        }

        item {
            Spacer(modifier = Modifier.height(8.dp))
            var saved by remember { mutableStateOf(false) }
            Button(
                onClick = {
                    if (selectedSource == InputSourceType.WMS_PRO) {
                        viewModel.saveWmsProConfig(context)
                        viewModel.syncDevicesFromWmsMappingsNow()
                    } else {
                        viewModel.saveInceptionConfig(context)
                        viewModel.syncDevicesFromMappingsNow()
                    }
                    viewModel.restartPolling()
                    saved = true
                    Toast.makeText(context, "Settings saved, reconnecting…", Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                colors = ButtonDefaults.buttonColors(backgroundColor = ActiveTabColor, contentColor = Color.White),
                shape = RoundedCornerShape(28.dp)
            ) {
                Text(if (saved) "Saved & Reconnecting" else "Save & Apply", fontSize = 16.sp, fontWeight = FontWeight.Medium)
            }
            LaunchedEffect(saved) {
                if (saved) { kotlinx.coroutines.delay(2500); saved = false }
            }
        }
    }
}

@Composable
private fun SourceSelectorCard(type: InputSourceType, isSelected: Boolean, onClick: () -> Unit) {
    val (subtitle, icon) = when (type) {
        InputSourceType.MOXA_REST   -> "REST polling · slots 1–16"  to Icons.Filled.Router
        InputSourceType.MOXA_REST_2 -> "REST polling · slots 17–32" to Icons.Filled.Router
        InputSourceType.MODBUS_TCP  -> "Modbus TCP · port 502"       to Icons.Filled.Cable
        InputSourceType.INCEPTION   -> "Inner Range Inception REST"  to Icons.Filled.Security
        InputSourceType.WMS_PRO     -> "Tecom WMS Pro REST API v2"   to Icons.Filled.Security
    }
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        color = if (isSelected) AccentOrange.copy(alpha = 0.12f) else InputFieldBackground,
        shape = RoundedCornerShape(12.dp),
        border = if (isSelected)
            androidx.compose.foundation.BorderStroke(1.5.dp, AccentOrange)
        else
            androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.06f))
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Box(
                    modifier = Modifier.size(40.dp).background(
                        if (isSelected) AccentOrange.copy(alpha = 0.2f) else TabIconBackground, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, contentDescription = null, tint = if (isSelected) AccentOrange else TextSecondary, modifier = Modifier.size(20.dp))
                }
                Column {
                    Text(type.displayName, fontSize = 15.sp, color = if (isSelected) AccentOrange else TextPrimary,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal)
                    Text(subtitle, fontSize = 11.sp, color = TextSecondary.copy(alpha = 0.7f))
                }
            }
            if (isSelected) {
                Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = AccentOrange, modifier = Modifier.size(22.dp))
            }
        }
    }
}

@Composable
private fun MoxaRestConfig(viewModel: DeviceViewModel, modbusIp: String, context: Context, isUnit2: Boolean = false) {
    val moxa2Ip by viewModel.moxa2Ip
    var ipInput by remember(if (isUnit2) moxa2Ip else modbusIp) {
        mutableStateOf(if (isUnit2) moxa2Ip else modbusIp)
    }
    val slotRange = if (isUnit2) "17–32" else "1–16"
    IpSectionHeader("Moxa ioLogik Settings (Unit ${if (isUnit2) 2 else 1})",
        "Polls GET /api/slot/0/io/di every 3 s. Maps to device slots $slotRange.")
    IpTextField("Device IP Address", ipInput, { ipInput = it }, if (isUnit2) "192.168.0.251" else "192.168.0.250")
    Spacer(modifier = Modifier.height(12.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
        Button(
            onClick = { Toast.makeText(context, "Testing Moxa Unit ${if (isUnit2) 2 else 1}…", Toast.LENGTH_SHORT).show() },
            modifier = Modifier.weight(1f).height(48.dp),
            colors = ButtonDefaults.buttonColors(backgroundColor = TabIconBackground, contentColor = Color.White),
            shape = RoundedCornerShape(24.dp)
        ) {
            Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("Test", fontSize = 14.sp)
        }
        Button(
            onClick = {
                if (ipInput.trim().isNotEmpty()) {
                    if (isUnit2) viewModel.setMoxa2Ip(ipInput.trim()) else viewModel.setModbusIp(ipInput.trim())
                    Toast.makeText(context, "IP saved", Toast.LENGTH_SHORT).show()
                } else Toast.makeText(context, "Enter a valid IP", Toast.LENGTH_SHORT).show()
            },
            modifier = Modifier.weight(1f).height(48.dp),
            colors = ButtonDefaults.buttonColors(backgroundColor = ActiveTabColor, contentColor = Color.White),
            shape = RoundedCornerShape(24.dp)
        ) { Text("Save IP", fontSize = 14.sp, fontWeight = FontWeight.Medium) }
    }
}

@Composable
private fun ModbusTcpConfig(viewModel: DeviceViewModel, modbusIp: String, context: Context) {
    var ipInput by remember(modbusIp) { mutableStateOf(modbusIp) }
    IpSectionHeader("Modbus TCP Settings", "Reads 16 discrete inputs via Function Code 0x02. Port 502.")
    IpTextField("Device IP Address", ipInput, { ipInput = it }, "192.168.0.250")
    Spacer(modifier = Modifier.height(12.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
        Button(
            onClick = { Toast.makeText(context, "Testing Modbus connection…", Toast.LENGTH_SHORT).show() },
            modifier = Modifier.weight(1f).height(48.dp),
            colors = ButtonDefaults.buttonColors(backgroundColor = TabIconBackground, contentColor = Color.White),
            shape = RoundedCornerShape(24.dp)
        ) {
            Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("Test", fontSize = 14.sp)
        }
        Button(
            onClick = {
                if (ipInput.trim().isNotEmpty()) { viewModel.setModbusIp(ipInput.trim()); Toast.makeText(context, "IP saved", Toast.LENGTH_SHORT).show() }
                else Toast.makeText(context, "Enter a valid IP", Toast.LENGTH_SHORT).show()
            },
            modifier = Modifier.weight(1f).height(48.dp),
            colors = ButtonDefaults.buttonColors(backgroundColor = ActiveTabColor, contentColor = Color.White),
            shape = RoundedCornerShape(24.dp)
        ) { Text("Save IP", fontSize = 14.sp, fontWeight = FontWeight.Medium) }
    }
}

@Composable
private fun InceptionConnectionConfig(viewModel: DeviceViewModel, context: Context) {
    val config = viewModel.inceptionConfig
    var showPassword by remember { mutableStateOf(false) }
    IpSectionHeader("Inception Controller",
        "Enter the IP/hostname of your Inception controller and the credentials of a dedicated API User.")
    IpTextField("Host / IP Address", config.host.value, { viewModel.updateInceptionHost(it) }, "192.168.0.100")
    Spacer(modifier = Modifier.height(10.dp))
    IpTextField("API Username", config.username.value, { viewModel.updateInceptionUsername(it) }, "api_user")
    Spacer(modifier = Modifier.height(10.dp))
    OutlinedTextField(
        value = config.password.value,
        onValueChange = { viewModel.updateInceptionPassword(it) },
        label = { Text("API Password", fontSize = 12.sp, color = TextSecondary) },
        placeholder = { Text("••••••••", color = TextSecondary.copy(alpha = 0.5f)) },
        singleLine = true,
        visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = TextFieldDefaults.outlinedTextFieldColors(
            backgroundColor = InputFieldBackground, textColor = Color.White,
            cursorColor = Color.White, focusedBorderColor = AccentOrange, unfocusedBorderColor = Color.Transparent
        ),
        leadingIcon = { Icon(painter = painterResource(id = R.drawable.ic_password), contentDescription = null, tint = Color.White.copy(alpha = 0.6f), modifier = Modifier.size(20.dp)) },
        trailingIcon = {
            IconButton(onClick = { showPassword = !showPassword }) {
                Icon(if (showPassword) Icons.Filled.Visibility else Icons.Filled.VisibilityOff, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(20.dp))
            }
        }
    )
    Spacer(modifier = Modifier.height(12.dp))
    val isConnected by viewModel.isConnected
    val statusText by viewModel.connectionStatusText
    Surface(color = if (isConnected) Color(0xFF0D2B1A) else Color(0xFF2B0D0D), shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(modifier = Modifier.size(10.dp).background(if (isConnected) Color(0xFF4CAF50) else Color(0xFFE53935), CircleShape))
            Text(if (isConnected) "Connected · $statusText" else "Disconnected · $statusText",
                fontSize = 13.sp, color = if (isConnected) Color(0xFF81C784) else Color(0xFFEF9A9A))
        }
    }
}

@Composable
private fun InceptionInputMappingConfig(viewModel: DeviceViewModel, scope: kotlinx.coroutines.CoroutineScope) {
    var availableInputs by remember { mutableStateOf<List<InceptionInputInfo>?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    val currentMappings by remember { derivedStateOf { viewModel.inceptionConfig.inputMappings.value } }
    IpSectionHeader("Input → Device Slot Mapping",
        "Manually assign each Inception input to a VAD device slot (1–32). All inputs must be mapped — there is no auto-assign for Inception.")
    Button(
        onClick = {
            scope.launch {
                isLoading = true; errorMsg = null
                val result = viewModel.fetchInceptionInputs()
                if (result != null) availableInputs = result
                else errorMsg = "Could not connect. Check host and credentials above."
                isLoading = false
            }
        },
        modifier = Modifier.fillMaxWidth().height(48.dp),
        colors = ButtonDefaults.buttonColors(backgroundColor = TabIconBackground, contentColor = Color.White),
        shape = RoundedCornerShape(24.dp), enabled = !isLoading
    ) {
        if (isLoading) {
            CircularProgressIndicator(modifier = Modifier.size(18.dp), color = AccentOrange, strokeWidth = 2.dp)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Connecting to Inception…", fontSize = 14.sp)
        } else {
            Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Load Inputs from Controller", fontSize = 14.sp)
        }
    }
    errorMsg?.let {
        Spacer(modifier = Modifier.height(8.dp))
        Surface(color = Color(0xFF3B1515), shape = RoundedCornerShape(8.dp)) {
            Text(it, fontSize = 12.sp, color = Color(0xFFEF9A9A), modifier = Modifier.padding(10.dp))
        }
    }
    if (availableInputs != null) {
        Spacer(modifier = Modifier.height(12.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("${availableInputs!!.size} inputs found", fontSize = 13.sp, color = TextSecondary)
            TextButton(onClick = { viewModel.clearInceptionInputMappings() }, colors = ButtonDefaults.textButtonColors(contentColor = AccentOrange)) {
                Text("Clear all", fontSize = 12.sp)
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        availableInputs!!.forEach { inputInfo ->
            InceptionMappingRow(inputInfo = inputInfo, currentSlot = currentMappings[inputInfo.id],
                onSlotChanged = { slot -> viewModel.updateInceptionInputMapping(inputInfo.id, slot) })
            Spacer(modifier = Modifier.height(6.dp))
        }
    }
}

@Composable
private fun InceptionMappingRow(inputInfo: InceptionInputInfo, currentSlot: Int?, onSlotChanged: (Int?) -> Unit) {
    var slotText by remember(currentSlot) { mutableStateOf(currentSlot?.toString() ?: "") }
    Surface(color = InputFieldBackground, shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Column(modifier = Modifier.weight(1f)) {
                Text(inputInfo.name, fontSize = 13.sp, color = TextPrimary, fontWeight = FontWeight.Medium)
                Text("Reporting ID: ${inputInfo.reportingId}", fontSize = 10.sp, color = TextSecondary.copy(alpha = 0.6f))
            }
            Spacer(modifier = Modifier.width(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Slot", fontSize = 12.sp, color = TextSecondary)
                Spacer(modifier = Modifier.width(8.dp))
                OutlinedTextField(
                    value = slotText,
                    onValueChange = { v ->
                        slotText = v.filter { it.isDigit() }.take(2)
                        val parsed = slotText.toIntOrNull()
                        onSlotChanged(if (parsed != null && parsed in 1..32) parsed else null)
                    },
                    modifier = Modifier.width(64.dp), singleLine = true,
                    placeholder = { Text("1-32", fontSize = 11.sp, color = TextSecondary.copy(alpha = 0.5f)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    colors = TextFieldDefaults.outlinedTextFieldColors(backgroundColor = TabIconBackground, textColor = Color.White,
                        cursorColor = AccentOrange, focusedBorderColor = AccentOrange, unfocusedBorderColor = Color.Transparent),
                    shape = RoundedCornerShape(8.dp)
                )
            }
        }
    }
}


@Composable
private fun WmsProConnectionConfig(viewModel: DeviceViewModel, context: Context) {
    val config = viewModel.wmsProConfig
    var showPassword by remember { mutableStateOf(false) }
    IpSectionHeader("WMS Pro Controller",
        "Enter the IP address of your WMS Pro server and the credentials of a WMS Pro External API Operator.")
    IpTextField("Host / IP Address", config.host.value, { viewModel.updateWmsProHost(it) }, "192.168.0.100")
    Spacer(modifier = Modifier.height(10.dp))
    IpTextField("Username", config.username.value, { viewModel.updateWmsProUsername(it) }, "api_user")
    Spacer(modifier = Modifier.height(10.dp))
    OutlinedTextField(
        value = config.password.value,
        onValueChange = { viewModel.updateWmsProPassword(it) },
        label = { Text("Password", fontSize = 12.sp, color = TextSecondary) },
        placeholder = { Text("••••••••", color = TextSecondary.copy(alpha = 0.5f)) },
        singleLine = true,
        visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = TextFieldDefaults.outlinedTextFieldColors(
            backgroundColor = InputFieldBackground, textColor = Color.White,
            cursorColor = Color.White, focusedBorderColor = AccentOrange, unfocusedBorderColor = Color.Transparent
        ),
        leadingIcon = { Icon(painter = painterResource(id = R.drawable.ic_password), contentDescription = null, tint = Color.White.copy(alpha = 0.6f), modifier = Modifier.size(20.dp)) },
        trailingIcon = {
            IconButton(onClick = { showPassword = !showPassword }) {
                Icon(if (showPassword) Icons.Filled.Visibility else Icons.Filled.VisibilityOff, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(20.dp))
            }
        }
    )
    Spacer(modifier = Modifier.height(10.dp))
    val isConnected by viewModel.isConnected
    val statusText by viewModel.connectionStatusText
    Surface(color = if (isConnected) Color(0xFF0D2B1A) else Color(0xFF2B0D0D), shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(modifier = Modifier.size(10.dp).background(if (isConnected) Color(0xFF4CAF50) else Color(0xFFE53935), CircleShape))
            Text(if (isConnected) "Connected · $statusText" else "Disconnected · $statusText",
                fontSize = 13.sp, color = if (isConnected) Color(0xFF81C784) else Color(0xFFEF9A9A))
        }
    }
}

@Composable
private fun WmsProDeviceMappingConfig(viewModel: DeviceViewModel, scope: kotlinx.coroutines.CoroutineScope) {
    var availableDevices by remember { mutableStateOf<List<com.example.visualduress.integration.WmsProDeviceInfo>?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    val currentMappings by remember { derivedStateOf { viewModel.wmsProConfig.deviceMappings.value } }

    IpSectionHeader("Device → Slot Mapping",
        "Manually assign each WMS Pro device to a VAD slot (1–32). All devices must be mapped — there is no auto-assign.")
    Button(
        onClick = {
            scope.launch {
                isLoading = true; errorMsg = null
                val result = viewModel.fetchWmsProDevices()
                if (result != null) availableDevices = result
                else errorMsg = "Could not connect. Check host and bearer token above."
                isLoading = false
            }
        },
        modifier = Modifier.fillMaxWidth().height(48.dp),
        colors = ButtonDefaults.buttonColors(backgroundColor = TabIconBackground, contentColor = Color.White),
        shape = RoundedCornerShape(24.dp), enabled = !isLoading
    ) {
        if (isLoading) {
            CircularProgressIndicator(modifier = Modifier.size(18.dp), color = AccentOrange, strokeWidth = 2.dp)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Connecting to WMS Pro…", fontSize = 14.sp)
        } else {
            Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Load Devices from Controller", fontSize = 14.sp)
        }
    }
    errorMsg?.let {
        Spacer(modifier = Modifier.height(8.dp))
        Surface(color = Color(0xFF3B1515), shape = RoundedCornerShape(8.dp)) {
            Text(it, fontSize = 12.sp, color = Color(0xFFEF9A9A), modifier = Modifier.padding(10.dp))
        }
    }
    if (availableDevices != null) {
        Spacer(modifier = Modifier.height(12.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("${availableDevices!!.size} devices found", fontSize = 13.sp, color = TextSecondary)
            TextButton(onClick = { viewModel.clearWmsProDeviceMappings() }, colors = ButtonDefaults.textButtonColors(contentColor = AccentOrange)) {
                Text("Clear all", fontSize = 12.sp)
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        availableDevices!!.forEach { deviceInfo ->
            WmsProMappingRow(
                deviceInfo = deviceInfo,
                currentSlot = currentMappings[deviceInfo.uid],
                onSlotChanged = { slot -> viewModel.updateWmsProDeviceMapping(deviceInfo.uid, slot) }
            )
            Spacer(modifier = Modifier.height(6.dp))
        }
    }
}

@Composable
private fun WmsProMappingRow(
    deviceInfo: com.example.visualduress.integration.WmsProDeviceInfo,
    currentSlot: Int?,
    onSlotChanged: (Int?) -> Unit
) {
    var slotText by remember(currentSlot) { mutableStateOf(currentSlot?.toString() ?: "") }
    Surface(color = InputFieldBackground, shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Column(modifier = Modifier.weight(1f)) {
                Text(deviceInfo.name, fontSize = 13.sp, color = TextPrimary, fontWeight = FontWeight.Medium)
                Text("UID: ${deviceInfo.uid}", fontSize = 10.sp, color = TextSecondary.copy(alpha = 0.6f))
            }
            Spacer(modifier = Modifier.width(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Slot", fontSize = 12.sp, color = TextSecondary)
                Spacer(modifier = Modifier.width(8.dp))
                OutlinedTextField(
                    value = slotText,
                    onValueChange = { v ->
                        slotText = v.filter { it.isDigit() }.take(2)
                        val parsed = slotText.toIntOrNull()
                        onSlotChanged(if (parsed != null && parsed in 1..32) parsed else null)
                    },
                    modifier = Modifier.width(64.dp), singleLine = true,
                    placeholder = { Text("1-32", fontSize = 11.sp, color = TextSecondary.copy(alpha = 0.5f)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    colors = TextFieldDefaults.outlinedTextFieldColors(backgroundColor = TabIconBackground, textColor = Color.White,
                        cursorColor = AccentOrange, focusedBorderColor = AccentOrange, unfocusedBorderColor = Color.Transparent),
                    shape = RoundedCornerShape(8.dp)
                )
            }
        }
    }
}

@Composable
private fun IpSectionHeader(title: String, hint: String) {
    Text(title, fontSize = 17.sp, color = TextPrimary, fontWeight = FontWeight.SemiBold)
    Spacer(modifier = Modifier.height(4.dp))
    Text(hint, fontSize = 12.sp, color = TextSecondary.copy(alpha = 0.75f), lineHeight = 17.sp)
    Spacer(modifier = Modifier.height(12.dp))
}

@Composable
private fun IpTextField(label: String, value: String, onValueChange: (String) -> Unit, placeholder: String) {
    OutlinedTextField(
        value = value, onValueChange = onValueChange,
        label = { Text(label, fontSize = 12.sp, color = TextSecondary) },
        placeholder = { Text(placeholder, color = TextSecondary.copy(alpha = 0.5f), fontSize = 14.sp) },
        singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
        colors = TextFieldDefaults.outlinedTextFieldColors(
            backgroundColor = InputFieldBackground, textColor = Color.White,
            cursorColor = Color.White, focusedBorderColor = AccentOrange, unfocusedBorderColor = Color.Transparent
        ),
        leadingIcon = { Icon(painter = painterResource(id = R.drawable.ic_device), contentDescription = null, tint = Color.White.copy(alpha = 0.6f), modifier = Modifier.size(20.dp)) }
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// Floorplan tab
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun FloorplanContent(viewModel: DeviceViewModel, launcher: ActivityResultLauncher<Array<String>>, floorplanUri: Uri?) {
    Column(modifier = Modifier.fillMaxSize()) {
        Text("Floorplan Images", fontSize = 24.sp, color = TextPrimary, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(20.dp))
        Button(onClick = { launcher.launch(arrayOf("image/*")) }, modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(backgroundColor = ActiveTabColor, contentColor = Color.White),
            shape = RoundedCornerShape(12.dp), contentPadding = PaddingValues(horizontal = 24.dp, vertical = 14.dp)
        ) {
            Text("Select Floorplan Image", fontSize = 16.sp, fontWeight = FontWeight.Medium)
        }
        Spacer(modifier = Modifier.height(24.dp))
        floorplanUri?.let { uri ->
            AsyncImage(model = uri, contentDescription = "Selected Floorplan",
                modifier = Modifier.fillMaxWidth().height(400.dp).clip(RoundedCornerShape(16.dp)).background(Color.White))
        }
        Spacer(modifier = Modifier.weight(1f))
        if (floorplanUri != null) {
            Button(onClick = { viewModel.setFloorplanUri(Uri.EMPTY) },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                colors = ButtonDefaults.buttonColors(backgroundColor = ActiveTabColor, contentColor = Color.White),
                shape = RoundedCornerShape(28.dp)
            ) { Text("Remove Floorplan", fontSize = 16.sp, fontWeight = FontWeight.Medium) }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Password tab
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun PasswordContent(viewModel: DeviceViewModel, password: String, context: Context, onPasswordChange: (String) -> Unit) {
    var confirmPassword by remember { mutableStateOf("") }
    Column(modifier = Modifier.fillMaxSize()) {
        Text("Change Password", fontSize = 24.sp, color = TextPrimary, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(24.dp))
        Text("New Password", fontSize = 18.sp, color = TextPrimary, fontWeight = FontWeight.Medium)
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(value = password, onValueChange = onPasswordChange,
            placeholder = { Text("********************", color = TextSecondary.copy(alpha = 0.6f), fontSize = 16.sp) },
            singleLine = true, visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp),
            colors = TextFieldDefaults.outlinedTextFieldColors(backgroundColor = InputFieldBackground, textColor = Color.White,
                cursorColor = Color.White, focusedBorderColor = Color.Transparent, unfocusedBorderColor = Color.Transparent),
            leadingIcon = { Icon(painter = painterResource(id = R.drawable.ic_password), contentDescription = null, tint = Color.White.copy(alpha = 0.6f), modifier = Modifier.size(20.dp)) }
        )
        Spacer(modifier = Modifier.height(24.dp))
        Text("Confirm Password", fontSize = 18.sp, color = TextPrimary, fontWeight = FontWeight.Medium)
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(value = confirmPassword, onValueChange = { confirmPassword = it },
            placeholder = { Text("********************", color = TextSecondary.copy(alpha = 0.6f), fontSize = 16.sp) },
            singleLine = true, visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp),
            colors = TextFieldDefaults.outlinedTextFieldColors(backgroundColor = InputFieldBackground, textColor = Color.White,
                cursorColor = Color.White, focusedBorderColor = Color.Transparent, unfocusedBorderColor = Color.Transparent),
            leadingIcon = { Icon(painter = painterResource(id = R.drawable.ic_password), contentDescription = null, tint = Color.White.copy(alpha = 0.6f), modifier = Modifier.size(20.dp)) }
        )
        Spacer(modifier = Modifier.weight(1f))
        Button(onClick = {
            if (password.isNotEmpty() && password == confirmPassword) {
                viewModel.changePassword(password); onPasswordChange(""); confirmPassword = ""
                Toast.makeText(context, "Password changed", Toast.LENGTH_SHORT).show()
            } else if (password != confirmPassword) Toast.makeText(context, "Passwords don't match", Toast.LENGTH_SHORT).show()
            else Toast.makeText(context, "Enter a password", Toast.LENGTH_SHORT).show()
        }, modifier = Modifier.fillMaxWidth().height(56.dp),
            colors = ButtonDefaults.buttonColors(backgroundColor = ActiveTabColor, contentColor = Color.White),
            shape = RoundedCornerShape(28.dp)
        ) { Text("Save", fontSize = 16.sp, fontWeight = FontWeight.Medium) }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// License tab
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun LicenseContent(viewModel: DeviceViewModel, context: Context, licenseKey: String, onLicenseKeyChange: (String) -> Unit, deviceId: String, licenseType: String) {
    Column(modifier = Modifier.fillMaxSize()) {
        Text("Device ID", fontSize = 18.sp, color = TextPrimary, fontWeight = FontWeight.Medium)
        Spacer(modifier = Modifier.height(8.dp))
        SelectionContainer {
            Box(modifier = Modifier.fillMaxWidth().background(InputFieldBackground, RoundedCornerShape(12.dp)).padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(painter = painterResource(id = R.drawable.ic_user), contentDescription = null, tint = Color.White.copy(alpha = 0.6f), modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(deviceId, color = TextPrimary, fontSize = 16.sp)
                }
            }
        }
        Spacer(modifier = Modifier.height(24.dp))
        Text("Stored Licence Key", fontSize = 18.sp, color = TextPrimary, fontWeight = FontWeight.Medium)
        Spacer(modifier = Modifier.height(8.dp))
        Box(modifier = Modifier.fillMaxWidth().background(InputFieldBackground, RoundedCornerShape(12.dp)).padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(painter = painterResource(id = R.drawable.ic_key), contentDescription = null, tint = Color.White.copy(alpha = 0.6f), modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(12.dp))
                Text(LicenseManager.getLicenseType(context).takeIf { it.isNotEmpty() } ?: "No license stored", color = TextPrimary, fontSize = 16.sp)
            }
        }
        Spacer(modifier = Modifier.height(24.dp))

        val expiryDate = remember { LicenseManager.getExpiryDateString(context) }
        val daysLeft = remember { LicenseManager.getDaysUntilExpiry(context) }
        val currentLicenseType = remember { LicenseManager.getLicenseType(context) }

        if (currentLicenseType != "NONE") {
            Text("Licence Status", fontSize = 18.sp, color = TextPrimary, fontWeight = FontWeight.Medium)
            Spacer(modifier = Modifier.height(8.dp))
            Box(
                modifier = Modifier.fillMaxWidth().background(
                    when {
                        currentLicenseType == "EXPIRED" -> Color(0xFF3B1515)
                        daysLeft != null && daysLeft <= 30 -> Color(0xFF2B2010)
                        else -> Color(0xFF0D2B1A)
                    }, RoundedCornerShape(12.dp)
                ).padding(16.dp)
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Box(modifier = Modifier.size(10.dp).background(
                            when {
                                currentLicenseType == "EXPIRED" -> Color(0xFFE53935)
                                daysLeft != null && daysLeft <= 30 -> Color(0xFFF59E0B)
                                else -> Color(0xFF4CAF50)
                            }, CircleShape))
                        Text(
                            when (currentLicenseType) {
                                "EXPIRED" -> "Licence expired — please renew"
                                "PREMIUM" -> "Premium licence — active"
                                "BASIC"   -> "Basic licence — active"
                                else      -> "Licence active"
                            },
                            fontSize = 14.sp, fontWeight = FontWeight.Medium,
                            color = when {
                                currentLicenseType == "EXPIRED" -> Color(0xFFEF9A9A)
                                daysLeft != null && daysLeft <= 30 -> Color(0xFFFCD34D)
                                else -> Color(0xFF81C784)
                            }
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    if (expiryDate != null) {
                        Text(
                            when {
                                currentLicenseType == "EXPIRED" -> "Expired on $expiryDate"
                                daysLeft != null && daysLeft <= 30 -> "Expires $expiryDate · $daysLeft days remaining"
                                daysLeft != null && daysLeft == 0 && LicenseManager.getDaysUntilLocked(context) != null ->
                                    "Grace period — ${LicenseManager.getDaysUntilLocked(context)} days until locked"
                                else -> "Expires $expiryDate"
                            },
                            fontSize = 12.sp, color = TextSecondary.copy(alpha = 0.8f)
                        )
                    } else {
                        Text("No expiry — legacy licence", fontSize = 12.sp, color = TextSecondary.copy(alpha = 0.8f))
                    }
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
        }

        Text("Enter New Licence Key", fontSize = 18.sp, color = TextPrimary, fontWeight = FontWeight.Medium)
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(value = licenseKey, onValueChange = onLicenseKeyChange,
            placeholder = { Text("********************", color = TextSecondary.copy(alpha = 0.6f), fontSize = 16.sp) },
            singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp),
            colors = TextFieldDefaults.outlinedTextFieldColors(backgroundColor = InputFieldBackground, textColor = Color.White,
                cursorColor = Color.White, focusedBorderColor = Color.Transparent, unfocusedBorderColor = Color.Transparent),
            leadingIcon = { Icon(painter = painterResource(id = R.drawable.ic_key), contentDescription = null, tint = Color.White.copy(alpha = 0.6f), modifier = Modifier.size(20.dp)) }
        )
        Spacer(modifier = Modifier.weight(1f))
        Button(onClick = {
            val trimmedKey = licenseKey.trim()
            if (trimmedKey.isEmpty()) { Toast.makeText(context, "Please enter a license key", Toast.LENGTH_SHORT).show(); return@Button }
            if (LicenseManager.validateLicense(context, trimmedKey)) {
                LicenseManager.saveLicense(context, trimmedKey)
                viewModel.forceRefreshLicense(context) { Toast.makeText(context, "License activated: ${LicenseManager.getLicenseType(context)}", Toast.LENGTH_LONG).show() }
            } else Toast.makeText(context, "Invalid license key", Toast.LENGTH_LONG).show()
        }, modifier = Modifier.fillMaxWidth().height(56.dp),
            colors = ButtonDefaults.buttonColors(backgroundColor = ActiveTabColor, contentColor = Color.White),
            shape = RoundedCornerShape(28.dp)
        ) { Text("Activate", fontSize = 16.sp, fontWeight = FontWeight.Medium) }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// About tab
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun AboutContent(appVersion: String, companyName: String, websiteUrl: String) {
    Column(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.fillMaxWidth().background(InputFieldBackground, RoundedCornerShape(16.dp)).padding(32.dp)) {
            Column {
                Text("About This App", fontSize = 28.sp, color = TextPrimary, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(24.dp))
                Text("App Version: $appVersion", fontSize = 18.sp, color = TextPrimary)
                Spacer(modifier = Modifier.height(16.dp))
                Text("Company: $companyName", fontSize = 18.sp, color = TextPrimary)
                Spacer(modifier = Modifier.height(16.dp))
                Text("Website: $websiteUrl", fontSize = 18.sp, color = TextPrimary)
            }
        }
        Spacer(modifier = Modifier.weight(1f))
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// SMS tab
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun SmsSettings(viewModel: DeviceViewModel) {
    val context = LocalContext.current
    Column(modifier = Modifier.fillMaxSize()) {
        Text("SMS Settings", fontSize = 24.sp, color = TextPrimary, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(20.dp))
        LazyColumn(modifier = Modifier.fillMaxWidth().weight(1f), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            items(5) { index ->
                val entry = viewModel.smsConfig.smsNumbers[index]
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Name", color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                        Spacer(modifier = Modifier.height(6.dp))
                        OutlinedTextField(value = entry.label.value, onValueChange = { viewModel.updateSmsNumberLabel(index, it) },
                            placeholder = { Text("Enter name", color = TextSecondary.copy(alpha = 0.6f), fontSize = 14.sp) },
                            modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp),
                            colors = TextFieldDefaults.outlinedTextFieldColors(backgroundColor = InputFieldBackground, textColor = Color.White, cursorColor = Color.White, focusedBorderColor = Color.Transparent, unfocusedBorderColor = Color.Transparent),
                            leadingIcon = { Icon(painter = painterResource(id = R.drawable.ic_user), contentDescription = null, tint = Color.White.copy(alpha = 0.6f), modifier = Modifier.size(20.dp)) })
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Phone Number", color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                        Spacer(modifier = Modifier.height(6.dp))
                        OutlinedTextField(value = entry.number.value, onValueChange = { viewModel.updateSmsNumber(index, it) },
                            placeholder = { Text("Enter phone number", color = TextSecondary.copy(alpha = 0.6f), fontSize = 14.sp) },
                            modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp),
                            colors = TextFieldDefaults.outlinedTextFieldColors(backgroundColor = InputFieldBackground, textColor = Color.White, cursorColor = Color.White, focusedBorderColor = Color.Transparent, unfocusedBorderColor = Color.Transparent),
                            leadingIcon = { Icon(painter = painterResource(id = R.drawable.ic_call), contentDescription = null, tint = Color.White.copy(alpha = 0.6f), modifier = Modifier.size(20.dp)) })
                    }
                }
            }
            item {
                Spacer(modifier = Modifier.height(8.dp))
                Text("Gateway Settings", color = TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
            }
            item {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text("SMS Gateway URL", color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    Spacer(modifier = Modifier.height(6.dp))
                    OutlinedTextField(value = viewModel.smsConfig.gatewayUrl.value, onValueChange = viewModel::updateSmsGatewayUrl,
                        placeholder = { Text("https://www.demo.com", color = TextSecondary.copy(alpha = 0.6f), fontSize = 14.sp) },
                        modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp),
                        colors = TextFieldDefaults.outlinedTextFieldColors(backgroundColor = InputFieldBackground, textColor = Color.White, cursorColor = Color.White, focusedBorderColor = Color.Transparent, unfocusedBorderColor = Color.Transparent),
                        leadingIcon = { Icon(painter = painterResource(id = R.drawable.ic_link), contentDescription = null, tint = Color.White.copy(alpha = 0.6f), modifier = Modifier.size(20.dp)) })
                }
            }
            item {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text("Username", color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    Spacer(modifier = Modifier.height(6.dp))
                    OutlinedTextField(value = viewModel.smsConfig.username.value, onValueChange = viewModel::updateSmsUsername,
                        placeholder = { Text("Username", color = TextSecondary.copy(alpha = 0.6f), fontSize = 14.sp) },
                        modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp),
                        colors = TextFieldDefaults.outlinedTextFieldColors(backgroundColor = InputFieldBackground, textColor = Color.White, cursorColor = Color.White, focusedBorderColor = Color.Transparent, unfocusedBorderColor = Color.Transparent),
                        leadingIcon = { Icon(painter = painterResource(id = R.drawable.ic_user), contentDescription = null, tint = Color.White.copy(alpha = 0.6f), modifier = Modifier.size(20.dp)) })
                }
            }
            item {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text("Password", color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    Spacer(modifier = Modifier.height(6.dp))
                    OutlinedTextField(value = viewModel.smsConfig.password.value, onValueChange = viewModel::updateSmsPassword,
                        placeholder = { Text("******************", color = TextSecondary.copy(alpha = 0.6f), fontSize = 14.sp) },
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp),
                        colors = TextFieldDefaults.outlinedTextFieldColors(backgroundColor = InputFieldBackground, textColor = Color.White, cursorColor = Color.White, focusedBorderColor = Color.Transparent, unfocusedBorderColor = Color.Transparent),
                        leadingIcon = { Icon(painter = painterResource(id = R.drawable.ic_password), contentDescription = null, tint = Color.White.copy(alpha = 0.6f), modifier = Modifier.size(20.dp)) })
                }
            }
            item {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text("API Key", color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    Spacer(modifier = Modifier.height(6.dp))
                    OutlinedTextField(value = viewModel.smsConfig.apiKey.value, onValueChange = viewModel::updateSmsApiKey,
                        placeholder = { Text("Enter API key", color = TextSecondary.copy(alpha = 0.6f), fontSize = 14.sp) },
                        modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp),
                        colors = TextFieldDefaults.outlinedTextFieldColors(backgroundColor = InputFieldBackground, textColor = Color.White, cursorColor = Color.White, focusedBorderColor = Color.Transparent, unfocusedBorderColor = Color.Transparent),
                        leadingIcon = { Icon(painter = painterResource(id = R.drawable.ic_key), contentDescription = null, tint = Color.White.copy(alpha = 0.6f), modifier = Modifier.size(20.dp)) })
                }
            }
            item {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text("Sender ID", color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    Spacer(modifier = Modifier.height(6.dp))
                    OutlinedTextField(value = viewModel.smsConfig.senderId.value, onValueChange = viewModel::updateSmsSenderId,
                        placeholder = { Text("000000000", color = TextSecondary.copy(alpha = 0.6f), fontSize = 14.sp) },
                        modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp),
                        colors = TextFieldDefaults.outlinedTextFieldColors(backgroundColor = InputFieldBackground, textColor = Color.White, cursorColor = Color.White, focusedBorderColor = Color.Transparent, unfocusedBorderColor = Color.Transparent),
                        leadingIcon = { Icon(painter = painterResource(id = R.drawable.ic_user), contentDescription = null, tint = Color.White.copy(alpha = 0.6f), modifier = Modifier.size(20.dp)) })
                }
            }
            item { Spacer(modifier = Modifier.height(16.dp)) }
        }
        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(
                onClick = { viewModel.saveSmsSettings(context); Toast.makeText(context, "SMS settings saved", Toast.LENGTH_SHORT).show() },
                modifier = Modifier.weight(1f).height(56.dp),
                colors = ButtonDefaults.buttonColors(backgroundColor = ActiveTabColor, contentColor = Color.White),
                shape = RoundedCornerShape(28.dp)
            ) { Text("Save SMS", fontSize = 15.sp, fontWeight = FontWeight.Medium) }
            Button(
                onClick = { viewModel.sendTestSms(context) },
                modifier = Modifier.weight(1f).height(56.dp),
                colors = ButtonDefaults.buttonColors(backgroundColor = ActiveTabColor, contentColor = Color.White),
                shape = RoundedCornerShape(28.dp)
            ) { Text("Test SMS", fontSize = 15.sp, fontWeight = FontWeight.Medium) }
        }
    }
}