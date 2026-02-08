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

import com.example.visualduress.ui.theme.TextPrimary
import com.example.visualduress.ui.theme.PreviewBackground
import com.example.visualduress.ui.theme.PreviewBorder
import com.example.visualduress.ui.theme.AccentOrange


@Composable
fun CameraPreviewPopup(
    streamUrl: String,
    onClose: () -> Unit,
    onTap: () -> Unit
) {
    val context = LocalContext.current
    val player = remember {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(Uri.parse(streamUrl)))
            playWhenReady = true
            prepare()
        }
    }

    DisposableEffect(Unit) {
        onDispose { player.release() }
    }

    Surface(
        modifier = Modifier
            .width(320.dp)
            .height(200.dp),
        shape = RoundedCornerShape(16.dp),
        elevation = 8.dp,
        color = PreviewBackground
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable { onTap() }
        ) {
            // Video Player
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(8.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.Black)
            ) {
                AndroidView(
                    factory = {
                        PlayerView(it).apply {
                            this.player = player
                            useController = false
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )

                // Play indicator overlay (subtle hint to tap for fullscreen)
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Surface(
                        color = AccentOrange.copy(alpha = 0.8f),
                        shape = CircleShape,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Filled.PlayArrow,
                                contentDescription = "Tap to view fullscreen",
                                tint = Color.White,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    }
                }
            }

            // Close Button
            Surface(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(12.dp),
                shape = CircleShape,
                color = Color.Black.copy(alpha = 0.6f),
                elevation = 4.dp
            ) {
                IconButton(
                    onClick = { onClose() },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Close",
                        tint = TextPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            // "Live" indicator
            Surface(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(12.dp),
                shape = RoundedCornerShape(8.dp),
                color = Color.Red.copy(alpha = 0.8f)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Pulsing dot
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(Color.White, CircleShape)
                    )
                    Text(
                        "LIVE",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }

            // Bottom gradient for better text visibility
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(40.dp)
                    .background(
                        androidx.compose.ui.graphics.Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.6f)
                            )
                        )
                    )
            )

            // Tap to expand hint
            Text(
                "Tap to expand",
                fontSize = 11.sp,
                color = TextPrimary.copy(alpha = 0.8f),
                fontWeight = FontWeight.Medium,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 12.dp)
            )
        }
    }
}