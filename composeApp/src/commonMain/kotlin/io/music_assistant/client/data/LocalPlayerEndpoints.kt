package io.music_assistant.client.data

import co.touchlab.kermit.Logger
import io.music_assistant.client.api.LABEL_ROUTED_CHANNELS_SCHEMA
import io.music_assistant.client.api.ServiceClient
import io.music_assistant.client.data.model.server.ENCRYPTED_SENDSPIN_MIN_SCHEMA
import io.music_assistant.client.data.model.server.sendspinWireFor
import io.music_assistant.client.data.model.server.supportsEncryptedSendspin
import io.music_assistant.client.settings.SettingsRepository
import io.music_assistant.client.utils.AuthProcessState
import io.music_assistant.client.utils.DataConnectionState
import io.music_assistant.client.utils.SessionState
import io.music_assistant.client.utils.authenticatedToken
import io.music_assistant.client.webrtc.DataChannelInbound
import io.music_assistant.client.webrtc.DataChannelState
import io.music_assistant.client.webrtc.DataChannelWrapper
import io.music_assistant.client.webrtc.WebRTCConnectionManager
import io.music_assistant.sendspin.api.Endpoint
import io.music_assistant.sendspin.api.Frame
import io.music_assistant.sendspin.api.SendspinTransport
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.produce
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.runningFold
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Derives the Sendspin [Endpoint] from the MA session. The endpoint follows the
 * session's transport: a WebRTC session gets the data channel, a direct one the
 * proxied WebSocket (or the custom Sendspin server from settings).
 * A known schema below [ENCRYPTED_SENDSPIN_MIN_SCHEMA] yields [Endpoint.Unsupported];
 * an unknown schema is allowed through so the handshake decides. The schema and the
 * server version pick the wire dialect ([sendspinWireFor]); a wire change restarts the connection.
 *
 * Transient session states keep the last endpoint: the Sendspin connection
 * lives with the process and reconnects on its own, so a reconnecting MA
 * session must not tear it down. Only a logout, a terminal auth failure, or no
 * server at all clears it. The WebRTC endpoint instance is reused while the
 * session stays WebRTC, because a new instance restarts the connection.
 */
