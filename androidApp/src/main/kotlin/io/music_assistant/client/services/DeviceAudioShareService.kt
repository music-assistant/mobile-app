package io.music_assistant.client.services

import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioManager
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.core.content.IntentCompat
import co.touchlab.kermit.Logger
import io.music_assistant.client.R
import io.music_assistant.client.share.AndroidDeviceAudioShare
import org.koin.android.ext.android.inject

/**
 * Holds the media projection of a device audio share. Android ends a projection that has no
 * foreground service of type `mediaProjection`, and wants the service in the foreground
 * before the projection exists.
 *
 * The service only holds the projection. [AndroidDeviceAudioShare] publishes the capture, and
 * the Sendspin player records from it while the server wants the stream.
 */
@RequiresApi(Build.VERSION_CODES.Q)
class DeviceAudioShareService : Service() {
    private val logger = Logger.withTag("DeviceAudioShareService")
    private val share: AndroidDeviceAudioShare by inject()
    private var projection: MediaProjection? = null

    /** This service muted the media stream, so it must give the sound back. */
    private var mutedMedia = false

    private val projectionCallback = object : MediaProjection.Callback() {
        override fun onStop() {
            logger.i { "The system ended the projection" }
            share.onRevoked()
            stopSelf()
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> share.stop()
            else -> start(intent)
        }
        return START_NOT_STICKY
    }

    private fun start(intent: Intent?) {
        startForeground(NOTIFICATION_ID, notification(intent), ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        if (projection != null) return
        val consent = intent?.let {
            IntentCompat.getParcelableExtra(it, AndroidDeviceAudioShare.EXTRA_CONSENT, Intent::class.java)
        }
        val granted = consent?.let { data ->
            val resultCode = intent.getIntExtra(AndroidDeviceAudioShare.EXTRA_RESULT_CODE, Activity.RESULT_CANCELED)
            getSystemService(MediaProjectionManager::class.java).getMediaProjection(resultCode, data)
        } ?: run {
            logger.w { "No capture consent: stopping" }
            stopSelf()
            return
        }
        // Android 14 wants the callback before the projection is used.
        granted.registerCallback(projectionCallback, Handler(Looper.getMainLooper()))
        projection = granted
        share.onStarted(granted)
        muteMedia()
    }

    /**
     * The capture copies the audio and the device still plays it, about 2 seconds before the
     * player: an echo. So the media stream is silent for the share. The capture does not depend
     * on the stream volume. A mute, not a volume of zero: if the process dies, a volume key
     * brings the sound back at its old level. A stream that the user muted stays muted after.
     */
    private fun muteMedia() {
        val audio = getSystemService(AudioManager::class.java)
        if (audio.isStreamMute(AudioManager.STREAM_MUSIC)) return
        runCatching { audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_MUTE, 0) }
            .onSuccess { mutedMedia = true }
            .onFailure { logger.w(it) { "Cannot mute the media stream" } }
    }

    private fun unmuteMedia() {
        if (!mutedMedia) return
        mutedMedia = false
        runCatching {
            getSystemService(AudioManager::class.java)
                .adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_UNMUTE, 0)
        }.onFailure { logger.w(it) { "Cannot unmute the media stream" } }
    }

    override fun onDestroy() {
        projection?.let {
            it.unregisterCallback(projectionCallback)
            it.stop()
        }
        projection = null
        unmuteMedia()
        // No effect after a stop by the user, which cleared the capture already.
        share.onRevoked()
        super.onDestroy()
    }

    private fun notification(intent: Intent?): Notification {
        val title = intent.text(AndroidDeviceAudioShare.EXTRA_TITLE)
        getSystemService(NotificationManager::class.java)
            .createNotificationChannel(NotificationChannel(CHANNEL_ID, title, NotificationManager.IMPORTANCE_LOW))
        val stop = PendingIntent.getService(
            this,
            0,
            Intent(this, DeviceAudioShareService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(intent.text(AndroidDeviceAudioShare.EXTRA_TEXT))
            .setOngoing(true)
            .addAction(0, intent.text(AndroidDeviceAudioShare.EXTRA_STOP_LABEL), stop)
            .build()
    }

    private fun Intent?.text(key: String): String = this?.getStringExtra(key).orEmpty()

    private companion object {
        const val CHANNEL_ID = "device_audio_share"
        const val NOTIFICATION_ID = 2
        const val ACTION_STOP = "io.music_assistant.client.action.STOP_PHONE_AUDIO_SHARE"
    }
}
