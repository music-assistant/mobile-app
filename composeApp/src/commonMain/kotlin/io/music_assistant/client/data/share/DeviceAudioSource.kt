package io.music_assistant.client.data.share

import io.music_assistant.client.settings.SettingsRepository
import io.music_assistant.sendspin.api.SourceConfig
import io.music_assistant.sendspin.api.SourceDialect
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch

/**
 * What the local player offers the server as a source. The local player reads [config], and
 * [DeviceAudioRouter] selects the [dialect]. It has no dependency on either, so both can hold it.
 */
class DeviceAudioSource(
    share: DeviceAudioShare,
    /** The provider instance that lists this device as a live input; null if the server has none. */
    val instanceId: StateFlow<String?>,
    /** The `server_id` of the MA server; null before its first `server/hello`. */
    private val serverId: StateFlow<String?>,
    private val settings: SettingsRepository,
    scope: CoroutineScope,
) {
    /**
     * The handshake does not tell the revision of the source role, so the router finds it by a
     * retry. A server starts with the dialect it accepted last time, so that retry runs once
     * per server and not once per process.
     */
    val dialect = MutableStateFlow(SourceDialect.SPEC)

    /** Null while nothing is shared, and on a server without Sendspin sources. */
    val config: Flow<SourceConfig?> = combine(share.capture, dialect, instanceId) { capture, dialect, instance ->
        capture?.takeIf { instance != null }?.let { SourceConfig(it, dialect) }
    }

    init {
        // A reconnect can clear the server info for a moment: keep the dialect until another server comes.
        scope.launch {
            serverId.filterNotNull().distinctUntilChanged().collect { id ->
                dialect.value = settings.getSourceDialect(id) ?: SourceDialect.SPEC
            }
        }
    }

    /** The server accepted the source in the current [dialect]: the next process starts with it. */
    fun accepted() {
        val id = serverId.value ?: return
        val current = dialect.value
        if (settings.getSourceDialect(id) != current) settings.setSourceDialect(id, current)
    }
}
