package io.music_assistant.client.wear.data

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.wear.ongoing.OngoingActivity
import androidx.wear.ongoing.Status
import io.music_assistant.client.wear.R

/**
 * The "still playing" presence the watch needs to keep the app on screen.
 *
 * Wear gives an ambient app one minute and then moves its task to the back and brings the
 * watch face forward — its own log names the reason as the task having no ongoing activity.
 * An Ongoing Activity is the platform's answer: it marks the task as still doing something
 * the user is watching, and it is also what puts the playing chip on the watch face.
 *
 * It is published only while a player is actually playing. A notification posted merely to
 * hold the screen, with nothing happening behind it, is the kind of thing the chip exists to
 * stop.
 */
class OngoingPlayback(private val context: Context) {

    private val manager = NotificationManagerCompat.from(context)
    private var channelReady = false

    /** What was last published, so an unchanged track does not re-post the notification. */
    private var shown: Pair<String, String?>? = null

    fun update(nowPlaying: NowPlaying?) {
        val playing = nowPlaying?.takeIf { it.isPlaying }
            ?: return hide()
        val next = playing.title to playing.artist
        if (next == shown) return
        if (!canPost()) return
        publish(playing.title, playing.artist)
        shown = next
    }

    fun hide() {
        shown ?: return
        shown = null
        manager.cancel(NOTIFICATION_ID)
    }

    /**
     * Posting needs the runtime permission from Android 13 on, and the watch is Android 13 or
     * newer. Without it the post is dropped silently, so the app keeps the ambient screen for
     * the minute Wear allows and no longer.
     */
    private fun canPost(): Boolean = ContextCompat.checkSelfPermission(
        context,
        android.Manifest.permission.POST_NOTIFICATIONS,
    ) == PackageManager.PERMISSION_GRANTED

    private fun publish(title: String, artist: String?) {
        ensureChannel()
        val touch = PendingIntent.getActivity(
            context,
            0,
            context.packageManager.getLaunchIntentForPackage(context.packageName)
                ?.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
                ?: Intent(),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_music_note)
            .setContentTitle(title)
            .setContentText(artist)
            .setContentIntent(touch)
            .setOngoing(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
            .setPriority(NotificationCompat.PRIORITY_LOW)
        OngoingActivity.Builder(context, NOTIFICATION_ID, builder)
            .setStaticIcon(R.drawable.ic_music_note)
            .setTouchIntent(touch)
            .setStatus(Status.forPart(Status.TextPart(listOfNotNull(title, artist).joinToString(" · "))))
            .build()
            .apply(context)
        manager.notify(NOTIFICATION_ID, builder.build())
    }

    private fun ensureChannel() {
        if (channelReady) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.channel_playing),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            setShowBadge(false)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }
        context.getSystemService(NotificationManager::class.java)
            ?.createNotificationChannel(channel)
        channelReady = true
    }

    private companion object {
        const val CHANNEL_ID = "playing"
        const val NOTIFICATION_ID = 1
    }
}
