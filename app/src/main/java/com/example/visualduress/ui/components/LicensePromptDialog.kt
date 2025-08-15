package com.example.visualduress.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.visualduress.util.LicenseManager

@Composable
fun LicensePromptDialog(onLicenseValidated: () -> Unit) {
    val context = LocalContext.current
    val deviceId = remember { LicenseManager.getDeviceId(context) }
    var licenseKey by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = {}, // forced
        title = { Text("Activate License") },
        text = {
            Column {
                Text("Device ID:")
                SelectionContainer {
                    Text(deviceId, style = MaterialTheme.typography.body2)
                }
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = licenseKey,
                    onValueChange = { licenseKey = it },
                    label = { Text("Enter License Key") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                errorMessage?.let {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(it, color = MaterialTheme.colors.error)
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                if (LicenseManager.validateLicense(context, licenseKey)) {
                    LicenseManager.saveLicense(context, licenseKey)
                    onLicenseValidated()
                } else {
                    errorMessage = "❌ Invalid license key"
                }
            }) {
                Text("Activate")
            }
        }
    )
}
