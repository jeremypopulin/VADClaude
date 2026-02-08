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
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.visualduress.R
import com.example.visualduress.ui.components.AdvancedModernSlider
import com.example.visualduress.ui.components.ModernSlider
import com.example.visualduress.ui.theme.AccentBlue
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

@Composable
fun SettingsDialog(
    viewModel: DeviceViewModel,
    launcher: ActivityResultLauncher<Array<String>>
) {
    val context = LocalContext.current
    //val licenseType by viewModel.licenseType
    //val isBasic = licenseType == "BASIC"
    val licenseType by viewModel.licenseType
    val isBasic = licenseType != "PREMIUM"
    val initialTab = if (LicenseManager.isLicenseValid(context)) "devices" else "license"
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

    AlertDialog(
        modifier = Modifier
            .fillMaxWidth(0.95f)
            .fillMaxHeight(0.9f),
        onDismissRequest = { viewModel.closeSettings() },
        buttons = {},
        backgroundColor = DialogBackground,
        shape = RoundedCornerShape(16.dp),
        text = {
            Box(modifier = Modifier.fillMaxSize()) {
                Column(modifier = Modifier.fillMaxSize()) {
                    // Modern Header
                    ModernDialogHeader()

                    Spacer(modifier = Modifier.height(16.dp))

                    // Modern Tab Navigation with Custom Icons
                    ModernTabNavigation(
                        activeTab = activeTab,
                        isBasic = isBasic,
                        onTabChange = { activeTabState.value = it }
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    // Content Area
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                    ) {
                        when (activeTab) {
                            "devices" -> DeviceSettingsContent(viewModel, isBasic)
                            "floorplan" -> FloorplanContent(viewModel, launcher, floorplanUri)
                            "ip" -> IpContent(viewModel, modbusIp, context)
                            "password" -> PasswordContent(
                                viewModel,
                                newPassword,
                                context,
                                onPasswordChange = { newPassword = it }
                            )
                            "sms" -> {
                                if (!isBasic) {
                                    SmsSettings(viewModel)
                                }
                            }
                            "license" -> LicenseContent(
                                viewModel,
                                context,
                                licenseKey,
                                onLicenseKeyChange = { licenseKey = it },
                                deviceId,
                                licenseType
                            )
                            "about" -> AboutContent(appVersion, companyName, websiteUrl)
                        }
                    }

                    // Modern Close Button
                    ModernCloseButton(
                        onClick = { viewModel.closeSettings() }
                    )
                }
            }
        }
    )
}

@Composable
private fun ModernDialogHeader() {

    Row(
        modifier = Modifier
            .fillMaxWidth()
            //.background(HeaderBackground),
            .padding(16.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            painter = painterResource(id = R.drawable.vadlogo),
            contentDescription = "App Logo",
            modifier = Modifier.height(30.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = "Visual Alert Display",
            color = TextPrimary,
            fontSize = 16.sp
        )
    }
}

@Composable
private fun ModernTabNavigation(
    activeTab: String,
    isBasic: Boolean,
    onTabChange: (String) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        ModernIconTabDrawable(
            tabId = "devices",
            activeTab = activeTab,
            iconRes = R.drawable.ic_devices,
            label = "Devices",
            onClick = { onTabChange("devices") }
        )

        ModernIconTabDrawable(
            tabId = "floorplan",
            activeTab = activeTab,
            iconRes = R.drawable.ic_floorplan,
            label = "Floorplan",
            onClick = { onTabChange("floorplan") }
        )

        ModernIconTabDrawable(
            tabId = "ip",
            activeTab = activeTab,
            iconRes = R.drawable.ic_ip,
            label = "IP",
            onClick = { onTabChange("ip") }
        )

        ModernIconTabDrawable(
            tabId = "password",
            activeTab = activeTab,
            iconRes = R.drawable.ic_password,
            label = "Password",
            onClick = { onTabChange("password") }
        )

        if (!isBasic) {
            ModernIconTabDrawable(
                tabId = "sms",
                activeTab = activeTab,
                iconRes = R.drawable.ic_sms,
                label = "SMS",
                onClick = { onTabChange("sms") }
            )
        }

        ModernIconTabDrawable(
            tabId = "license",
            activeTab = activeTab,
            iconRes = R.drawable.ic_license,
            label = "Licence",
            onClick = { onTabChange("license") }
        )

        ModernIconTabDrawable(
            tabId = "about",
            activeTab = activeTab,
            iconRes = R.drawable.ic_about,
            label = "About",
            onClick = { onTabChange("about") }
        )
    }
}

