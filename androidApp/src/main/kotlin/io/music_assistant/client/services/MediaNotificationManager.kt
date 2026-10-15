package io.music_assistant.client.services

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.support.v4.media.session.MediaSessionCompat
import androidx.core.app.NotificationCompat
import androidx.core.net.toUri
import io.music_assistant.client.MainActivity
import io.music_assistant.client.R
import io.music_assistant.client.services.MainMediaPlaybackService.Companion.ACTION_NOTIFICATION_DISMISSED
import io.music_assistant.client.services.MainMediaPlaybackService.Companion.ACTION_TRANSPORT_CUSTOM
import io.music_assistant.client.services.MainMediaPlaybackService.Companion.ACTION_TRANSPORT_NEXT
import io.music_assistant.client.services.MainMediaPlaybackService.Companion.ACTION_TRANSPORT_PAUSE
import io.music_assistant.client.services.MainMediaPlaybackService.Companion.ACTION_TRANSPORT_PLAY
import io.music_assistant.client.services.MainMediaPlaybackService.Companion.ACTION_TRANSPORT_PREVIOUS
import io.music_assistant.client.services.MainMediaPlaybackService.Companion.EXTRA_CUSTOM_ACTION_ID
import io.music_assistant.client.services.SharedMediaSessionManager.NotificationContent

class MediaNotificationManager(
    private val context: Context,
    private val sessionToken: MediaSessionCompat.Token,
) {
    /**
     * API 33+ draws the media card from the session and ignores the title, text and actions
     * set here. Below API 33 SystemUI draws only what this notification carries, so it needs
     * them. [content] and [strings] are null before the session writer starts.
     */
    fun createNotification(content: NotificationContent?, strings: MediaSessionStrings?): Notification {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            data = "musicassistant://app/players".toUri()
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
            .setLargeIcon(content?.bitmap)
            .setContentTitle(content?.title)
            .setContentText(content?.artist)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .also { builder ->
                val actions = content?.let { c -> strings?.let { transportActions(c, it) } }.orEmpty()
                actions.forEach(builder::addAction)
                builder.setStyle(
                    androidx.media.app.NotificationCompat.MediaStyle()
                        .setMediaSession(sessionToken)
                        .also { style ->
                            if (actions.isNotEmpty()) style.setShowActionsInCompactView(0, 1, 2)
                        },
                )
            }
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

    private fun transportActions(content: NotificationContent, strings: MediaSessionStrings) = listOf(
        transportAction(R.drawable.baseline_skip_previous_24, strings.previous, ACTION_TRANSPORT_PREVIOUS),
        if (content.isPlaying) {
            transportAction(R.drawable.baseline_pause_24, strings.pause, ACTION_TRANSPORT_PAUSE)
        } else {
            transportAction(R.drawable.baseline_play_arrow_24, strings.play, ACTION_TRANSPORT_PLAY)
        },
        transportAction(R.drawable.baseline_skip_next_24, strings.next, ACTION_TRANSPORT_NEXT),
    ) + content.customActions.map { custom ->
        transportAction(custom.icon, custom.label, ACTION_TRANSPORT_CUSTOM) {
            putExtra(EXTRA_CUSTOM_ACTION_ID, custom.id)
        }
    }

    private fun transportAction(
        icon: Int,
        title: String,
        action: String,
        extras: Intent.() -> Unit = {},
    ): NotificationCompat.Action {
        val intent = Intent(action).setPackage(context.packageName).apply(extras)
        // PendingIntent identity ignores extras, so custom actions that share an Intent action
        // need distinct request codes, or the last one would overwrite the others.
        val requestCode = intent.getStringExtra(EXTRA_CUSTOM_ACTION_ID).hashCode()
        return NotificationCompat.Action(
            icon,
            title,
            PendingIntent.getBroadcast(
                context,
                requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            ),
        )
    }

    companion object {
        const val CHANNEL_ID = "media_channel"
        const val NOTIFICATION_ID = 1
    }
}
