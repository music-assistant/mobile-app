package io.music_assistant.client.share

import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjection
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.annotation.VisibleForTesting
import io.music_assistant.client.data.share.DeviceAudioShare
import io.music_assistant.client.utils.sendspinClock
import io.music_assistant.sendspin.api.AudioCapture
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
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

    private val _startFailures = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    override val startFailures: Flow<Unit> = _startFailures

    /** A stop by the user and an end by the system can race: the first to clear the capture decides. */
    private val lock = Any()

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
        synchronized(lock) { _capture.value = null }
        context.stopService(Intent(context, serviceClass))
    }

    /** The service holds [projection]: the capture can run. */
    @RequiresApi(Build.VERSION_CODES.Q)
    fun onStarted(projection: MediaProjection) =
        onStarted(PlaybackCapture(projection, sendspinClock, onFailed = ::onCaptureFailed))

    @VisibleForTesting
    internal fun onStarted(capture: AudioCapture) = synchronized(lock) {
        endedBySystem = false
        _capture.value = capture
    }

    /** The service got no projection from the consent: nothing was shared. */
    fun onStartFailed() {
        _startFailures.tryEmit(Unit)
    }

    /**
     * The system ended the projection, or the service went away. No effect once the capture is
     * cleared: after a stop by the user, or on the second call of a revoke (callback, then destroy).
     */
    fun onRevoked() {
        synchronized(lock) { _capture.value?.let { endBySystem() } }
    }

    /** [capture] cannot record any more; a capture that was replaced or stopped has no effect. */
    @VisibleForTesting
    internal fun onCaptureFailed(capture: AudioCapture) = synchronized(lock) {
        if (_capture.value === capture) endBySystem()
    }

    /** [endedBySystem] first: the router reads it when the capture becomes null. */
    private fun endBySystem() {
        endedBySystem = true
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
