package com.example.visualduress.ui

import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Message
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.visualduress.viewmodel.DeviceViewModel
import com.example.visualduress.ui.components.IconTab
import com.example.visualduress.util.*

@Composable
fun SettingsDialog(
    viewModel: DeviceViewModel,
    launcher: ActivityResultLauncher<Array<String>>
) {
    val context = LocalContext.current
    val licenseType by viewModel.licenseType
    val isBasic = licenseType == "BASIC"
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
    var licenseStatus by remember { mutableStateOf(LicenseManager.isLicenseValid(context)) }
    val deviceId = remember { LicenseManager.getDeviceId(context) }

    val licenseFileLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri?.let {
            val inputStream = context.contentResolver.openInputStream(it)
            val reader = inputStream?.bufferedReader()
            licenseKey = reader?.readLine().orEmpty()
        }
    }

    AlertDialog(
        modifier = Modifier.padding(top = 32.dp),
        onDismissRequest = { viewModel.closeSettings() },
        buttons = {},
        backgroundColor = Color.White,
        shape = RoundedCornerShape(16.dp),
        text = {
            Box(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.fillMaxWidth().padding(top = 32.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth().background(Color.White),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        IconTab("devices", activeTab, Icons.Filled.Menu) { activeTabState.value = "devices" }
                        IconTab("floorplan", activeTab, Icons.Filled.Menu) { activeTabState.value = "floorplan" }
                        IconTab("ip", activeTab, Icons.Filled.Menu) { activeTabState.value = "ip" }
                        IconTab("password", activeTab, Icons.Filled.Menu) { activeTabState.value = "password" }

                        if (!isBasic) {
                            IconTab("sms", activeTab, Icons.Filled.Message) { activeTabState.value = "sms" }
                        }

                        IconTab("license", activeTab, Icons.Filled.Info) { activeTabState.value = "license" }
                        IconTab("about", activeTab, Icons.Filled.Info) { activeTabState.value = "about" }
                    }

                    Divider(color = Color.LightGray)

                    Column(modifier = Modifier.padding(16.dp)) {
                        when (activeTab) {
                            "devices" -> {
                                Text("Device Settings", fontSize = 18.sp)
                                Spacer(modifier = Modifier.height(8.dp))
                                LazyColumn {
                                    items(viewModel.deviceStates) { device ->
                                        var advancedExpanded by remember { mutableStateOf(false) }

                                        Column(modifier = Modifier.padding(vertical = 8.dp)) {
                                            OutlinedTextField(
                                                value = device.name.value,
                                                onValueChange = {
                                                    device.name.value = it
                                                    viewModel.saveDeviceStates()
                                                },
                                                modifier = Modifier.fillMaxWidth(),
                                                singleLine = true,
                                                shape = RoundedCornerShape(10.dp)
                                            )

                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text("Enabled")
                                                Switch(
                                                    checked = device.isEnabled.value,
                                                    onCheckedChange = {
                                                        device.isEnabled.value = it
                                                        viewModel.saveDeviceStates()
                                                    }
                                                )
                                            }

                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Text("Size: ${device.size.value.toInt()}%", modifier = Modifier.width(80.dp))
                                                Slider(
                                                    value = device.size.value,
                                                    onValueChange = {
                                                        device.size.value = it
                                                        viewModel.saveDeviceStates()
                                                    },
                                                    valueRange = 30f..250f,
                                                    modifier = Modifier.weight(1f)
                                                )
                                            }

                                            TextButton(
                                                onClick = { advancedExpanded = !advancedExpanded },
                                                modifier = Modifier.align(Alignment.End)
                                            ) {
                                                Text(if (advancedExpanded) "Hide Advanced" else "Show Advanced")
                                            }

                                            if (advancedExpanded) {
                                                Column {
                                                    Text("Camera Stream URL", style = MaterialTheme.typography.subtitle2)
                                                    OutlinedTextField(
                                                        value = device.streamUrl.value.orEmpty(),
                                                        onValueChange = {
                                                            device.streamUrl.value = it
                                                            viewModel.saveDeviceStates()
                                                        },
                                                        label = { Text("RTSP or HTTP Stream") },
                                                        singleLine = true,
                                                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                                                    )
                                                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                                                        Text("Enable Camera Link", modifier = Modifier.weight(1f))
                                                        Switch(
                                                            checked = device.cameraEnabled.value,
                                                            onCheckedChange = if (isBasic) null else {
                                                                { isChecked ->
                                                                    device.cameraEnabled.value = isChecked
                                                                    viewModel.saveDeviceStates()
                                                                }
                                                            },
                                                            enabled = !isBasic
                                                        )
                                                    }

                                                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                                                        Text("Enable SMS Alerts", modifier = Modifier.weight(1f))
                                                        Switch(
                                                            checked = device.smsEnabled.value,
                                                            onCheckedChange = if (isBasic) null else {
                                                                { isChecked ->
                                                                    device.smsEnabled.value = isChecked
                                                                    viewModel.saveDeviceStates()
                                                                }
                                                            },
                                                            enabled = !isBasic
                                                        )
                                                    }
                                                }
                                            }

                                            Divider(color = Color.LightGray)
                                        }
                                    }

                                    item {
                                        Spacer(modifier = Modifier.height(16.dp))
                                        Button(
                                            onClick = { viewModel.resetDevicePositions() },
                                            modifier = Modifier.fillMaxWidth()
                                        ) { Text("Reset Device Positions") }

                                        Spacer(modifier = Modifier.height(8.dp))
                                        Button(
                                            onClick = { viewModel.toggleAllDevices() },
                                            modifier = Modifier.fillMaxWidth()
                                        ) { Text("Toggle All Devices") }
                                    }
                                }
                            }

                            "floorplan" -> {
                                Column {
                                    Text("Floorplan Image", fontSize = 18.sp)
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Button(onClick = { launcher.launch(arrayOf("image/*")) }) {
                                        Text("Select Floorplan Image")
                                    }
                                    Spacer(modifier = Modifier.height(12.dp))
                                    floorplanUri?.let { uri ->
                                        AsyncImage(
                                            model = uri,
                                            contentDescription = "Selected Floorplan",
                                            modifier = Modifier.fillMaxWidth().height(150.dp)
                                        )
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Button(
                                            onClick = { viewModel.setFloorplanUri(Uri.EMPTY) },
                                            colors = ButtonDefaults.buttonColors(backgroundColor = Color.Red, contentColor = Color.White),
                                            modifier = Modifier.fillMaxWidth()
                                        ) { Text("Remove Floorplan") }
                                    }
                                }
                            }

                            "ip" -> {
                                var ipInput by remember { mutableStateOf(modbusIp) }

                                Column {
                                    Text("Modbus IP Address", fontSize = 18.sp)
                                    Spacer(modifier = Modifier.height(8.dp))
                                    OutlinedTextField(
                                        value = ipInput,
                                        onValueChange = { ipInput = it },
                                        label = { Text("Modbus IP") },
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Button(
                                        onClick = {
                                            if (ipInput.trim().isNotEmpty()) {
                                                viewModel.setModbusIp(ipInput.trim())
                                                Toast.makeText(context, "✅ IP saved and polling restarted", Toast.LENGTH_SHORT).show()
                                            } else {
                                                Toast.makeText(context, "⚠️ Please enter a valid IP address", Toast.LENGTH_SHORT).show()
                                            }
                                        },
                                        modifier = Modifier.align(Alignment.End)
                                    ) { Text("Save IP") }
                                }
                            }

                            "password" -> {
                                Column {
                                    Text("Change Password", fontSize = 18.sp)
                                    Spacer(modifier = Modifier.height(8.dp))
                                    OutlinedTextField(
                                        value = newPassword,
                                        onValueChange = { newPassword = it },
                                        label = { Text("New Password") },
                                        singleLine = true,
                                        visualTransformation = PasswordVisualTransformation(),
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Button(
                                        onClick = {
                                            viewModel.changePassword(newPassword)
                                            newPassword = ""
                                        },
                                        modifier = Modifier.align(Alignment.End)
                                    ) { Text("Save") }
                                }
                            }

                            "sms" -> {
                                if (!isBasic) {
                                    SmsSettings(viewModel)
                                }
                            }

                            "license" -> {
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    Text("Device ID", style = MaterialTheme.typography.subtitle2)
                                    SelectionContainer { Text(deviceId) }
                                    Spacer(modifier = Modifier.height(8.dp))

                                    Text(
                                        "License Type: ${licenseType.capitalize()}",
                                        style = MaterialTheme.typography.subtitle2,
                                        modifier = Modifier.padding(bottom = 12.dp)
                                    )

                                    OutlinedTextField(
                                        value = licenseKey,
                                        onValueChange = { licenseKey = it },
                                        label = { Text("Enter New License Key") },
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Button(onClick = {
                                        if (LicenseManager.validateLicense(context, licenseKey)) {
                                            LicenseManager.saveLicense(context, licenseKey)
                                            viewModel.refreshLicenseType(context)
                                            licenseStatus = true
                                            Toast.makeText(context, "✅ License activated", Toast.LENGTH_SHORT).show()
                                        } else {
                                            licenseStatus = false
                                            Toast.makeText(context, "❌ Invalid license key", Toast.LENGTH_SHORT).show()
                                        }
                                    }) {
                                        Text("Activate")
                                    }
                                }
                            }

                            "about" -> {
                                Column {
                                    Text("About This App", fontSize = 18.sp)
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text("App Version: $appVersion")
                                    Text("Company: $companyName")
                                    Text("Website: $websiteUrl")
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = { viewModel.closeSettings() },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(
                                backgroundColor = Color(0xFF2196F3),
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("Close")
                        }
                    }
                }
            }
        }
    )
}

