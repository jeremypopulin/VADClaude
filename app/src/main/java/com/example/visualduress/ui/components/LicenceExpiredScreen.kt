package com.example.visualduress.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.visualduress.ui.theme.AccentOrange
import com.example.visualduress.ui.theme.DialogBackground
import com.example.visualduress.ui.theme.InputFieldBackground
import com.example.visualduress.ui.theme.TextPrimary
import com.example.visualduress.ui.theme.TextSecondary
import com.example.visualduress.util.LicenseManager
import com.example.visualduress.viewmodel.DeviceViewModel

/**
 * Full-screen locked state shown when the licence has been expired
 * for more than 30 days (past the grace period).
 *
 * The user can only enter a new licence key here — no other
 * part of the app is accessible until a valid key is entered.
 */
@Composable
fun LicenceExpiredScreen(
    viewModel: DeviceViewModel,
    onLicenceRenewed: () -> Unit
) {
    val context = LocalContext.current
    val deviceId = remember { LicenseManager.getDeviceId(context) }
    var licenceKey by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0D1520)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.6f)
                .background(DialogBackground, RoundedCornerShape(20.dp))
                .padding(40.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Lock icon
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .background(Color(0xFF3B1515), RoundedCornerShape(36.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Filled.Lock,
                    contentDescription = null,
                    tint = Color(0xFFE53935),
                    modifier = Modifier.size(36.dp)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                "Licence Expired",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                "Your licence has expired and the grace period has ended.\nMonitoring is suspended until a valid licence key is entered.",
                fontSize = 14.sp,
                color = TextSecondary,
                textAlign = TextAlign.Center,
                lineHeight = 22.sp
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Device ID
            Column(modifier = Modifier.fillMaxWidth()) {
                Text("Device ID", fontSize = 13.sp, color = TextSecondary, fontWeight = FontWeight.Medium)
                Spacer(modifier = Modifier.height(6.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(InputFieldBackground, RoundedCornerShape(10.dp))
                        .padding(14.dp)
                ) {
                    Text(deviceId, color = TextPrimary, fontSize = 14.sp)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Licence key input
            Column(modifier = Modifier.fillMaxWidth()) {
                Text("New Licence Key", fontSize = 13.sp, color = TextSecondary, fontWeight = FontWeight.Medium)
                Spacer(modifier = Modifier.height(6.dp))
                OutlinedTextField(
                    value = licenceKey,
                    onValueChange = { licenceKey = it; errorMessage = null },
                    placeholder = {
                        Text("Enter your new licence key", color = TextSecondary.copy(alpha = 0.5f), fontSize = 13.sp)
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = TextFieldDefaults.outlinedTextFieldColors(
                        backgroundColor = InputFieldBackground,
                        textColor = TextPrimary,
                        cursorColor = AccentOrange,
                        focusedBorderColor = if (errorMessage != null) Color(0xFFE53935) else AccentOrange,
                        unfocusedBorderColor = if (errorMessage != null) Color(0xFFE53935) else Color.Transparent
                    ),
                    isError = errorMessage != null
                )
                errorMessage?.let { err ->
                    Spacer(modifier = Modifier.height(6.dp))
                    Surface(color = Color(0x26E53935), shape = RoundedCornerShape(6.dp)) {
                        Text(
                            err, fontSize = 12.sp, color = Color(0xFFEF9A9A),
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            Button(
                onClick = {
                    val trimmed = licenceKey.trim()
                    if (trimmed.isEmpty()) {
                        errorMessage = "Please enter a licence key"
                        return@Button
                    }
                    if (LicenseManager.validateLicense(context, trimmed)) {
                        LicenseManager.saveLicense(context, trimmed)
                        viewModel.forceRefreshLicense(context) {
                            onLicenceRenewed()
                        }
                    } else {
                        errorMessage = "Invalid licence key. Please check and try again."
                    }
                },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                colors = ButtonDefaults.buttonColors(backgroundColor = AccentOrange, contentColor = Color.White),
                shape = RoundedCornerShape(26.dp),
                elevation = ButtonDefaults.elevation(defaultElevation = 4.dp)
            ) {
                Text("Activate New Licence", fontSize = 15.sp, fontWeight = FontWeight.Bold)
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                "Contact support@visualalertdisplay.com.au",
                fontSize = 12.sp,
                color = TextSecondary.copy(alpha = 0.6f),
                textAlign = TextAlign.Center
            )
        }
    }
}