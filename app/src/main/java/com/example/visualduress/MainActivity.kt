package com.example.visualduress

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import com.example.visualduress.receiver.KioskAdminReceiver
import com.example.visualduress.ui.MainScreen
import com.example.visualduress.ui.theme.VisualAlertTheme
import com.example.visualduress.viewmodel.DeviceViewModel

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

        enableKioskMode()

        setContent {
            VisualAlertTheme {
                MainScreen(viewModel = viewModel)
            }
        }
    }

    private fun enableKioskMode() {
        if (dpm.isDeviceOwnerApp(packageName)) {

            // Allow ONLY this app
            dpm.setLockTaskPackages(admin, arrayOf(packageName))

            // Hard lockdown
            dpm.setStatusBarDisabled(admin, true)
            dpm.setKeyguardDisabled(admin, true)

            startLockTask() // TRUE kiosk
        }
    }

    // Block back button using the modern OnBackPressedDispatcher
    private val backCallback = object : androidx.activity.OnBackPressedCallback(true) {
        override fun handleOnBackPressed() {
            // Do nothing — back button blocked in kiosk mode
        }
    }
}