// Custom Drawable Icon Tab (Use this with Figma exports)
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

// Fallback: Material Icon Version (if you don't have custom icons yet)
@Composable
fun ModernIconTab(
    tabId: String,
    activeTab: String,
    icon: ImageVector,
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
                imageVector = icon,
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
private fun ModernCloseButton(onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .height(52.dp),
        colors = ButtonDefaults.buttonColors(
            backgroundColor = Color.White,
            contentColor = CloseButtonColor
        ),
        shape = RoundedCornerShape(26.dp),
        elevation = ButtonDefaults.elevation(
            defaultElevation = 0.dp,
            pressedElevation = 2.dp
        )
    ) {
        Text(
            text = "Close ×",
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
fun DeviceSettingsContent(viewModel: DeviceViewModel, isBasic: Boolean) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            "Device Settings",
            fontSize = 20.sp,
            color = TextPrimary,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(16.dp))

        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
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
                        color = TextSecondary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    // Device Name Field
                    OutlinedTextField(
                        value = device.name.value,
                        onValueChange = {
                            device.name.value = it
                            viewModel.saveDeviceStates()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(8.dp),
                        colors = TextFieldDefaults.outlinedTextFieldColors(
                            backgroundColor = TabIconBackground,
                            textColor = Color.White,
                            cursorColor = Color.White,
                            focusedBorderColor = AccentBlue,
                            unfocusedBorderColor = Color.Transparent
                        ),
                        leadingIcon = {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_device),
                                contentDescription = null,
                                tint = Color.White.copy(alpha = 0.6f),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Enabled Toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Enabled", color = TextPrimary)
                        Switch(
                            checked = device.isEnabled.value,
                            onCheckedChange = {
                                device.isEnabled.value = it
                                viewModel.saveDeviceStates()
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = DarkBlue,
                                checkedTrackColor = Color.White,
                                uncheckedThumbColor = Color.White,
                                uncheckedTrackColor = Color.Black
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Size Slider
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            "Size: ${device.size.value.toInt()}%",
                            color = TextPrimary
                        )
                        Slider(
                            value = device.size.value,
                            onValueChange = {
                                device.size.value = it
                                viewModel.saveDeviceStates()
                            },
                            valueRange = 30f..250f,
                            modifier = Modifier.fillMaxWidth(),
                            colors = SliderDefaults.colors(
                                thumbColor = DarkBlue,
                                activeTrackColor = DarkBlue,
                                inactiveTrackColor = Color.Black
                            )
                        )
                        /*AdvancedModernSlider(
                            value = device.size.value,
                            onValueChange = {
                                device.size.value = it
                                viewModel.saveDeviceStates()
                            },
                            valueRange = 30f..250f,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            activeTrackColor = Color(0xFF4A90E2), // Cyan/Blue
                            inactiveTrackColor = Color(0xFF2E3440), // Dark gray
                            thumbColor = Color(0xFF5A6F83), // Medium gray
                            thumbIconRes = R.drawable.ic_pause
                        )*/
                        /*ModernSlider(
                            value = device.size.value,
                            onValueChange = {
                                device.size.value = it
                                viewModel.saveDeviceStates()
                            },
                            valueRange = 30f..250f,
                            modifier = Modifier.fillMaxWidth(),
                            activeTrackColor = Color(0xFF4A90E2), // Blue active track
                            inactiveTrackColor = Color(0xFF2E3440), // Dark inactive track
                            thumbColor = Color(0xFF5A6F83), // Gray thumb
                            thumbIconRes = R.drawable.ic_pause // Custom pause icon
                        )*/
                    }

                    // Advanced Section
                    if (advancedExpanded) {
                        Spacer(modifier = Modifier.height(12.dp))

                        // Camera Stream URL
                        Text(
                            "Camera Stream URL",
                            color = TextSecondary,
                            fontSize = 14.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        OutlinedTextField(
                            value = device.streamUrl.value.orEmpty(),
                            onValueChange = {
                                device.streamUrl.value = it
                                viewModel.saveDeviceStates()
                            },
                            placeholder = {
                                Text(
                                    "https://www.demo.com",
                                    color = TextSecondary.copy(alpha = 0.5f)
                                )
                            },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp),
                            colors = TextFieldDefaults.outlinedTextFieldColors(
                                backgroundColor = TabIconBackground,
                                textColor = Color.White,
                                cursorColor = Color.White,
                                focusedBorderColor = AccentBlue,
                                unfocusedBorderColor = Color.Transparent
                            ),
                            leadingIcon = {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_link),
                                    contentDescription = null,
                                    tint = Color.White.copy(alpha = 0.6f),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        // Enable Camera Link
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Enable Camera Link", color = TextPrimary)
                            Switch(
                                checked = device.cameraEnabled.value,
                                onCheckedChange = if (isBasic) null else {
                                    { isChecked ->
                                        device.cameraEnabled.value = isChecked
                                        viewModel.saveDeviceStates()
                                    }
                                },
                                enabled = !isBasic,
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = DarkBlue,
                                    checkedTrackColor = Color.White,
                                    uncheckedThumbColor = Color.White,
                                    uncheckedTrackColor = Color.Black
                                )
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Enable SMS Alerts
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Enable SMS Alerts", color = TextPrimary)
                            Switch(
                                checked = device.smsEnabled.value,
                                onCheckedChange = if (isBasic) null else {
                                    { isChecked ->
                                        device.smsEnabled.value = isChecked
                                        viewModel.saveDeviceStates()
                                    }
                                },
                                enabled = !isBasic,
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = DarkBlue,
                                    checkedTrackColor = Color.White,
                                    uncheckedThumbColor = Color.White,
                                    uncheckedTrackColor = Color.Black
                                )
                            )
                        }
                    }

                    // Toggle Advanced
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = { advancedExpanded = !advancedExpanded }) {
                            Text(
                                text = if (advancedExpanded) "Hide Advanced" else "Show Advanced",
                                color = Color.White,
                                fontSize = 14.sp,
                                textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline

                            )
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = { viewModel.resetDevicePositions() },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        backgroundColor = TabIconBackground,
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Reset Device Positions")
                }

                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = { viewModel.toggleAllDevices() },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        backgroundColor = TabIconBackground,
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Toggle All Devices")
                }
            }
        }
    }
}

