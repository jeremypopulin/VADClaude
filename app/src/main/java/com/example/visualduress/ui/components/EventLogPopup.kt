package com.example.visualduress.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
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
import com.example.visualduress.ui.theme.DialogBackground
import com.example.visualduress.ui.theme.EventItemBackground
import com.example.visualduress.ui.theme.HeaderBackground//AccentOrange
import com.example.visualduress.ui.theme.TextPrimary
import com.example.visualduress.ui.theme.TextSecondary
import com.example.visualduress.ui.theme.AccentOrange
import java.text.SimpleDateFormat
import java.util.*

// Color scheme matching the main theme


@Composable
fun EventLogPopup(
    log: List<EventLogEntry>,
    onClose: () -> Unit
) {
    val screenHeight = LocalConfiguration.current.screenHeightDp.dp
    val maxHeight = screenHeight * 0.9f
    val minHeight = screenHeight * 0.4f

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
                .background(DialogBackground, shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Drag Handle
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .padding(top = 12.dp, bottom = 16.dp)
                        .size(width = 48.dp, height = 5.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(Color.White.copy(alpha = 0.3f))
                )

                // Header with gradient background
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = HeaderBackground,
                    elevation = 4.dp
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Event Log",
                            fontSize = 24.sp,
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold
                        )
                        IconButton(
                            onClick = onClose,
                            modifier = Modifier
                                .size(40.dp)
                                .background(Color.White.copy(alpha = 0.1f), CircleShape)
                        ) {
                            Icon(
                                Icons.Filled.Close,
                                contentDescription = "Close",
                                tint = TextPrimary,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }

                // Event List
                if (log.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                "No Events Logged",
                                fontSize = 18.sp,
                                color = TextSecondary,
                                fontWeight = FontWeight.Medium
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                "Events will appear here when they occur",
                                fontSize = 14.sp,
                                color = TextSecondary.copy(alpha = 0.7f)
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(log.reversed()) { entry ->
                            EventLogItem(entry)
                        }

                        // Bottom spacing
                        item {
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EventLogItem(entry: EventLogEntry) {
    val formatter = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }
    val timeString = formatter.format(Date(entry.timestamp))

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = EventItemBackground,
        shape = RoundedCornerShape(12.dp),
        elevation = 2.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Time badge
            Surface(
                color = AccentOrange.copy(alpha = 0.2f),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    timeString,
                    fontSize = 13.sp,
                    color = AccentOrange,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Event message
            Text(
                entry.message,
                fontSize = 15.sp,
                color = TextPrimary,
                modifier = Modifier.weight(1f)
            )
        }
    }
}