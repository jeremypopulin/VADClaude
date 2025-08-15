package com.example.visualduress.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.visualduress.model.EventLogEntry
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun EventLogPopup(
    log: List<EventLogEntry>,
    onClose: () -> Unit
) {
    val screenHeight = LocalConfiguration.current.screenHeightDp.dp
    val maxHeight = screenHeight * 0.95f
    val minHeight = screenHeight * 0.3f

    var currentHeight by remember { mutableStateOf(minHeight) }

    Dialog(onDismissRequest = onClose) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(currentHeight)
                .pointerInput(Unit) {
                    detectVerticalDragGestures { _, dragAmount ->
                        currentHeight = (currentHeight - dragAmount.dp).coerceIn(minHeight, maxHeight)
                    }
                }
                .background(Color.Black, shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                .padding(12.dp)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Drag Handle
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .padding(bottom = 8.dp)
                        .size(width = 40.dp, height = 6.dp)
                        .clip(CircleShape)
                        .background(Color.Gray.copy(alpha = 0.7f))
                )

                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Event Log", fontSize = 20.sp, color = Color.White, fontWeight = FontWeight.Bold)
                    TextButton(onClick = onClose) {
                        Text("X", color = Color.White, fontSize = 18.sp)
                    }
                }

                Divider(modifier = Modifier.padding(vertical = 6.dp), color = Color.DarkGray)

                Column(modifier = Modifier.fillMaxSize().padding(end = 4.dp)) {
                    if (log.isEmpty()) {
                        Text("No events logged.", fontSize = 16.sp, color = Color.White)
                    } else {
                        val formatter = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }
                        log.reversed().forEach {
                            Text(
                                "- ${formatter.format(Date(it.timestamp))}: ${it.message}",
                                fontSize = 14.sp,
                                color = Color.White
                            )
                        }
                    }
                }
            }
        }
    }
}