@Composable
fun FloorplanContent(
    viewModel: DeviceViewModel,
    launcher: ActivityResultLauncher<Array<String>>,
    floorplanUri: Uri?
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            "Floorplan Images",
            fontSize = 24.sp,
            color = TextPrimary,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(20.dp))

        // Select Floorplan Button
        Button(
            onClick = { launcher.launch(arrayOf("image/*")) },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                backgroundColor = ActiveTabColor,
                contentColor = Color.White
            ),
            shape = RoundedCornerShape(12.dp),
            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 14.dp)
        ) {
            Text(
                "Select Floorplan Image",
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text("⌄", fontSize = 20.sp)
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Floorplan Image Preview
        floorplanUri?.let { uri ->
            AsyncImage(
                model = uri,
                contentDescription = "Selected Floorplan",
                modifier = Modifier
                    .fillMaxWidth()
                    .height(400.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.White)
            )
        }

        Spacer(modifier = Modifier.weight(1f))

        // Remove Floorplan Button
        if (floorplanUri != null) {
            Button(
                onClick = { viewModel.setFloorplanUri(Uri.EMPTY) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                colors = ButtonDefaults.buttonColors(
                    backgroundColor = ActiveTabColor,
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(28.dp)
            ) {
                Text(
                    "Remove Floorplan",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text("→", fontSize = 18.sp)
            }
        }
    }
}

@Composable
fun IpContent(
    viewModel: DeviceViewModel,
    modbusIp: String,
    context: Context
) {
    var ipInput by remember { mutableStateOf(modbusIp) }

    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            "I/O IP Settings",
            fontSize = 24.sp,
            color = TextPrimary,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(20.dp))

        Text(
            "Moxa IP Address",
            fontSize = 14.sp,
            color = TextPrimary,
            fontWeight = FontWeight.Medium
        )
        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = ipInput,
            onValueChange = { ipInput = it },
            placeholder = {
                Text(
                    "Enter IP Address",
                    color = TextSecondary.copy(alpha = 0.6f),
                    fontSize = 14.sp
                )
            },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = TextFieldDefaults.outlinedTextFieldColors(
                backgroundColor = InputFieldBackground,
                textColor = Color.White,
                cursorColor = Color.White,
                focusedBorderColor = Color.Transparent,
                unfocusedBorderColor = Color.Transparent
            ),
            leadingIcon = {
                Icon(
                    painter = painterResource(id = R.drawable.ic_device),
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.6f),
                    modifier = Modifier.size(20.dp)
                )
            }
        )

        Spacer(modifier = Modifier.weight(1f))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(
                onClick = {
                    Toast.makeText(context, "Testing connection...", Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier
                    .weight(1f)
                    .height(56.dp),
                colors = ButtonDefaults.buttonColors(
                    backgroundColor = TabIconBackground,
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(28.dp)
            ) {
                Text("Test", fontSize = 16.sp, fontWeight = FontWeight.Medium)
                Spacer(modifier = Modifier.width(8.dp))
                Icon(
                    Icons.Filled.Refresh,
                    contentDescription = null,
                    tint = AccentBlue,
                    modifier = Modifier.size(20.dp)
                )
            }

            Button(
                onClick = {
                    if (ipInput.trim().isNotEmpty()) {
                        viewModel.setModbusIp(ipInput.trim())
                        Toast.makeText(context, "✅ IP saved", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "⚠️ Invalid IP", Toast.LENGTH_SHORT).show()
                    }
                },
                modifier = Modifier
                    .weight(1f)
                    .height(56.dp),
                colors = ButtonDefaults.buttonColors(
                    backgroundColor = ActiveTabColor,
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(28.dp)
            ) {
                Text("Save IP", fontSize = 16.sp, fontWeight = FontWeight.Medium)
            }
        }
    }
}

@Composable
fun PasswordContent(
    viewModel: DeviceViewModel,
    password: String,
    context: Context,
    onPasswordChange: (String) -> Unit
) {
    var confirmPassword by remember { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            "Change Password",
            fontSize = 24.sp,
            color = TextPrimary,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(24.dp))

        Text(
            "New Password",
            fontSize = 18.sp,
            color = TextPrimary,
            fontWeight = FontWeight.Medium
        )
        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = password,
            onValueChange = onPasswordChange,
            placeholder = {
                Text(
                    "********************",
                    color = TextSecondary.copy(alpha = 0.6f),
                    fontSize = 16.sp
                )
            },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = TextFieldDefaults.outlinedTextFieldColors(
                backgroundColor = InputFieldBackground,
                textColor = Color.White,
                cursorColor = Color.White,
                focusedBorderColor = Color.Transparent,
                unfocusedBorderColor = Color.Transparent
            ),
            leadingIcon = {
                Icon(
                    painter = painterResource(id = R.drawable.ic_password),
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.6f),
                    modifier = Modifier.size(20.dp)
                )
            }
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            "Confirm Password",
            fontSize = 18.sp,
            color = TextPrimary,
            fontWeight = FontWeight.Medium
        )
        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = confirmPassword,
            onValueChange = { confirmPassword = it },
            placeholder = {
                Text(
                    "********************",
                    color = TextSecondary.copy(alpha = 0.6f),
                    fontSize = 16.sp
                )
            },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = TextFieldDefaults.outlinedTextFieldColors(
                backgroundColor = InputFieldBackground,
                textColor = Color.White,
                cursorColor = Color.White,
                focusedBorderColor = Color.Transparent,
                unfocusedBorderColor = Color.Transparent
            ),
            leadingIcon = {
                Icon(
                    painter = painterResource(id = R.drawable.ic_password),
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.6f),
                    modifier = Modifier.size(20.dp)
                )
            }
        )

        Spacer(modifier = Modifier.weight(1f))

        Button(
            onClick = {
                if (password.isNotEmpty() && password == confirmPassword) {
                    viewModel.changePassword(password)
                    onPasswordChange("")
                    confirmPassword = ""
                    Toast.makeText(context, "✅ Password changed", Toast.LENGTH_SHORT).show()
                } else if (password != confirmPassword) {
                    Toast.makeText(context, "❌ Passwords don't match", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "⚠️ Enter a password", Toast.LENGTH_SHORT).show()
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            colors = ButtonDefaults.buttonColors(
                backgroundColor = ActiveTabColor,
                contentColor = Color.White
            ),
            shape = RoundedCornerShape(28.dp)
        ) {
            Text("Save", fontSize = 16.sp, fontWeight = FontWeight.Medium)
            Spacer(modifier = Modifier.width(4.dp))
            Text("→", fontSize = 18.sp)
        }
    }
}

