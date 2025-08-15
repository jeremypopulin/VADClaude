package com.example.visualduress

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import com.example.visualduress.ui.MainScreen
import com.example.visualduress.ui.theme.VisualAlertTheme
import com.example.visualduress.viewmodel.DeviceViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: DeviceViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize ViewModel with application context
        viewModel.initWith(applicationContext)

        setContent {
            VisualAlertTheme {
                MainScreen(viewModel = viewModel)
            }
        }
    }
}
