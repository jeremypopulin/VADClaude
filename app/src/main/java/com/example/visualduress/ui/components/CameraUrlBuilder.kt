package com.example.visualduress.ui.components

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.visualduress.ui.theme.*

enum class CameraInputMode { BUILDER, MANUAL }

data class CameraBrand(
    val name: String,
    val defaultPort: Int = 554,
    val hasChannel: Boolean = true,
    val hasStream: Boolean = true,
    val streamLabels: List<String> = listOf("Main", "Sub"),
    val urlTemplate: (ip: String, port: Int, user: String, pass: String, channel: Int, stream: Int) -> String
)

val CAMERA_BRANDS = listOf(
    CameraBrand(
        name = "Hikvision",
        defaultPort = 554,
        streamLabels = listOf("Main", "Sub"),
        urlTemplate = { ip, port, user, pass, ch, stream ->
            val streamNum = stream + 1
            val auth = if (user.isNotEmpty()) "$user:$pass@" else ""
            "rtsp://${auth}${ip}:${port}/Streaming/Channels/${ch}0${streamNum}"
        }
    ),
    CameraBrand(
        name = "Dahua",
        defaultPort = 554,
        streamLabels = listOf("Main", "Sub"),
        urlTemplate = { ip, port, user, pass, ch, stream ->
            val auth = if (user.isNotEmpty()) "$user:$pass@" else ""
            "rtsp://${auth}${ip}:${port}/cam/realmonitor?channel=${ch}&subtype=${stream}"
        }
    ),
    CameraBrand(
        name = "Axis",
        defaultPort = 554,
        hasChannel = false,
        hasStream = false,
        urlTemplate = { ip, port, user, pass, _, _ ->
            val auth = if (user.isNotEmpty()) "$user:$pass@" else ""
            "rtsp://${auth}${ip}:${port}/axis-media/media.amp"
        }
    ),
    CameraBrand(
        name = "Hanwha / Samsung",
        defaultPort = 554,
        streamLabels = listOf("Profile 1", "Profile 2"),
        urlTemplate = { ip, port, user, pass, _, stream ->
            val auth = if (user.isNotEmpty()) "$user:$pass@" else ""
            "rtsp://${auth}${ip}:${port}/profile${stream + 1}/media.smp"
        }
    ),
    CameraBrand(
        name = "Uniview (UNV)",
        defaultPort = 554,
        streamLabels = listOf("Main", "Sub"),
        urlTemplate = { ip, port, user, pass, ch, _ ->
            val auth = if (user.isNotEmpty()) "$user:$pass@" else ""
            "rtsp://${auth}${ip}:${port}/media/video${ch}"
        }
    ),
    CameraBrand(
        name = "Reolink",
        defaultPort = 554,
        streamLabels = listOf("Main", "Sub"),
        urlTemplate = { ip, port, user, pass, ch, stream ->
            val auth = if (user.isNotEmpty()) "$user:$pass@" else ""
            val streamName = if (stream == 0) "main" else "sub"
            "rtsp://${auth}${ip}:${port}//h264Preview_0${ch}_${streamName}"
        }
    ),
    CameraBrand(
        name = "Bosch",
        defaultPort = 554,
        hasChannel = false,
        streamLabels = listOf("Primary", "Secondary"),
        urlTemplate = { ip, port, user, pass, _, stream ->
            val auth = if (user.isNotEmpty()) "$user:$pass@" else ""
            "rtsp://${auth}${ip}:${port}/video?inst=${stream + 1}"
        }
    ),
    CameraBrand(
        name = "Vivotek",
        defaultPort = 554,
        streamLabels = listOf("Stream 1", "Stream 2"),
        urlTemplate = { ip, port, user, pass, _, stream ->
            val auth = if (user.isNotEmpty()) "$user:$pass@" else ""
            "rtsp://${auth}${ip}:${port}/live${stream + 1}.sdp"
        }
    ),
    CameraBrand(
        name = "TVT",
        defaultPort = 554,
        streamLabels = listOf("Main", "Sub"),
        urlTemplate = { ip, port, user, pass, ch, stream ->
            val auth = if (user.isNotEmpty()) "$user:$pass@" else ""
            "rtsp://${auth}${ip}:${port}/tcp/av0_${ch - 1}_${stream}"
        }
    ),
    CameraBrand(
        name = "Generic / ONVIF",
        defaultPort = 554,
        hasChannel = false,
        hasStream = false,
        urlTemplate = { ip, port, user, pass, _, _ ->
            val auth = if (user.isNotEmpty()) "$user:$pass@" else ""
            "rtsp://${auth}${ip}:${port}/stream"
        }
    )
)

/** Parse existing URL back into builder fields so reopening settings repopulates correctly */
private data class ParsedUrl(
    val brandIndex: Int, val ip: String, val port: String,
    val username: String, val password: String,
    val channel: Int, val stream: Int
)