@Composable
fun LicenseContent(
    viewModel: DeviceViewModel,
    context: Context,
    licenseKey: String,
    onLicenseKeyChange: (String) -> Unit,
    deviceId: String,
    licenseType: String
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            "Device ID",
            fontSize = 18.sp,
            color = TextPrimary,
            fontWeight = FontWeight.Medium
        )
        Spacer(modifier = Modifier.height(8.dp))

        SelectionContainer {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(InputFieldBackground, RoundedCornerShape(12.dp))
                    .padding(16.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_user),
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.6f),
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(deviceId, color = TextPrimary, fontSize = 16.sp)
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            "Stored Licence Key",
            fontSize = 18.sp,
            color = TextPrimary,
            fontWeight = FontWeight.Medium
        )
        Spacer(modifier = Modifier.height(8.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(InputFieldBackground, RoundedCornerShape(12.dp))
                .padding(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_key),
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.6f),
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    LicenseManager.getLicenseType(context).takeIf { it.isNotEmpty() }
                        ?: "No license stored",
                    color = TextPrimary,
                    fontSize = 16.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            "Enter New Licence Key",
            fontSize = 18.sp,
            color = TextPrimary,
            fontWeight = FontWeight.Medium
        )
        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = licenseKey,
            onValueChange = onLicenseKeyChange,
            placeholder = {
                Text(
                    "********************",
                    color = TextSecondary.copy(alpha = 0.6f),
                    fontSize = 16.sp
                )
            },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = TextFieldDefaults.outlinedTextFieldColors(
                backgroundColor = InputFieldBackground,
                textColor = Color.White,
                cursorColor = Color.White,
                focusedBorderColor = Color.Transparent,
                unfocusedBorderColor = Color.Transparent
            ),
            leadingIcon = {
                Icon(
                    painter = painterResource(id = R.drawable.ic_key),
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.6f),
                    modifier = Modifier.size(20.dp)
                )
            }
        )

        Spacer(modifier = Modifier.weight(1f))

        /*Button(
            onClick = {
                if (LicenseManager.validateLicense(context, licenseKey)) {
                    LicenseManager.saveLicense(context, licenseKey)
                    viewModel.refreshLicenseType(context)
                    Toast.makeText(context, "✅ License activated", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "❌ Invalid license", Toast.LENGTH_SHORT).show()
                }
            },*/
        Button(
            onClick = {
                val trimmedKey = licenseKey.trim()

                if (trimmedKey.isEmpty()) {
                    Toast.makeText(context, "⚠️ Please enter a license key", Toast.LENGTH_SHORT).show()
                    return@Button
                }

                Log.d("Settings", "🔑 Activating license: $trimmedKey")

                if (LicenseManager.validateLicense(context, trimmedKey)) {
                    LicenseManager.saveLicense(context, trimmedKey)
                    Log.d("Settings", "✅ License saved, forcing refresh...")

                    // Use the new force refresh function
                    viewModel.forceRefreshLicense(context) {
                        val currentType = LicenseManager.getLicenseType(context)
                        Toast.makeText(
                            context,
                            "✅ License activated: $currentType",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                } else {
                    Toast.makeText(context, "❌ Invalid license key", Toast.LENGTH_LONG).show()
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            colors = ButtonDefaults.buttonColors(
                backgroundColor = ActiveTabColor,
                contentColor = Color.White
            ),
            shape = RoundedCornerShape(28.dp)
        ) {
            Text("Activate", fontSize = 16.sp, fontWeight = FontWeight.Medium)
            Spacer(modifier = Modifier.width(4.dp))
            Text("→", fontSize = 18.sp)
        }
    }
}

@Composable
fun AboutContent(
    appVersion: String,
    companyName: String,
    websiteUrl: String
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(InputFieldBackground, RoundedCornerShape(16.dp))
                .padding(32.dp)
        ) {
            Column {
                Text(
                    "About This App",
                    fontSize = 28.sp,
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    "App Version: $appVersion",
                    fontSize = 18.sp,
                    color = TextPrimary,
                    fontWeight = FontWeight.Normal
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    "Company: $companyName",
                    fontSize = 18.sp,
                    color = TextPrimary,
                    fontWeight = FontWeight.Normal
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    "Website: $websiteUrl",
                    fontSize = 18.sp,
                    color = TextPrimary,
                    fontWeight = FontWeight.Normal
                )
            }
        }

        Spacer(modifier = Modifier.weight(1f))
        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
fun SmsSettings(viewModel: DeviceViewModel) {
    val context = LocalContext.current

    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            "SMS Settings",
            fontSize = 24.sp,
            color = TextPrimary,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(20.dp))

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items(5) { index ->
                val entry = viewModel.smsConfig.smsNumbers[index]
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Name",
                            color = TextPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        OutlinedTextField(
                            value = entry.label.value,
                            onValueChange = { viewModel.updateSmsNumberLabel(index, it) },
                            placeholder = {
                                Text(
                                    "Enter your name",
                                    color = TextSecondary.copy(alpha = 0.6f),
                                    fontSize = 14.sp
                                )
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = TextFieldDefaults.outlinedTextFieldColors(
                                backgroundColor = InputFieldBackground,
                                textColor = Color.White,
                                cursorColor = Color.White,
                                focusedBorderColor = Color.Transparent,
                                unfocusedBorderColor = Color.Transparent
                            ),
                            leadingIcon = {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_user),
                                    contentDescription = null,
                                    tint = Color.White.copy(alpha = 0.6f),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        )
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Phone Number",
                            color = TextPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        OutlinedTextField(
                            value = entry.number.value,
                            onValueChange = { viewModel.updateSmsNumber(index, it) },
                            placeholder = {
                                Text(
                                    "Enter phone number",
                                    color = TextSecondary.copy(alpha = 0.6f),
                                    fontSize = 14.sp
                                )
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = TextFieldDefaults.outlinedTextFieldColors(
                                backgroundColor = InputFieldBackground,
                                textColor = Color.White,
                                cursorColor = Color.White,
                                focusedBorderColor = Color.Transparent,
                                unfocusedBorderColor = Color.Transparent
                            ),
                            leadingIcon = {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_call),
                                    contentDescription = null,
                                    tint = Color.White.copy(alpha = 0.6f),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        )
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "Gateway Settings",
                    color = TextPrimary,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            item {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        "SMS Gateway URL",
                        color = TextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    OutlinedTextField(
                        value = viewModel.smsConfig.gatewayUrl.value,
                        onValueChange = viewModel::updateSmsGatewayUrl,
                        placeholder = {
                            Text(
                                "https://www.demo.com",
                                color = TextSecondary.copy(alpha = 0.6f),
                                fontSize = 14.sp
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = TextFieldDefaults.outlinedTextFieldColors(
                            backgroundColor = InputFieldBackground,
                            textColor = Color.White,
                            cursorColor = Color.White,
                            focusedBorderColor = Color.Transparent,
                            unfocusedBorderColor = Color.Transparent
                        ),
                        leadingIcon = {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_link),
                                contentDescription = null,
                                tint = Color.White.copy(alpha = 0.6f),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    )
                }
            }

            item {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        "Username",
                        color = TextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    OutlinedTextField(
                        value = viewModel.smsConfig.username.value,
                        onValueChange = viewModel::updateSmsUsername,
                        placeholder = {
                            Text(
                                "98RRx5",
                                color = TextSecondary.copy(alpha = 0.6f),
                                fontSize = 14.sp
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = TextFieldDefaults.outlinedTextFieldColors(
                            backgroundColor = InputFieldBackground,
                            textColor = Color.White,
                            cursorColor = Color.White,
                            focusedBorderColor = Color.Transparent,
                            unfocusedBorderColor = Color.Transparent
                        ),
                        leadingIcon = {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_user),
                                contentDescription = null,
                                tint = Color.White.copy(alpha = 0.6f),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    )
                }
            }

            item {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        "Password",
                        color = TextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    OutlinedTextField(
                        value = viewModel.smsConfig.password.value,
                        onValueChange = viewModel::updateSmsPassword,
                        placeholder = {
                            Text(
                                "******************",
                                color = TextSecondary.copy(alpha = 0.6f),
                                fontSize = 14.sp
                            )
                        },
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = TextFieldDefaults.outlinedTextFieldColors(
                            backgroundColor = InputFieldBackground,
                            textColor = Color.White,
                            cursorColor = Color.White,
                            focusedBorderColor = Color.Transparent,
                            unfocusedBorderColor = Color.Transparent
                        ),
                        leadingIcon = {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_password),
                                contentDescription = null,
                                tint = Color.White.copy(alpha = 0.6f),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    )
                }
            }

            item {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        "API Key",
                        color = TextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    OutlinedTextField(
                        value = viewModel.smsConfig.apiKey.value,
                        onValueChange = viewModel::updateSmsApiKey,
                        placeholder = {
                            Text(
                                "Enter API key",
                                color = TextSecondary.copy(alpha = 0.6f),
                                fontSize = 14.sp
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = TextFieldDefaults.outlinedTextFieldColors(
                            backgroundColor = InputFieldBackground,
                            textColor = Color.White,
                            cursorColor = Color.White,
                            focusedBorderColor = Color.Transparent,
                            unfocusedBorderColor = Color.Transparent
                        ),
                        leadingIcon = {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_key),
                                contentDescription = null,
                                tint = Color.White.copy(alpha = 0.6f),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    )
                }
            }

            item {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        "Sender ID",
                        color = TextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    OutlinedTextField(
                        value = viewModel.smsConfig.senderId.value,
                        onValueChange = viewModel::updateSmsSenderId,
                        placeholder = {
                            Text(
                                "000000000",
                                color = TextSecondary.copy(alpha = 0.6f),
                                fontSize = 14.sp
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = TextFieldDefaults.outlinedTextFieldColors(
                            backgroundColor = InputFieldBackground,
                            textColor = Color.White,
                            cursorColor = Color.White,
                            focusedBorderColor = Color.Transparent,
                            unfocusedBorderColor = Color.Transparent
                        ),
                        leadingIcon = {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_user),
                                contentDescription = null,
                                tint = Color.White.copy(alpha = 0.6f),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    )
                }
            }

            item { Spacer(modifier = Modifier.height(16.dp)) }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(
                onClick = {
                    viewModel.saveSmsSettings(context)
                    Toast.makeText(context, "✅ SMS settings saved", Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier
                    .weight(1f)
                    .height(56.dp),
                colors = ButtonDefaults.buttonColors(
                    backgroundColor = ActiveTabColor,
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(28.dp)
            ) {
                Text("Save SMS", fontSize = 15.sp, fontWeight = FontWeight.Medium)
            }

            Button(
                onClick = { viewModel.sendTestSms(context) },
                modifier = Modifier
                    .weight(1f)
                    .height(56.dp),
                colors = ButtonDefaults.buttonColors(
                    backgroundColor = ActiveTabColor,
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(28.dp)
            ) {
                Text("Test SMS", fontSize = 15.sp, fontWeight = FontWeight.Medium)
            }
        }
    }
}