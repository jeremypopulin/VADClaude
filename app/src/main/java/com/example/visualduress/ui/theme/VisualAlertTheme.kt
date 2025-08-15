package com.example.visualduress.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorPalette = darkColors(
    primary = RedAlert,
    primaryVariant = RedAlert,
    secondary = RedAlert,
    background = DarkGrayBackground,
    surface = DarkGrayBackground,
    onPrimary = Color.White,
    onSecondary = Color.White,
    onBackground = LightGrayText,
    onSurface = LightGrayText,
)

@Composable
fun VisualAlertTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colors = DarkColorPalette,
        typography = Typography,
        shapes = Shapes,
        content = content
    )
}
