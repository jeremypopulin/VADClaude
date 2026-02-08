package com.example.visualduress.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun ModernSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float> = 30f..250f,
    modifier: Modifier = Modifier,
    activeTrackColor: Color = Color(0xFF4A90E2),
    inactiveTrackColor: Color = Color(0xFF2E3440),
    thumbColor: Color = Color(0xFF5A6F83),
    thumbIconRes: Int? = null // Optional custom icon for thumb
) {
    var sliderWidth by remember { mutableStateOf(0f) }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(40.dp)
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .align(Alignment.CenterStart)
                .pointerInput(Unit) {
                    detectDragGestures { change, _ ->
                        change.consume()
                        val newValue = ((change.position.x / sliderWidth) *
                                (valueRange.endInclusive - valueRange.start) + valueRange.start)
                            .coerceIn(valueRange.start, valueRange.endInclusive)
                        onValueChange(newValue)
                    }
                }
        ) {
            sliderWidth = size.width

            // Calculate progress
            val progress = (value - valueRange.start) / (valueRange.endInclusive - valueRange.start)
            val activeWidth = size.width * progress

            // Draw inactive track
            drawLine(
                color = inactiveTrackColor,
                start = Offset(0f, size.height / 2),
                end = Offset(size.width, size.height / 2),
                strokeWidth = size.height,
                cap = StrokeCap.Round
            )

            // Draw active track
            drawLine(
                color = activeTrackColor,
                start = Offset(0f, size.height / 2),
                end = Offset(activeWidth, size.height / 2),
                strokeWidth = size.height,
                cap = StrokeCap.Round
            )
        }

        // Thumb (draggable circle with icon)
        val progress = (value - valueRange.start) / (valueRange.endInclusive - valueRange.start)

        Box(
            modifier = Modifier
                .offset(x = ((sliderWidth - 32.dp.toPx()) * progress).dp)
                .size(32.dp)
                .background(thumbColor, CircleShape)
                .align(Alignment.CenterStart),
            contentAlignment = Alignment.Center
        ) {
            if (thumbIconRes != null) {
                Icon(
                    painter = painterResource(id = thumbIconRes),
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(16.dp)
                )
            } else {
                // Default pause icon
                Icon(
                    imageVector = Icons.Filled.Pause,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

// Extension function to convert Dp to pixels
@Composable
private fun Dp.toPx(): Float {
    val density = androidx.compose.ui.platform.LocalDensity.current
    return with(density) { this@toPx.toPx() }
}