@Composable
fun SmsSettings(viewModel: DeviceViewModel) {
    val context = LocalContext.current

    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Text("SMS Settings", fontSize = 18.sp)
        }

        items(5) { index ->
            val entry = viewModel.smsConfig.smsNumbers[index]
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = entry.label.value,
                    onValueChange = { viewModel.updateSmsNumberLabel(index, it) },
                    label = { Text("Name") },
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                OutlinedTextField(
                    value = entry.number.value,
                    onValueChange = { viewModel.updateSmsNumber(index, it) },
                    label = { Text("Phone Number") },
                    modifier = Modifier.weight(1f)
                )
            }
        }

        item {
            Divider(color = Color.LightGray, thickness = 1.dp)
            Spacer(modifier = Modifier.height(12.dp))
            Text("Gateway Settings", style = MaterialTheme.typography.subtitle2)
        }

        item {
            OutlinedTextField(
                value = viewModel.smsConfig.gatewayUrl.value,
                onValueChange = viewModel::updateSmsGatewayUrl,
                label = { Text("SMS Gateway URL") },
                modifier = Modifier.fillMaxWidth()
            )
        }

        item {
            OutlinedTextField(
                value = viewModel.smsConfig.username.value,
                onValueChange = viewModel::updateSmsUsername,
                label = { Text("Username") },
                modifier = Modifier.fillMaxWidth()
            )
        }

        item {
            OutlinedTextField(
                value = viewModel.smsConfig.password.value,
                onValueChange = viewModel::updateSmsPassword,
                label = { Text("Password") },
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth()
            )
        }

        item {
            OutlinedTextField(
                value = viewModel.smsConfig.apiKey.value,
                onValueChange = viewModel::updateSmsApiKey,
                label = { Text("API Key") },
                modifier = Modifier.fillMaxWidth()
            )
        }

        item {
            OutlinedTextField(
                value = viewModel.smsConfig.senderId.value,
                onValueChange = viewModel::updateSmsSenderId,
                label = { Text("Sender ID") },
                modifier = Modifier.fillMaxWidth()
            )
        }

        item {
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Button(onClick = {
                    viewModel.saveSmsSettings(context)
                    Toast.makeText(context, "✅ SMS settings saved", Toast.LENGTH_SHORT).show()
                }) {
                    Text("Save SMS Settings")
                }

                Button(
                    onClick = { viewModel.sendTestSms(context) },
                    colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFF4CAF50), contentColor = Color.White)
                ) {
                    Text("Send Test SMS")
                }
            }
        }
    }
}