private fun parseRtspUrl(url: String): ParsedUrl? {
    if (url.isBlank()) return null
    return try {
        val authMatch = Regex("rtsp://([^:@]+):([^@]*)@").find(url)
        val username = authMatch?.groupValues?.get(1) ?: ""
        val password = authMatch?.groupValues?.get(2) ?: ""
        val ipMatch = if (authMatch != null)
            Regex("@([^:/]+):?(\\d+)?/").find(url)
        else
            Regex("rtsp://([^:/]+):?(\\d+)?/").find(url)
        val ip = ipMatch?.groupValues?.get(1) ?: return null
        val port = ipMatch.groupValues[2].ifEmpty { "554" }
        when {
            "realmonitor" in url -> {
                val ch = Regex("channel=(\\d+)").find(url)?.groupValues?.get(1)?.toIntOrNull() ?: 1
                val st = Regex("subtype=(\\d+)").find(url)?.groupValues?.get(1)?.toIntOrNull() ?: 0
                ParsedUrl(1, ip, port, username, password, ch, st)
            }
            "Streaming/Channels" in url -> {
                val m = Regex("/(\\d)(\\d)/").find(url)
                val ch = m?.groupValues?.get(1)?.toIntOrNull() ?: 1
                val st = (m?.groupValues?.get(2)?.toIntOrNull() ?: 1) - 1
                ParsedUrl(0, ip, port, username, password, ch, st)
            }
            "h264Preview" in url -> {
                val m = Regex("h264Preview_0(\\d+)_(main|sub)").find(url)
                val ch = m?.groupValues?.get(1)?.toIntOrNull() ?: 1
                val st = if (m?.groupValues?.get(2) == "sub") 1 else 0
                ParsedUrl(5, ip, port, username, password, ch, st)
            }
            "tcp/av0" in url -> {
                val m = Regex("av0_(\\d+)_(\\d+)").find(url)
                val ch = (m?.groupValues?.get(1)?.toIntOrNull() ?: 0) + 1
                val st = m?.groupValues?.get(2)?.toIntOrNull() ?: 0
                ParsedUrl(8, ip, port, username, password, ch, st)
            }
            "axis-media" in url -> ParsedUrl(2, ip, port, username, password, 1, 0)
            "media/video" in url -> {
                val ch = Regex("video(\\d+)").find(url)?.groupValues?.get(1)?.toIntOrNull() ?: 1
                ParsedUrl(4, ip, port, username, password, ch, 0)
            }
            "video?inst" in url -> {
                val st = Regex("inst=(\\d+)").find(url)?.groupValues?.get(1)?.toIntOrNull() ?: 1
                ParsedUrl(6, ip, port, username, password, 1, st - 1)
            }
            "live" in url && ".sdp" in url -> {
                val st = Regex("live(\\d+)\\.sdp").find(url)?.groupValues?.get(1)?.toIntOrNull() ?: 1
                ParsedUrl(7, ip, port, username, password, 1, st - 1)
            }
            else -> null
        }
    } catch (e: Exception) { null }
}

