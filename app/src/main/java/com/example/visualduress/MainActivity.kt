package com.example.visualduress

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.AlertDialog
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.visualduress.ui.MainScreen
import com.example.visualduress.ui.theme.VisualAlertTheme
import com.example.visualduress.util.KioskManager
import com.example.visualduress.viewmodel.DeviceViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: DeviceViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        onBackPressedDispatcher.addCallback(this, backCallback)

        // Initialize ViewModel with application context
        viewModel.initWith(applicationContext)

        setContent {
            VisualAlertTheme {
                Box(Modifier.fillMaxSize()) {
                    MainScreen(viewModel = viewModel)
                    // Emergency fallback: hidden corner still works if Settings can't be reached
                    KioskExitCorner(
                        modifier = Modifier.align(Alignment.TopStart),
                        onUnlock = { KioskManager.exit(this@MainActivity) },
                        onOpenSettings = { KioskManager.openAndroidSettings(this@MainActivity) },
                        onRemoveOwner = { KioskManager.removeDeviceOwner(this@MainActivity) }
                    )
                }
            }
        }
    }

    // Re-lock every time VAD comes back to the front (only if Device Owner + kiosk enabled)
    override fun onResume() {
        super.onResume()
        KioskManager.enable(this)
    }

    // Block back button using the modern OnBackPressedDispatcher
    private val backCallback = object : androidx.activity.OnBackPressedCallback(true) {
        override fun handleOnBackPressed() {
            // Do nothing — back button blocked in kiosk mode
        }
    }
}

// Invisible 80dp tap zone: 5 quick taps opens the service PIN prompt
@Composable
private fun KioskExitCorner(
    modifier: Modifier,
    onUnlock: () -> Unit,
    onOpenSettings: () -> Unit,
    onRemoveOwner: () -> Unit
) {
    var tapCount by remember { mutableStateOf(0) }
    var lastTap by remember { mutableStateOf(0L) }
    var showDialog by remember { mutableStateOf(false) }
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }

    fun ifPinOk(action: () -> Unit) {
        if (pin == KioskManager.SERVICE_PIN) { showDialog = false; action() } else error = true
    }

    Box(
        modifier = modifier
            .size(80.dp)
            .pointerInput(Unit) {
                detectTapGestures {
                    val now = System.currentTimeMillis()
                    tapCount = if (now - lastTap > 1500) 1 else tapCount + 1
                    lastTap = now
                    if (tapCount >= 5) {
                        tapCount = 0
                        pin = ""
                        error = false
                        showDialog = true
                    }
                }
            }
    )

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text("Service access") },
            text = {
                Column {
                    OutlinedTextField(
                        value = pin,
                        onValueChange = { pin = it; error = false },
                        label = { Text("Service PIN") },
                        singleLine = true,
                        isError = error,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword)
                    )
                    if (error) Text("Incorrect PIN")
                    TextButton(onClick = { ifPinOk(onOpenSettings) }) {
                        Text("Android Settings")
                    }
                    TextButton(onClick = { ifPinOk(onRemoveOwner) }) {
                        Text("Remove Device Owner (decommission)")
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { ifPinOk(onUnlock) }) { Text("Exit kiosk") }
            },
            dismissButton = {
                TextButton(onClick = { showDialog = false }) { Text("Cancel") }
            }
        )
    }
}