class LocalPlayerEndpoints(
    private val apiClient: ServiceClient,
    private val settings: SettingsRepository,
    scope: CoroutineScope,
) {
    private val log = Logger.withTag("LocalPlayerEndpoints")

    val endpoint: StateFlow<Endpoint?> = combine(
        apiClient.sessionState,
        settings.sendspinPlayerSettings.map { it.connectionOverride }.distinctUntilChanged(),
    ) { state, _ -> state }
        .runningFold(null as Endpoint?) { previous, state -> next(previous, state) }
        .stateIn(scope, SharingStarted.Eagerly, null)

    /** Pure reducer; a Connected session is Clear (terminal auth), Replace (proven), or Keep (auth pending). */
    internal fun next(previous: Endpoint?, state: SessionState): Endpoint? = when (state) {
        is SessionState.Connected -> when {
            state.authIsTerminal() -> null
            else -> connected(previous, state) ?: previous
        }

        is SessionState.Disconnected.ByUser,
        SessionState.Disconnected.Initial,
        SessionState.Disconnected.NoServerData,
        -> null

        else -> previous
    }

    /** A logout or a failed reauthorization keeps the session Connected; nothing follows on its own. */
    private fun SessionState.Connected.authIsTerminal(): Boolean {
        val connection = dataConnectionState as? DataConnectionState.AwaitingAuth ?: return false
        return connection.authProcessState is AuthProcessState.LoggedOut ||
            connection.authProcessState is AuthProcessState.Failed
    }

    /**
     * The endpoint for a proven session, or null while the server info or the authentication
     * is still pending. Without server info the wire is unknown, and a guessed wire replaced on
     * `server/hello` would spend the single-use WebRTC channel and renegotiate in a loop.
     */
    private fun connected(previous: Endpoint?, state: SessionState.Connected): Endpoint? {
        val info = state.serverInfo ?: return null
        val schema = info.schemaVersion
        // Missing schema is inconclusive, so let the wire handshake establish compatibility.
        if (schema != null && !supportsEncryptedSendspin(schema)) {
            return Endpoint.Unsupported(
                "MA server schema $schema does not support encrypted Sendspin; requires schema $ENCRYPTED_SENDSPIN_MIN_SCHEMA",
            )
        }
        val wire = sendspinWireFor(schema, info.serverVersion)
        return when (state) {
            is SessionState.Connected.WebRTC ->
                (previous as? Endpoint.WebRtc)?.takeIf { it.wire == wire } ?: Endpoint.WebRtc(wire, ::openFreshChannel)

            is SessionState.Connected.Direct -> state.authenticatedToken()?.let { token ->
                Endpoint.WebSocket(webSocketUrl(state), token, wire)
            }
        }
    }

    private fun webSocketUrl(state: SessionState.Connected.Direct): String {
        val override = settings.sendspinPlayerSettings.value.connectionOverride
        if (override.enabled) {
            val scheme = if (override.tls) "wss" else "ws"
            val host = override.host.takeIf { it.isNotEmpty() } ?: state.connectionInfo.host
            return "$scheme://$host:${override.port}${override.path}"
        }
        // Same reverse proxy as the control socket, so the same base path.
        return "${state.connectionInfo.wsUrl}/sendspin"
    }

    // --- WebRTC: one single-use channel per connection attempt ---

    private var usedChannel: DataChannelWrapper? = null

    /**
     * The negotiated channel if no attempt used it yet, else a new channel on the live peer,
     * else a renegotiated peer connection. Only the last one interrupts the MA session.
     */
    private suspend fun openFreshChannel(): SendspinTransport {
        val fresh = unusedNegotiatedChannel() ?: reopenedChannel() ?: renegotiatedChannel()
        withTimeout(CHANNEL_TIMEOUT_MILLIS) { fresh.state.first { it == DataChannelState.Open } }
        usedChannel = fresh
        return DataChannelTransport(fresh)
    }

    private fun unusedNegotiatedChannel(): DataChannelWrapper? = apiClient.webrtcSendspinChannel
        ?.takeIf { it !== usedChannel && it.state.value != DataChannelState.Closed }

    /**
     * A new channel on the live peer, signalled in-band. The server bridges one `sendspin`
     * channel for each peer and refuses a second one while the first is open, so the spent
     * channel must close first. A server without label routing gets a renegotiation instead.
     */
    private suspend fun reopenedChannel(): DataChannelWrapper? {
        val state = apiClient.sessionState.value as? SessionState.Connected.WebRTC ?: return null
        val schema = state.serverInfo?.schemaVersion ?: return null
        if (schema < LABEL_ROUTED_CHANNELS_SCHEMA) return null
        usedChannel?.let { spent ->
            withTimeoutOrNull(SPENT_CLOSE_TIMEOUT_MILLIS) { spent.state.first { it == DataChannelState.Closed } }
        }
        return apiClient.openWebRTCDataChannel(WebRTCConnectionManager.SENDSPIN_CHANNEL_LABEL)
            ?.also { log.i { "Sendspin data channel reopened on the live peer" } }
    }

    private suspend fun renegotiatedChannel(): DataChannelWrapper {
        val current = apiClient.webrtcSendspinChannel
        log.i { "Sendspin data channel spent; renegotiating WebRTC" }
        apiClient.forceWebRTCReconnect()
        withTimeout(CHANNEL_TIMEOUT_MILLIS) {
            apiClient.sessionState.first {
                it is SessionState.Connected.WebRTC && apiClient.webrtcSendspinChannel.let { c -> c != null && c !== current }
            }
        }
        return checkNotNull(apiClient.webrtcSendspinChannel)
    }

    private companion object {
        const val CHANNEL_TIMEOUT_MILLIS = 30_000L
        const val SPENT_CLOSE_TIMEOUT_MILLIS = 2_000L
    }
}

/** [SendspinTransport] over one open [DataChannelWrapper]. */
private class DataChannelTransport(private val wrapper: DataChannelWrapper) : SendspinTransport {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override val inbound: ReceiveChannel<Frame> = scope.produce(capacity = INBOUND_CAPACITY) {
        wrapper.inbound.collect { message ->
            send(
                when (message) {
                    is DataChannelInbound.Text -> Frame.Text(message.text)
                    is DataChannelInbound.Binary -> Frame.Binary(message.bytes)
                },
            )
        }
    }

    override suspend fun send(frame: Frame) {
        check(wrapper.state.value == DataChannelState.Open) { "data channel not open (${wrapper.state.value})" }
        when (frame) {
            is Frame.Text -> wrapper.send(frame.text)
            is Frame.Binary -> wrapper.sendBinary(frame.bytes)
        }
    }

    override suspend fun close() {
        wrapper.close()
        scope.coroutineContext[kotlinx.coroutines.Job]?.cancel()
    }

    private companion object {
        const val INBOUND_CAPACITY = 64
    }
}