@Composable
fun CameraUrlBuilder(
    currentUrl: String,
    onUrlChanged: (String) -> Unit
) {
    val context = LocalContext.current

    // Parse existing URL to repopulate fields when reopening settings
    val parsed = remember(currentUrl) { parseRtspUrl(currentUrl) }

    var inputMode by remember(currentUrl) {
        mutableStateOf(if (parsed != null || currentUrl.isBlank()) CameraInputMode.BUILDER else CameraInputMode.MANUAL)
    }
    var selectedBrand by remember(currentUrl) { mutableStateOf(CAMERA_BRANDS[parsed?.brandIndex ?: 0]) }
    var ip by remember(currentUrl) { mutableStateOf(parsed?.ip ?: "") }
    var port by remember(currentUrl) { mutableStateOf(parsed?.port ?: selectedBrand.defaultPort.toString()) }
    var username by remember(currentUrl) { mutableStateOf(parsed?.username ?: "admin") }
    var password by remember(currentUrl) { mutableStateOf(parsed?.password ?: "") }
    var channel by remember(currentUrl) { mutableStateOf(parsed?.channel ?: 1) }
    var streamIndex by remember(currentUrl) { mutableStateOf(parsed?.stream ?: 0) }
    var showPassword by remember { mutableStateOf(false) }
    var brandDropdownExpanded by remember { mutableStateOf(false) }
    var manualUrl by remember(currentUrl) { mutableStateOf(currentUrl) }

    val generatedUrl by remember(selectedBrand, ip, port, username, password, channel, streamIndex) {
        derivedStateOf {
            if (ip.isBlank()) "" else {
                selectedBrand.urlTemplate(
                    ip.trim(),
                    port.toIntOrNull() ?: selectedBrand.defaultPort,
                    username.trim(),
                    password,
                    channel,
                    streamIndex
                )
            }
        }
    }

    LaunchedEffect(generatedUrl, inputMode, manualUrl) {
        val url = if (inputMode == CameraInputMode.BUILDER) generatedUrl else manualUrl
        if (url.isNotBlank()) onUrlChanged(url)
    }

    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {

        // Mode toggle
        Row(modifier = Modifier.fillMaxWidth().background(InputFieldBackground, RoundedCornerShape(10.dp)).padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            ModeToggleButton("Brand Builder", inputMode == CameraInputMode.BUILDER, Modifier.weight(1f)) { inputMode = CameraInputMode.BUILDER }
            ModeToggleButton("Manual URL", inputMode == CameraInputMode.MANUAL, Modifier.weight(1f)) { inputMode = CameraInputMode.MANUAL }
        }

        AnimatedVisibility(visible = inputMode == CameraInputMode.BUILDER,
            enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {

                // Brand selector
                Box {
                    Surface(modifier = Modifier.fillMaxWidth().clickable { brandDropdownExpanded = true },
                        color = InputFieldBackground, shape = RoundedCornerShape(10.dp)) {
                        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Column {
                                Text("Camera Brand", fontSize = 11.sp, color = TextSecondary.copy(alpha = 0.7f))
                                Text(selectedBrand.name, fontSize = 15.sp, color = TextPrimary, fontWeight = FontWeight.Medium)
                            }
                            Icon(Icons.Filled.ArrowDropDown, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(24.dp))
                        }
                    }
                    DropdownMenu(expanded = brandDropdownExpanded, onDismissRequest = { brandDropdownExpanded = false },
                        modifier = Modifier.background(Color(0xFF1A2535))) {
                        CAMERA_BRANDS.forEach { brand ->
                            DropdownMenuItem(onClick = {
                                selectedBrand = brand
                                port = brand.defaultPort.toString()
                                streamIndex = 0
                                brandDropdownExpanded = false
                            }) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    if (brand == selectedBrand) Icon(Icons.Filled.Check, null, tint = AccentOrange, modifier = Modifier.size(16.dp))
                                    else Spacer(Modifier.size(16.dp))
                                    Text(brand.name, color = if (brand == selectedBrand) AccentOrange else TextPrimary, fontSize = 14.sp)
                                }
                            }
                        }
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CamTextField("IP Address", ip, { ip = it }, "192.168.0.100", Modifier.weight(2f), KeyboardType.Uri)
                    CamTextField("Port", port, { port = it.filter { c -> c.isDigit() } }, "554", Modifier.weight(1f), KeyboardType.Number)
                }

                if (selectedBrand.hasChannel || selectedBrand.hasStream) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (selectedBrand.hasChannel) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Channel", fontSize = 11.sp, color = TextSecondary.copy(alpha = 0.7f), modifier = Modifier.padding(bottom = 4.dp))
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    StepperButton(Icons.Filled.Remove) { if (channel > 1) channel-- }
                                    Text("$channel", fontSize = 16.sp, color = TextPrimary, fontWeight = FontWeight.Bold,
                                        modifier = Modifier.width(28.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                                    StepperButton(Icons.Filled.Add) { if (channel < 32) channel++ }
                                }
                            }
                        }
                        if (selectedBrand.hasStream) {
                            Column(modifier = Modifier.weight(2f)) {
                                Text("Stream", fontSize = 11.sp, color = TextSecondary.copy(alpha = 0.7f), modifier = Modifier.padding(bottom = 4.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    selectedBrand.streamLabels.forEachIndexed { index, label ->
                                        StreamChip(label, streamIndex == index) { streamIndex = index }
                                    }
                                }
                            }
                        }
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CamTextField("Username", username, { username = it }, "admin", Modifier.weight(1f))
                    CamTextField("Password", password, { password = it }, "••••••", Modifier.weight(1f),
                        isPassword = true, showPassword = showPassword, onTogglePassword = { showPassword = !showPassword })
                }

                Surface(color = Color(0xFF1A2535), shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
                    Row(modifier = Modifier.padding(10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
                        Icon(Icons.Filled.Info, null, tint = AccentOrange, modifier = Modifier.size(14.dp).padding(top = 2.dp))
                        Text("For NVR systems — connect to the NVR IP and set the channel number to the camera port on the NVR.",
                            fontSize = 11.sp, color = TextSecondary.copy(alpha = 0.8f), lineHeight = 16.sp)
                    }
                }

                if (generatedUrl.isNotBlank()) {
                    GeneratedUrlBox(url = generatedUrl, onCopy = {
                        val cb = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                        cb.setPrimaryClip(android.content.ClipData.newPlainText("RTSP URL", generatedUrl))
                        Toast.makeText(context, "URL copied", Toast.LENGTH_SHORT).show()
                    })
                } else {
                    Surface(color = Color(0xFF1A2535), shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
                        Row(modifier = Modifier.padding(10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Info, null, tint = TextSecondary.copy(alpha = 0.5f), modifier = Modifier.size(14.dp))
                            Text("Enter the camera IP address to generate the RTSP URL", fontSize = 11.sp, color = TextSecondary.copy(alpha = 0.6f))
                        }
                    }
                }
            }
        }

        AnimatedVisibility(visible = inputMode == CameraInputMode.MANUAL,
            enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = manualUrl, onValueChange = { manualUrl = it; onUrlChanged(it) },
                    label = { Text("RTSP Stream URL", fontSize = 12.sp, color = TextSecondary) },
                    placeholder = { Text("rtsp://user:pass@192.168.0.100:554/stream", color = TextSecondary.copy(alpha = 0.4f), fontSize = 12.sp) },
                    singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp),
                    colors = TextFieldDefaults.outlinedTextFieldColors(backgroundColor = InputFieldBackground, textColor = TextPrimary,
                        cursorColor = AccentOrange, focusedBorderColor = AccentOrange, unfocusedBorderColor = Color.Transparent))
                Text("Use this for cameras not listed above, or if you know the exact URL.",
                    fontSize = 11.sp, color = TextSecondary.copy(alpha = 0.6f))
            }
        }
    }
}

