package io.music_assistant.client.webrtc

import co.touchlab.kermit.Logger
import com.shepeliev.webrtckmp.DataChannel
import com.shepeliev.webrtckmp.DataChannelState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Android implementation of DataChannelWrapper using webrtc-kmp library.
 */
actual class DataChannelWrapper(
    private val dataChannel: DataChannel
) {
    private val logger = Logger.withTag("DataChannelWrapper[Android]")
    private val eventScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val closed = AtomicBoolean(false)

    actual val label: String
        get() = dataChannel.label

    private val _state = MutableStateFlow(dataChannel.readyState)
    actual val state: StateFlow<DataChannelState> = _state.asStateFlow()

    private val _textMessages = MutableSharedFlow<String>(extraBufferCapacity = 50)
    actual val messages: Flow<String> = _textMessages.asSharedFlow()

    // CRITICAL: Binary messages (audio chunks) arrive at real-time streaming rate (~50-100/sec).
    // Large buffer prevents backpressure blocking WebRTC native callbacks during consumer lag.
    // Without sufficient buffering, emit() suspends → native callbacks block → audio starves.
    // 2000 messages ≈ 20-40 seconds of headroom depending on chunk size.
    private val _binaryMessages = MutableSharedFlow<ByteArray>(extraBufferCapacity = 2000)
    actual val binaryMessages: Flow<ByteArray> = _binaryMessages.asSharedFlow()

    init {
        // Discriminate between text and binary messages by first byte.
        // JSON text starts with '{' (0x7B) or '[' (0x5B).
        // Binary audio chunks start with a timestamp header (never 0x7B/0x5B).
        // Checking first byte avoids decodeToString() on binary data (50-100 chunks/sec).
        eventScope.launch {
            try {
                dataChannel.onMessage.collect { data ->
                    if (data.isNotEmpty() && (data[0] == 0x7B.toByte() || data[0] == 0x5B.toByte())) {
                        _textMessages.emit(data.decodeToString())
                    } else {
                        _binaryMessages.emit(data)
                    }
                }
            } catch (e: Exception) {
                logger.e(e) { "Error in onMessage flow" }
            }
        }
        // Monitor state changes via flow events
        eventScope.launch {
            try {
                dataChannel.onOpen.collect { _state.update { DataChannelState.Open } }
            } catch (e: Exception) {
                logger.e(e) { "Error in onOpen flow" }
            }
        }
        eventScope.launch {
            try {
                dataChannel.onClosing.collect { _state.update { DataChannelState.Closing } }
            } catch (e: Exception) {
                logger.e(e) { "Error in onClosing flow" }
            }
        }
        eventScope.launch {
            try {
                dataChannel.onClose.collect { _state.update { DataChannelState.Closed } }
            } catch (e: Exception) {
                logger.e(e) { "Error in onClose flow" }
            }
        }
    }

    actual fun send(message: String) {
        val data = message.encodeToByteArray()

        // CRITICAL FIX: webrtc-kmp sends BINARY messages, but Music Assistant server expects TEXT
        // We bypass webrtc-kmp and use native Android WebRTC API to send as TEXT

        val buffer = org.webrtc.DataChannel.Buffer(
            ByteBuffer.wrap(data),
            false
        )
        if (!dataChannel.android.send(buffer)) {
            logger.e { "Native send failed on channel $label (state=${_state.value})" }
        }
    }

    actual fun sendBinary(data: ByteArray) {
        dataChannel.send(data)
    }

    actual suspend fun close() {
        if (!closed.compareAndSet(false, true)) {
            return
        }
        logger.i { "Closing data channel" }
        eventScope.cancel()
        dataChannel.close()
        _state.update { DataChannelState.Closed }
    }
}
