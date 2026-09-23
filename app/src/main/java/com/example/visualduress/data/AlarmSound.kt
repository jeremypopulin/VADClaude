package com.example.visualduress.data

import android.content.Context
import android.media.MediaPlayer
import android.util.Log
import com.example.visualduress.R

/**
 * The ONE alarm sound for the whole app.
 *
 * Anything that needs the beeper starts it with a reason ("device" or "connection").
 * The sound plays while at least one reason is active and stops when the last one is cleared.
 * Because there is only ever one MediaPlayer, two beeps can never play over each other,
 * and nothing can leave an orphaned beeper running.
 */
object AlarmSound {

    const val DEVICE = "device"          // a device is in alarm and not reset
    const val CONNECTION = "connection"  // controller link lost for 2+ minutes

    private var player: MediaPlayer? = null
    private val reasons = mutableSetOf<String>()

    @Synchronized
    fun start(context: Context, reason: String, beforeStart: () -> Unit = {}) {
        reasons.add(reason)
        if (player?.isPlaying == true) return
        releasePlayer()
        try {
            beforeStart()
            player = MediaPlayer.create(context.applicationContext, R.raw.beep)?.apply {
                isLooping = true
                start()
            }
            Log.i("AlarmSound", "Sound on ($reasons)")
        } catch (e: Exception) {
            Log.e("AlarmSound", "Could not start sound: ${e.message}", e)
        }
    }

    @Synchronized
    fun stop(reason: String) {
        reasons.remove(reason)
        if (reasons.isEmpty()) releasePlayer()
    }

    @Synchronized
    fun stopAll() {
        reasons.clear()
        releasePlayer()
    }

    fun isActive(reason: String): Boolean = reason in reasons

    private fun releasePlayer() {
        try {
            player?.let {
                if (it.isPlaying) it.stop()
                it.reset()
                it.release()
            }
        } catch (e: Exception) {
            Log.e("AlarmSound", "Error stopping sound: ${e.message}", e)
        }
        player = null
    }
}