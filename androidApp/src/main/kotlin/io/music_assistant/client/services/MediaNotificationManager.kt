package io.music_assistant.client.services

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.support.v4.media.session.MediaSessionCompat
import androidx.core.app.NotificationCompat
import io.music_assistant.client.MainActivity
import io.music_assistant.client.R
import io.music_assistant.client.services.MainMediaPlaybackService.Companion.ACTION_NOTIFICATION_DISMISSED

class MediaNotificationManager(
    private val context: Context,
    private val sessionToken: MediaSessionCompat.Token,
) {
    fun createNotification(bitmap: Bitmap?): Notification {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            data = Uri.parse("musicassistant://app/players")
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val dismissIntent = Intent(ACTION_NOTIFICATION_DISMISSED).apply {
            setPackage("io.music_assistant.client")
        }
        val dismissPendingIntent = PendingIntent.getBroadcast(
            context,
            0,
            dismissIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setLargeIcon(bitmap)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setStyle(
                androidx.media.app.NotificationCompat.MediaStyle()
                    .setMediaSession(sessionToken),
            )
            .setPriority(NotificationManager.IMPORTANCE_HIGH)
            .setOngoing(true)
            .setAutoCancel(false)
            .setContentIntent(pendingIntent)
            .also { builder ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    builder.setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE)
                }
            }
            .setDeleteIntent(dismissPendingIntent)
            .build()
    }

    companion object {
        const val CHANNEL_ID = "media_channel"
        const val NOTIFICATION_ID = 1
    }
}
