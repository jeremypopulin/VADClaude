package com.example.visualduress

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
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
import com.example.visualduress.receiver.KioskAdminReceiver
import com.example.visualduress.ui.MainScreen
import com.example.visualduress.ui.theme.VisualAlertTheme
import com.example.visualduress.viewmodel.DeviceViewModel

// TODO: move into secure settings before shipping
private const val SERVICE_PIN = "3121"

class MainActivity : ComponentActivity() {

    private val viewModel: DeviceViewModel by viewModels()

    private lateinit var dpm: DevicePolicyManager
    private lateinit var admin: ComponentName

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        onBackPressedDispatcher.addCallback(this, backCallback)

        // Initialize ViewModel with application context
        viewModel.initWith(applicationContext)

        dpm = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        admin = ComponentName(this, KioskAdminReceiver::class.java)

        setContent {
            VisualAlertTheme {
                Box(Modifier.fillMaxSize()) {
                    MainScreen(viewModel = viewModel)
                    KioskExitCorner(
                        modifier = Modifier.align(Alignment.TopStart),
                        onUnlock = { exitKioskMode() },
                        onOpenSettings = { openAndroidSettings() },
                        onRemoveOwner = { removeDeviceOwner() }
                    )
                }
            }
        }
    }

    // Re-lock every time VAD comes back to the front
    override fun onResume() {
        super.onResume()
        enableKioskMode()
    }

    private fun enableKioskMode() {
        if (dpm.isDeviceOwnerApp(packageName)) {

            // Allow ONLY this app
            dpm.setLockTaskPackages(admin, arrayOf(packageName))

            // Hard lockdown
            dpm.setStatusBarDisabled(admin, true)
            dpm.setKeyguardDisabled(admin, true)

            // Force VAD as the home screen (no launcher picker)
            val homeFilter = IntentFilter(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_HOME)
                addCategory(Intent.CATEGORY_DEFAULT)
            }
            dpm.addPersistentPreferredActivity(
                admin, homeFilter, ComponentName(this, MainActivity::class.java)
            )

            startLockTask() // TRUE kiosk
        }
    }

    // Temporary release — VAD re-locks next time it comes to the front
    private fun exitKioskMode() {
        if (!dpm.isDeviceOwnerApp(packageName)) return
        dpm.setStatusBarDisabled(admin, false)
        dpm.setKeyguardDisabled(admin, false)
        stopLockTask()
        Toast.makeText(this, "Kiosk suspended — press Home to re-lock", Toast.LENGTH_LONG).show()
    }

    // Unlock and jump straight into Android Settings (network changes etc.)
    private fun openAndroidSettings() {
        exitKioskMode()
        startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    // Permanent release — removes Device Owner and home lock, no factory reset needed
    @Suppress("DEPRECATION")
    private fun removeDeviceOwner() {
        if (!dpm.isDeviceOwnerApp(packageName)) return
        exitKioskMode()
        dpm.clearPackagePersistentPreferredActivities(admin, packageName)
        dpm.setLockTaskPackages(admin, emptyArray())
        dpm.clearDeviceOwnerApp(packageName)
        Toast.makeText(this, "Device Owner removed — kiosk disabled", Toast.LENGTH_LONG).show()
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
        if (pin == SERVICE_PIN) { showDialog = false; action() } else error = true
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