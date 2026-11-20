package io.music_assistant.client.share

import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjection
import android.os.Build
import androidx.annotation.RequiresApi
import io.music_assistant.client.data.share.DeviceAudioShare
import io.music_assistant.client.utils.sendspinClock
import io.music_assistant.sendspin.api.AudioCapture
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.concurrent.Volatile

/**
 * The playback capture of Android. A foreground service of type `mediaProjection` holds it,
 * because Android ends a projection without one. [serviceClass] is that service; it lives in
 * the application module, with the manifest and the notification resources.
 */
class AndroidDeviceAudioShare(
    private val context: Context,
    private val serviceClass: Class<out Service>,
) : DeviceAudioShare {
    /** `AudioPlaybackCaptureConfiguration` is from Android 10. */
    override val isSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q

    private val _capture = MutableStateFlow<AudioCapture?>(null)
    override val capture: StateFlow<AudioCapture?> = _capture

    @Volatile
    override var endedBySystem = false
        private set

    /** Starts the service with the consent that the user just gave ([resultCode], [consent]). */
    fun start(resultCode: Int, consent: Intent, notification: ShareNotificationText) {
        context.startForegroundService(
            Intent(context, serviceClass)
                .putExtra(EXTRA_RESULT_CODE, resultCode)
                .putExtra(EXTRA_CONSENT, consent)
                .putExtra(EXTRA_TITLE, notification.title)
                .putExtra(EXTRA_TEXT, notification.text)
                .putExtra(EXTRA_STOP_LABEL, notification.stopLabel),
        )
    }

    override fun stop() {
        _capture.value = null
        context.stopService(Intent(context, serviceClass))
    }

    /** The service holds [projection]: the capture can run. */
    @RequiresApi(Build.VERSION_CODES.Q)
    fun onStarted(projection: MediaProjection) {
        endedBySystem = false
        _capture.value = PlaybackCapture(projection, sendspinClock)
    }

    /** The system ended the projection. [stop] clears the capture first, so this is no user stop. */
    fun onRevoked() {
        endedBySystem = _capture.value != null
        _capture.value = null
    }

    companion object {
        const val EXTRA_RESULT_CODE = "result_code"
        const val EXTRA_CONSENT = "consent"
        const val EXTRA_TITLE = "title"
        const val EXTRA_TEXT = "text"
        const val EXTRA_STOP_LABEL = "stop_label"
    }
}

/** The service has no Compose resources, so the launcher hands it the texts in the user's language. */
data class ShareNotificationText(val title: String, val text: String, val stopLabel: String)
