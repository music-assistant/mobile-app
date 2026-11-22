package io.music_assistant.client.data.share

import io.music_assistant.sendspin.api.AudioCapture
import io.music_assistant.sendspin.api.SourceCodec
import io.music_assistant.sendspin.api.SourceConfig
import io.music_assistant.sendspin.api.SourceDialect
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.updateAndGet

/**
 * What the local player offers the server as a source. The local player reads [config], and
 * [DeviceAudioRouter] selects the [dialect]. It has no dependency on either, so both can hold it.
 */
class DeviceAudioSource(
    share: DeviceAudioShare,
    /** The provider instance that lists this device as a live input; null if the server has none. */
    val instanceId: StateFlow<String?>,
    /** The codec setting. A share takes its value when it starts, so a change does not restart a running share. */
    private val codec: StateFlow<SourceCodec>,
) {
    /** The handshake does not tell the revision of the source role, so the router finds it by a retry. */
    val dialect = MutableStateFlow(SourceDialect.SPEC)

    /**
     * The capture of the running share and the codec that it started with. It is shared by every
     * collector of [config], so a collector that comes later does not see a newer codec setting.
     */
    private val started = MutableStateFlow<Pair<AudioCapture, SourceCodec>?>(null)

    /** Null while nothing is shared, and on a server without Sendspin sources. */
    val config: Flow<SourceConfig?> = combine(share.capture, dialect, instanceId) { capture, dialect, instance ->
        if (capture == null) started.value = null
        capture?.takeIf { instance != null }?.let { SourceConfig(it, dialect, startCodec(it)) }
    }

    private fun startCodec(capture: AudioCapture): SourceCodec =
        started.updateAndGet { current -> current?.takeIf { it.first === capture } ?: (capture to codec.value) }
            ?.second ?: codec.value
}
