package io.music_assistant.client.data.share

import io.music_assistant.sendspin.api.AudioCapture
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow

/**
 * The capture of the audio that other apps play on this device. The platform starts it from
 * the UI, because it needs the consent of the user (`rememberDeviceAudioShareLauncher`).
 */
interface DeviceAudioShare {
    /** False on a platform, or on a system version, that cannot capture playback. */
    val isSupported: Boolean

    /** The running capture; null while nothing is shared. */
    val capture: StateFlow<AudioCapture?>

    /**
     * True if the system ended the last capture: the user stopped it in the system UI, another
     * app took the projection, or the capture failed. Read it when [capture] becomes null.
     */
    val endedBySystem: Boolean

    /** Emits when a start gave no capture: the system did not accept the consent. */
    val startFailures: Flow<Unit>

    /**
     * Whether the device is on Wi-Fi. Cold: the platform watches the network only while this is
     * collected, so the router collects it only during a share. Empty where the platform cannot tell.
     */
    val wifiConnected: Flow<Boolean>

    /** Ends the capture. No effect while nothing is shared. */
    fun stop()
}

/** A platform without playback capture. */
class UnsupportedDeviceAudioShare : DeviceAudioShare {
    override val isSupported = false
    override val capture: StateFlow<AudioCapture?> = MutableStateFlow(null)
    override val endedBySystem = false
    override val startFailures: Flow<Unit> = emptyFlow()
    override val wifiConnected: Flow<Boolean> = emptyFlow()
    override fun stop() = Unit
}
