package com.example.visualduress.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.Icon
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.example.visualduress.R

@Composable
fun AdvancedModernSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float> = 0f..100f,
    modifier: Modifier = Modifier,
    activeTrackColor: Color = Color(0xFF4A90E2),
    inactiveTrackColor: Color = Color(0xFF2E3440),
    thumbColor: Color = Color(0xFF5A6F83),
    thumbIconRes: Int = R.drawable.ic_pause
) {
    var sliderSize by remember { mutableStateOf(IntSize.Zero) }
    val density = LocalDensity.current

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(40.dp)
            .onSizeChanged { sliderSize = it }
    ) {
        // Track
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .align(Alignment.CenterStart)
                .pointerInput(Unit) {
                    detectHorizontalDragGestures { change, _ ->
                        change.consume()

                        val sliderWidthPx = sliderSize.width.toFloat()
                        val thumbRadiusPx = with(density) { 16.dp.toPx() }

                        // Calculate position accounting for thumb radius
                        val effectiveWidth = sliderWidthPx - (thumbRadiusPx * 2)
                        val position = (change.position.x - thumbRadiusPx).coerceIn(0f, effectiveWidth)

                        val fraction = if (effectiveWidth > 0) position / effectiveWidth else 0f
                        val newValue = valueRange.start +
                                (fraction * (valueRange.endInclusive - valueRange.start))

                        onValueChange(newValue.coerceIn(valueRange.start, valueRange.endInclusive))
                    }
                }
        ) {
            // Calculate progress
            val progress = ((value - valueRange.start) /
                    (valueRange.endInclusive - valueRange.start)).coerceIn(0f, 1f)
            val activeWidth = size.width * progress

            // Draw inactive track (full width)
            drawLine(
                color = inactiveTrackColor,
                start = Offset(0f, size.height / 2),
                end = Offset(size.width, size.height / 2),
                strokeWidth = size.height,
                cap = StrokeCap.Round
            )

            // Draw active track (up to current value)
            if (activeWidth > 0) {
                drawLine(
                    color = activeTrackColor,
                    start = Offset(0f, size.height / 2),
                    end = Offset(activeWidth, size.height / 2),
                    strokeWidth = size.height,
                    cap = StrokeCap.Round
                )
            }
        }

        // Thumb
        val progress = ((value - valueRange.start) /
                (valueRange.endInclusive - valueRange.start)).coerceIn(0f, 1f)

        val thumbOffsetPx = with(density) {
            val sliderWidthPx = sliderSize.width.toFloat()
            val thumbRadiusPx = 16.dp.toPx()
            val effectiveWidth = sliderWidthPx - (thumbRadiusPx * 2)
            (effectiveWidth * progress).dp
        }

        Box(
            modifier = Modifier
                .offset(x = thumbOffsetPx)
                .size(32.dp)
                .background(thumbColor, CircleShape)
                .align(Alignment.CenterStart),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(id = thumbIconRes),
                contentDescription = "Slider thumb",
                tint = Color.White,
                modifier = Modifier.size(14.dp)
            )
        }
    }
}