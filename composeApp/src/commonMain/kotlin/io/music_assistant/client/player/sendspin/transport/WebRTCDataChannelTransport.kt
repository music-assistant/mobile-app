package io.music_assistant.client.player.sendspin.transport

import co.touchlab.kermit.Logger
import com.shepeliev.webrtckmp.DataChannelState
import io.music_assistant.client.player.sendspin.WebSocketState
import io.music_assistant.client.webrtc.DataChannelWrapper
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withTimeoutOrNull

/**
 * WebRTC data channel implementation of SendspinTransport.
 * Wraps an existing DataChannelWrapper (created during WebRTC peer connection setup).
 *
 * Unlike WebSocket transport, the data channel is already created before this transport
 * is instantiated. The connect() method simply waits for the channel to be ready.
 */
class WebRTCDataChannelTransport(
    private val dataChannelWrapper: DataChannelWrapper
) : SendspinTransport {

    private val logger = Logger.withTag("WebRTCDataChannelTransport")

    init {
        logger.i { "Created WebRTC transport for channel: ${dataChannelWrapper.label}" }
    }

    /**
     * Maps DataChannelState to WebSocketState for compatibility with Sendspin protocol.
     *
     * DataChannelState values (from webrtc-kmp):
     * - Connecting: Channel is being established
     * - Open: Channel is ready, can send/receive
     * - Closing: Channel is shutting down
     * - Closed: Channel is closed
     */
    override val connectionState: Flow<WebSocketState> =
        dataChannelWrapper.state.map { dataChannelState ->
            when (dataChannelState) {
                DataChannelState.Connecting -> WebSocketState.Connecting
                DataChannelState.Open -> WebSocketState.Connected
                DataChannelState.Closing -> WebSocketState.Disconnected
                DataChannelState.Closed -> WebSocketState.Disconnected
            }
        }

    /**
     * Text messages (JSON protocol messages) from remote peer.
     */
    override val textMessages: Flow<String> = dataChannelWrapper.messages

    /**
     * Binary messages (audio chunks) from remote peer.
     */
    override val binaryMessages: Flow<ByteArray> = dataChannelWrapper.binaryMessages

    /**
     * Connect to the transport.
     *
     * For WebRTC, the data channel is already created during peer connection setup.
     * This method simply waits for the channel to reach "Open" state (with timeout).
     *
     * @throws IllegalStateException if channel doesn't open within timeout
     */
    override suspend fun connect() {
        val currentState = dataChannelWrapper.state.value
        logger.i { "Connect called, current state: $currentState" }

        if (currentState == DataChannelState.Open) {
            logger.d { "Channel already open" }
            return
        }

        // Wait for channel to open (with 10 second timeout)
        logger.d { "Waiting for channel to open..." }
        val openState = withTimeoutOrNull(10_000) {
            dataChannelWrapper.state.first { it == DataChannelState.Open }
        }

        if (openState == null) {
            val error = "WebRTC data channel did not open within timeout (current state: ${dataChannelWrapper.state.value})"
            logger.e { error }
            throw IllegalStateException(error)
        }

        logger.i { "Channel opened successfully" }
    }

    /**
     * Send text message (JSON protocol message).
     * Channel must be in "open" state.
     */
    override suspend fun sendText(message: String) {
        val currentState = dataChannelWrapper.state.value
        if (currentState != DataChannelState.Open) {
            logger.w { "Attempted to send text while channel not open (state: $currentState)" }
            throw IllegalStateException("Channel not open (state: $currentState)")
        }

        dataChannelWrapper.send(message)
    }

    /**
     * Send binary message (audio data).
     * Channel must be in "open" state.
     */
    override suspend fun sendBinary(data: ByteArray) {
        val currentState = dataChannelWrapper.state.value
        if (currentState != DataChannelState.Open) {
            logger.w { "Attempted to send binary while channel not open (state: $currentState)" }
            throw IllegalStateException("Channel not open (state: $currentState)")
        }

        dataChannelWrapper.sendBinary(data)
    }

    /**
     * Disconnect from the transport.
     * Closes the underlying data channel.
     */
    override suspend fun disconnect() {
        logger.i { "Disconnecting WebRTC transport" }
        dataChannelWrapper.close()
    }

    /**
     * Close and cleanup resources.
     * Closes the underlying data channel.
     */
    override fun close() {
        logger.i { "Closing WebRTC transport" }
        // DataChannelWrapper.close() is suspend, but close() is not
        // The close will happen when disconnect() is called
        // For immediate cleanup, we could launch a coroutine, but that requires a scope
        // For now, rely on disconnect() being called before close()
    }
}
