package com.example.visualduress.ui.components

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView

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

    Box(
        modifier = Modifier
            .width(300.dp)
            .height(180.dp)
            .background(Color.Black)
            .padding(8.dp)
            .clickable { onTap() }
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

        IconButton(
            onClick = { onClose() },
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(4.dp)
        ) {
            Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
        }
    }
}
