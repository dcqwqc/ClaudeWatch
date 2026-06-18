package io.qwqc.claudewatch

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.ImageDecoderDecoder
import io.qwqc.claudewatch.R

class ClaudeWatchApp : Application(), ImageLoaderFactory {
    override fun onCreate() {
        super.onCreate()
        Graph.init(this)
        createDoneChannel()
    }

    /** Coil loader with animated-GIF support (for the Clawd mascot). */
    override fun newImageLoader(): ImageLoader =
        ImageLoader.Builder(this)
            .components { add(ImageDecoderDecoder.Factory()) }
            .build()

    /** High-importance channel so "Claude is done" buzzes the wrist immediately. */
    private fun createDoneChannel() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            getString(R.string.done_channel_id),
            getString(R.string.done_channel_name),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = "Fires when Claude finishes a prompt"
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 120, 80, 120, 80, 240)
        }
        nm.createNotificationChannel(channel)
    }
}
