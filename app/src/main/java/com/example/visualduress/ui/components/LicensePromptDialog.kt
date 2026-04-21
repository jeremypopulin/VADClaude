package com.example.visualduress.ui.components

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.visualduress.ui.theme.AccentOrange
import com.example.visualduress.ui.theme.CardBackground
import com.example.visualduress.ui.theme.DialogBackground
import com.example.visualduress.ui.theme.ErrorRed
import com.example.visualduress.ui.theme.InputFieldBackground
import com.example.visualduress.ui.theme.TextPrimary
import com.example.visualduress.ui.theme.TextSecondary
import com.example.visualduress.util.LicenseManager

@Composable
fun LicensePromptDialog(
    viewModel: com.example.visualduress.viewmodel.DeviceViewModel,
    onLicenseValidated: () -> Unit) {
    val context = LocalContext.current
    val deviceId = remember { LicenseManager.getDeviceId(context) }
    var licenseKey by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    Dialog(
        onDismissRequest = {},
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)
    ) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = DialogBackground,
            elevation = 8.dp,
            modifier = Modifier.fillMaxWidth(0.95f)
        ) {
            Column(
                modifier = Modifier.padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Activate Licence",
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Please enter your licence key to continue",
                    fontSize = 14.sp,
                    color = TextSecondary,
                    fontWeight = FontWeight.Normal
                )

                Spacer(modifier = Modifier.height(24.dp))

                Column(modifier = Modifier.fillMaxWidth()) {
                    Text("Device ID", fontSize = 14.sp, color = TextSecondary, fontWeight = FontWeight.Medium)
                    Spacer(modifier = Modifier.height(6.dp))
                    SelectionContainer {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(CardBackground, RoundedCornerShape(12.dp))
                                .padding(16.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.Person, contentDescription = null,
                                    tint = TextPrimary.copy(alpha = 0.6f), modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(deviceId, fontSize = 15.sp, color = TextPrimary, fontWeight = FontWeight.Normal)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                Column(modifier = Modifier.fillMaxWidth()) {
                    Text("Licence Key", fontSize = 14.sp, color = TextSecondary, fontWeight = FontWeight.Medium)
                    Spacer(modifier = Modifier.height(6.dp))
                    OutlinedTextField(
                        value = licenseKey,
                        onValueChange = { licenseKey = it; errorMessage = null },
                        placeholder = {
                            Text("Enter your licence key", color = TextSecondary.copy(alpha = 0.6f), fontSize = 14.sp)
                        },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = TextFieldDefaults.outlinedTextFieldColors(
                            backgroundColor = InputFieldBackground,
                            textColor = TextPrimary,
                            cursorColor = TextPrimary,
                            focusedBorderColor = if (errorMessage != null) ErrorRed else Color.Transparent,
                            unfocusedBorderColor = if (errorMessage != null) ErrorRed else Color.Transparent,
                            errorBorderColor = ErrorRed,
                            placeholderColor = TextSecondary.copy(alpha = 0.6f)
                        ),
                        leadingIcon = {
                            Icon(Icons.Filled.Lock, contentDescription = null,
                                tint = if (errorMessage != null) ErrorRed else TextPrimary.copy(alpha = 0.6f),
                                modifier = Modifier.size(20.dp))
                        },
                        isError = errorMessage != null
                    )
                    errorMessage?.let { error ->
                        Spacer(modifier = Modifier.height(8.dp))
                        Surface(color = ErrorRed.copy(alpha = 0.15f), shape = RoundedCornerShape(8.dp)) {
                            Text(error, fontSize = 13.sp, color = ErrorRed, fontWeight = FontWeight.Medium,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp))
                        }
                    }
                }

                Spacer(modifier = Modifier.height(28.dp))

                Button(
                    onClick = {
                        val trimmedKey = licenseKey.trim()
                        if (trimmedKey.isEmpty()) {
                            errorMessage = "Please enter a licence key"
                            return@Button
                        }
                        if (LicenseManager.validateLicense(context, trimmedKey)) {
                            LicenseManager.saveLicense(context, trimmedKey)
                            viewModel.forceRefreshLicense(context) {
                                onLicenseValidated()
                            }
                        } else {
                            errorMessage = "Invalid licence key. Please check and try again."
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    colors = ButtonDefaults.buttonColors(backgroundColor = AccentOrange, contentColor = Color.White),
                    shape = RoundedCornerShape(26.dp),
                    elevation = ButtonDefaults.elevation(defaultElevation = 4.dp, pressedElevation = 6.dp)
                ) {
                    Text("Activate Licence", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    "Contact support if you need assistance",
                    fontSize = 12.sp,
                    color = TextSecondary.copy(alpha = 0.7f),
                    fontWeight = FontWeight.Normal
                )
            }
        }
    }
}