@Composable
private fun ModeToggleButton(label: String, isActive: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(modifier = modifier.clip(RoundedCornerShape(8.dp))
        .background(if (isActive) AccentOrange else Color.Transparent)
        .clickable(onClick = onClick).padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
        Text(label, fontSize = 13.sp, fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal,
            color = if (isActive) Color.White else TextSecondary)
    }
}

@Composable
private fun CamTextField(label: String, value: String, onValueChange: (String) -> Unit, placeholder: String,
                         modifier: Modifier = Modifier, keyboardType: KeyboardType = KeyboardType.Text,
                         isPassword: Boolean = false, showPassword: Boolean = false, onTogglePassword: (() -> Unit)? = null) {
    Column(modifier = modifier) {
        Text(label, fontSize = 11.sp, color = TextSecondary.copy(alpha = 0.7f), modifier = Modifier.padding(bottom = 4.dp))
        OutlinedTextField(value = value, onValueChange = onValueChange,
            placeholder = { Text(placeholder, color = TextSecondary.copy(alpha = 0.4f), fontSize = 12.sp) },
            singleLine = true,
            visualTransformation = if (isPassword && !showPassword) PasswordVisualTransformation() else VisualTransformation.None,
            modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp),
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            colors = TextFieldDefaults.outlinedTextFieldColors(backgroundColor = InputFieldBackground, textColor = TextPrimary,
                cursorColor = AccentOrange, focusedBorderColor = AccentOrange, unfocusedBorderColor = Color.Transparent),
            trailingIcon = if (isPassword && onTogglePassword != null) {
                { IconButton(onClick = onTogglePassword, modifier = Modifier.size(32.dp)) {
                    Icon(if (showPassword) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                        null, tint = TextSecondary, modifier = Modifier.size(16.dp)) } }
            } else null)
    }
}

@Composable
private fun StepperButton(icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Box(modifier = Modifier.size(32.dp).background(InputFieldBackground, RoundedCornerShape(6.dp)).clickable(onClick = onClick),
        contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = TextPrimary, modifier = Modifier.size(16.dp))
    }
}

@Composable
private fun StreamChip(label: String, isSelected: Boolean, onClick: () -> Unit) {
    Box(modifier = Modifier.clip(RoundedCornerShape(6.dp))
        .background(if (isSelected) AccentOrange.copy(alpha = 0.2f) else InputFieldBackground)
        .border(if (isSelected) 1.dp else 0.dp, if (isSelected) AccentOrange else Color.Transparent, RoundedCornerShape(6.dp))
        .clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 6.dp), contentAlignment = Alignment.Center) {
        Text(label, fontSize = 12.sp, color = if (isSelected) AccentOrange else TextSecondary,
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal)
    }
}

@Composable
private fun GeneratedUrlBox(url: String, onCopy: () -> Unit) {
    Surface(color = Color(0xFF0D1F2D), shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(modifier = Modifier.size(8.dp).background(Color(0xFF4CAF50), CircleShape))
                    Text("Generated URL", fontSize = 11.sp, color = Color(0xFF81C784), fontWeight = FontWeight.SemiBold)
                }
                IconButton(onClick = onCopy, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Filled.ContentCopy, "Copy URL", tint = TextSecondary, modifier = Modifier.size(14.dp))
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(url, fontSize = 11.sp, color = TextPrimary.copy(alpha = 0.9f),
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                overflow = TextOverflow.Ellipsis, maxLines = 2)
        }
    }
}