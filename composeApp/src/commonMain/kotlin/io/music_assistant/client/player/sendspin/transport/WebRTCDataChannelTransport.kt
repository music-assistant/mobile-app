package io.music_assistant.client.player.sendspin.transport

import co.touchlab.kermit.Logger
import com.sendspin.protocol.SendSpinTransport
import com.sendspin.protocol.TransportState
import io.music_assistant.client.webrtc.DataChannelState
import io.music_assistant.client.webrtc.DataChannelWrapper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withTimeoutOrNull

/**
 * [SendSpinTransport] over an existing WebRTC data channel. The channel is created during peer
 * connection setup (before this transport), so [connect] just waits for it to reach Open. Auth is
 * inherited from the ma-api channel — no [AuthenticatingTransport] wrapper is needed here.
 *
 * Send is non-blocking (mirrors [DataChannelWrapper]); disconnect/close are no-ops because the
 * channel's lifecycle is owned by WebRTCConnectionManager and shared across Sendspin sessions.
 */
class WebRTCDataChannelTransport(
    private val dataChannelWrapper: DataChannelWrapper,
) : SendSpinTransport {
    private val logger = Logger.withTag("WebRTCDataChannelTransport")
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    init {
        logger.i { "Created WebRTC transport for channel: ${dataChannelWrapper.label}" }
    }

    override val state: StateFlow<TransportState> =
        dataChannelWrapper.state.map { it.toTransportState() }
            .stateIn(scope, SharingStarted.Eagerly, dataChannelWrapper.state.value.toTransportState())

    override val textFrames: Flow<String> = dataChannelWrapper.messages
    override val binaryFrames: Flow<ByteArray> = dataChannelWrapper.binaryMessages

    override suspend fun connect() {
        val current = dataChannelWrapper.state.value
        logger.i { "connect() — current state: $current" }
        if (current == DataChannelState.Open) return

        val opened = withTimeoutOrNull(OPEN_TIMEOUT_MS) {
            dataChannelWrapper.state.first { it == DataChannelState.Open }
        }
        if (opened == null) {
            logger.e { "WebRTC data channel did not open within timeout (state=${dataChannelWrapper.state.value})" }
        }
    }

    override fun send(text: String): Boolean {
        dataChannelWrapper.send(text)
        return true
    }

    override fun send(bytes: ByteArray): Boolean {
        dataChannelWrapper.sendBinary(bytes)
        return true
    }

    // No-op: the data channel is owned by WebRTCConnectionManager and shared across sessions.
    override fun disconnect(code: Int, reason: String?) {
        logger.i { "disconnect() — channel stays open (owned by WebRTCConnectionManager)" }
    }

    override fun close() {
        scope.cancel()
    }

    private fun DataChannelState.toTransportState(): TransportState = when (this) {
        DataChannelState.Connecting -> TransportState.Connecting
        DataChannelState.Open -> TransportState.Connected
        DataChannelState.Closing -> TransportState.Disconnected
        DataChannelState.Closed -> TransportState.Disconnected
    }

    private companion object {
        const val OPEN_TIMEOUT_MS = 10_000L
    }
}
