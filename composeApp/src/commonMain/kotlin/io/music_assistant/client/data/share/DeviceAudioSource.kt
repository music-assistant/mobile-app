package io.music_assistant.client.data.share

import io.music_assistant.sendspin.api.SourceConfig
import io.music_assistant.sendspin.api.SourceDialect
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine

/**
 * What the local player offers the server as a source. The local player reads [config], and
 * [DeviceAudioRouter] selects the [dialect]. It has no dependency on either, so both can hold it.
 */
class DeviceAudioSource(
    share: DeviceAudioShare,
    /** The provider instance that lists this device as a live input; null if the server has none. */
    val instanceId: StateFlow<String?>,
) {
    /** The handshake does not tell the revision of the source role, so the router finds it by a retry. */
    val dialect = MutableStateFlow(SourceDialect.SPEC)

    /** Null while nothing is shared, and on a server without Sendspin sources. */
    val config: Flow<SourceConfig?> = combine(share.capture, dialect, instanceId) { capture, dialect, instance ->
        capture?.takeIf { instance != null }?.let { SourceConfig(it, dialect) }
    }
}
