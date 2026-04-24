package com.example.visualduress.ui.components

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.example.visualduress.ui.theme.AccentOrange
import com.example.visualduress.ui.theme.PreviewBackground
import com.example.visualduress.ui.theme.TextPrimary

@Composable
fun CameraPreviewPopup(
    streamUrl: String,
    onClose: () -> Unit,
    onTap: () -> Unit
) {
    val context = LocalContext.current

    // Use sub stream for the small popup — less bandwidth, faster to load
    val previewUrl = remember(streamUrl) {
        when {
            "realmonitor" in streamUrl ->
                streamUrl.replace(Regex("subtype=\\d"), "subtype=1")
            "Streaming/Channels" in streamUrl ->
                streamUrl.replace(Regex("(\\d)(0)(1)$"), "$10${"2"}")
            "h264Preview" in streamUrl ->
                streamUrl.replace("_main", "_sub")
            "profile" in streamUrl && "media.smp" in streamUrl ->
                streamUrl.replace(Regex("profile\\d+"), "profile2")
            "tcp/av0" in streamUrl ->
                streamUrl.replace(Regex("av0_(\\d+)_0")) { mr -> "av0_${mr.groupValues[1]}_1" }
            else -> streamUrl
        }
    }

    val player = remember {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(
                MediaItem.Builder()
                    .setUri(Uri.parse(previewUrl))
                    .setMimeType("application/x-rtsp")
                    .build()
            )
            playWhenReady = true
            prepare()
        }
    }

    DisposableEffect(Unit) {
        onDispose { player.release() }
    }

    Surface(
        modifier = Modifier.width(320.dp).height(200.dp),
        shape = RoundedCornerShape(16.dp),
        elevation = 8.dp,
        color = PreviewBackground
    ) {
        Box(modifier = Modifier.fillMaxSize().clickable { onTap() }) {
            Box(modifier = Modifier.fillMaxSize().padding(8.dp).clip(RoundedCornerShape(12.dp)).background(Color.Black)) {
                AndroidView(factory = { PlayerView(it).apply { this.player = player; useController = false } },
                    modifier = Modifier.fillMaxSize())
                Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center) {
                    Surface(color = AccentOrange.copy(alpha = 0.8f), shape = CircleShape, modifier = Modifier.size(48.dp)) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Filled.PlayArrow, "Tap to view fullscreen", tint = Color.White, modifier = Modifier.size(28.dp))
                        }
                    }
                }
            }
            Surface(modifier = Modifier.align(Alignment.TopEnd).padding(12.dp),
                shape = CircleShape, color = Color.Black.copy(alpha = 0.6f), elevation = 4.dp) {
                IconButton(onClick = onClose, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.Close, "Close", tint = TextPrimary, modifier = Modifier.size(20.dp))
                }
            }
            Surface(modifier = Modifier.align(Alignment.TopStart).padding(12.dp),
                shape = RoundedCornerShape(8.dp), color = Color.Red.copy(alpha = 0.8f)) {
                Row(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(modifier = Modifier.size(8.dp).background(Color.White, CircleShape))
                    Text("LIVE", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }
            }
            Box(modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(40.dp)
                .background(androidx.compose.ui.graphics.Brush.verticalGradient(
                    colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.6f)))))
            Text("Tap to expand", fontSize = 11.sp, color = TextPrimary.copy(alpha = 0.8f),
                fontWeight = FontWeight.Medium, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp))
        }
    }
}