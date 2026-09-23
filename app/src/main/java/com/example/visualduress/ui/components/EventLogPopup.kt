package com.example.visualduress.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.visualduress.model.EventLogEntry
import com.example.visualduress.ui.theme.DialogBackground
import com.example.visualduress.ui.theme.HeaderBackground
import com.example.visualduress.ui.theme.TextPrimary
import com.example.visualduress.ui.theme.TextSecondary
import java.text.SimpleDateFormat
import java.util.*

// ─────────────────────────────────────────────────────────────────────────────
// Event types — worked out from the start of each log message
// ─────────────────────────────────────────────────────────────────────────────

private enum class EventKind(val label: String, val color: Color) {
    ALARM("Alarm", Color(0xFFFF4B4B)),
    RESET("Reset", Color(0xFF3ECF8E)),
    OVERRIDE("Override", Color(0xFFF5B942)),
    CONNECTION("Connection", Color(0xFF8FA3C2)),
    SMS("SMS", Color(0xFF5AA9FF)),
    SYSTEM("System", Color(0xFF8A93A6))
}

private data class ParsedEvent(val kind: EventKind, val text: String)

private fun parse(message: String): ParsedEvent {
    val m = message.trim()
    val kind = when {
        m.startsWith("🚨") -> EventKind.ALARM
        m.startsWith("✅") -> EventKind.RESET
        m.startsWith("⚠") || m.startsWith("🔕") -> EventKind.OVERRIDE
        m.startsWith("❌") || m.startsWith("ℹ") -> EventKind.CONNECTION
        m.startsWith("📤") -> EventKind.SMS
        else -> EventKind.SYSTEM
    }
    // Drop the leading emoji — the coloured tag does that job now
    val text = m.dropWhile { !it.isLetterOrDigit() }.ifBlank { m }
    return ParsedEvent(kind, text)
}

private enum class Filter(val label: String, val kinds: Set<EventKind>?) {
    ALL("All", null),
    ALARMS("Alarms", setOf(EventKind.ALARM, EventKind.OVERRIDE)),
    RESETS("Resets", setOf(EventKind.RESET)),
    SYSTEM("System", setOf(EventKind.CONNECTION, EventKind.SMS, EventKind.SYSTEM))
}

// ─────────────────────────────────────────────────────────────────────────────
// Popup
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun EventLogPopup(
    log: List<EventLogEntry>,
    onClose: () -> Unit
) {
    var filter by remember { mutableStateOf(Filter.ALL) }

    val entries = remember(log.size, log.firstOrNull()?.timestamp, filter) {
        log.sortedByDescending { it.timestamp }
            .map { it to parse(it.message) }
            .filter { (_, p) -> filter.kinds?.contains(p.kind) ?: true }
    }

    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.85f)
                .fillMaxHeight(0.85f),
            color = DialogBackground,
            shape = RoundedCornerShape(16.dp),
            elevation = 8.dp
        ) {
            Column(modifier = Modifier.fillMaxSize()) {

                // Header
                Surface(color = HeaderBackground, elevation = 2.dp) {
                    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Event Log", fontSize = 24.sp, color = TextPrimary, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                "${log.size} event${if (log.size == 1) "" else "s"}",
                                fontSize = 14.sp,
                                color = TextSecondary
                            )
                            Spacer(modifier = Modifier.weight(1f))
                            IconButton(
                                onClick = onClose,
                                modifier = Modifier
                                    .size(44.dp)
                                    .background(Color.White.copy(alpha = 0.1f), CircleShape)
                            ) {
                                Icon(Icons.Filled.Close, contentDescription = "Close", tint = TextPrimary)
                            }
                        }
                        Spacer(modifier = Modifier.height(14.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Filter.values().forEach { f ->
                                FilterChip(label = f.label, selected = filter == f) { filter = f }
                            }
                        }
                    }
                }

                if (entries.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                if (log.isEmpty()) "No events logged" else "No ${filter.label.lowercase()} events",
                                fontSize = 18.sp, color = TextSecondary, fontWeight = FontWeight.Medium
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Events appear here as they happen", fontSize = 14.sp, color = TextSecondary.copy(alpha = 0.7f))
                        }
                    }
                } else {
                    val dayFmt = remember { SimpleDateFormat("EEE d MMM yyyy", Locale.getDefault()) }
                    val timeFmt = remember { SimpleDateFormat("h:mm:ss a", Locale.getDefault()) }

                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp)
                    ) {
                        var lastDay = ""
                        entries.forEach { (entry, parsed) ->
                            val day = dayLabel(entry.timestamp, dayFmt)
                            if (day != lastDay) {
                                lastDay = day
                                item { DayHeader(day) }
                            }
                            item {
                                EventRow(timeFmt.format(Date(entry.timestamp)).lowercase(), parsed)
                            }
                        }
                        item { Spacer(modifier = Modifier.height(8.dp)) }
                    }
                }
            }
        }
    }
}

private fun dayLabel(ts: Long, fmt: SimpleDateFormat): String {
    val cal = Calendar.getInstance().apply { timeInMillis = ts }
    val today = Calendar.getInstance()
    val yesterday = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }
    fun same(a: Calendar, b: Calendar) =
        a.get(Calendar.YEAR) == b.get(Calendar.YEAR) && a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR)
    return when {
        same(cal, today) -> "Today"
        same(cal, yesterday) -> "Yesterday"
        else -> fmt.format(Date(ts))
    }
}

@Composable
private fun FilterChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .background(
                if (selected) Color(0xFFF88107) else Color.White.copy(alpha = 0.08f),
                RoundedCornerShape(18.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 8.dp)
    ) {
        Text(
            label,
            color = if (selected) Color.White else TextSecondary,
            fontSize = 14.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
        )
    }
}

@Composable
private fun DayHeader(day: String) {
    Text(
        day.uppercase(),
        fontSize = 12.sp,
        letterSpacing = 1.sp,
        color = TextSecondary,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = 14.dp, bottom = 6.dp)
    )
}

@Composable
private fun EventRow(time: String, event: ParsedEvent) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .background(Color.White.copy(alpha = 0.04f), RoundedCornerShape(8.dp))
            .height(IntrinsicSize.Min),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // coloured edge
        Box(
            modifier = Modifier
                .width(4.dp)
                .fillMaxHeight()
                .background(event.kind.color, RoundedCornerShape(topStart = 8.dp, bottomStart = 8.dp))
        )
        Text(
            time,
            fontSize = 14.sp,
            color = TextSecondary,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.width(110.dp).padding(start = 14.dp)
        )
        // type tag
        Box(
            modifier = Modifier
                .width(96.dp)
                .padding(vertical = 10.dp)
        ) {
            Text(
                event.kind.label.uppercase(),
                fontSize = 11.sp,
                letterSpacing = 0.8.sp,
                fontWeight = FontWeight.Bold,
                color = event.kind.color,
                modifier = Modifier
                    .background(event.kind.color.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            )
        }
        Text(
            event.text,
            fontSize = 16.sp,
            color = TextPrimary,
            fontWeight = if (event.kind == EventKind.ALARM) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).padding(end = 14.dp, top = 10.dp, bottom = 10.dp)
        )
    }
}