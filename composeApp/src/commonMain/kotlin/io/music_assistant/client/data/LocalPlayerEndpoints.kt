package io.music_assistant.client.data

import co.touchlab.kermit.Logger
import io.music_assistant.client.api.ServiceClient
import io.music_assistant.client.settings.SettingsRepository
import io.music_assistant.client.utils.AuthProcessState
import io.music_assistant.client.utils.DataConnectionState
import io.music_assistant.client.utils.SessionState
import io.music_assistant.client.utils.authenticatedToken
import io.music_assistant.client.webrtc.DataChannelInbound
import io.music_assistant.client.webrtc.DataChannelState
import io.music_assistant.client.webrtc.DataChannelWrapper
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.runningFold
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withTimeout

/**
 * Derives the Sendspin [Endpoint] from the MA session. The endpoint follows the
 * session's transport: a WebRTC session gets the data channel, a direct one the
 * proxied WebSocket (or the custom Sendspin server from settings).
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
        settings.sendspinUseCustomConnection,
        settings.sendspinHost,
        settings.sendspinPort,
        settings.sendspinPath,
        settings.sendspinUseTls,
    ) { values -> values }
        .runningFold(null as Endpoint?) { previous, values -> next(previous, values[0] as SessionState) }
        .stateIn(scope, SharingStarted.Eagerly, null)

    private fun next(previous: Endpoint?, state: SessionState): Endpoint? = when (state) {
        is SessionState.Connected -> connected(previous, state) ?: previous
        is SessionState.Disconnected.ByUser,
        SessionState.Disconnected.Initial,
        SessionState.Disconnected.NoServerData,
        -> null

        else -> previous
    }

    private fun connected(previous: Endpoint?, state: SessionState.Connected): Endpoint? {
        val connection = state.dataConnectionState
        if (connection is DataConnectionState.AwaitingAuth &&
            (connection.authProcessState is AuthProcessState.LoggedOut || connection.authProcessState is AuthProcessState.Failed)
        ) {
            return null
        }
        return when (state) {
            is SessionState.Connected.WebRTC -> previous as? Endpoint.WebRtc ?: Endpoint.WebRtc(::openFreshChannel)
            is SessionState.Connected.Direct -> {
                val token = state.authenticatedToken() ?: return null
                Endpoint.WebSocket(webSocketUrl(state), token)
            }
        }
    }

    private fun webSocketUrl(state: SessionState.Connected.Direct): String {
        if (settings.sendspinUseCustomConnection.value) {
            val scheme = if (settings.sendspinUseTls.value) "wss" else "ws"
            val host = settings.sendspinHost.value.takeIf { it.isNotEmpty() } ?: state.connectionInfo.host
            return "$scheme://$host:${settings.sendspinPort.value}${settings.sendspinPath.value}"
        }
        // Same reverse proxy as the control socket, so the same base path.
        return "${state.connectionInfo.wsUrl}/sendspin"
    }

    // --- WebRTC: one single-use channel per connection attempt ---

    private var usedChannel: DataChannelWrapper? = null

    private suspend fun openFreshChannel(): SendspinTransport {
        val current = apiClient.webrtcSendspinChannel
        val fresh = if (current != null && current !== usedChannel && current.state.value != DataChannelState.Closed) {
            current
        } else {
            log.i { "Sendspin data channel spent; renegotiating WebRTC" }
            apiClient.forceWebRTCReconnect()
            withTimeout(CHANNEL_TIMEOUT_MILLIS) {
                apiClient.sessionState.first {
                    it is SessionState.Connected.WebRTC && apiClient.webrtcSendspinChannel.let { c -> c != null && c !== current }
                }
            }
            checkNotNull(apiClient.webrtcSendspinChannel)
        }
        withTimeout(CHANNEL_TIMEOUT_MILLIS) { fresh.state.first { it == DataChannelState.Open } }
        usedChannel = fresh
        return DataChannelTransport(fresh)
    }

    private companion object {
        const val CHANNEL_TIMEOUT_MILLIS = 30_000L
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
