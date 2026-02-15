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
        // Discriminate between text and binary messages
        eventScope.launch {
            try {
                dataChannel.onMessage.collect { data ->
                    // Access native DataChannel to check binary flag
                    val nativeChannel = dataChannel.android
                    // We need to check if this is binary or text by examining the buffer
                    // The webrtc-kmp library doesn't expose this, so we'll use a heuristic:
                    // Try to decode as UTF-8 text, if it fails or looks like binary, treat as binary
                    // However, for proper discrimination, we need to access the native Buffer's binary flag

                    // For now, we'll collect raw messages and check later
                    // The proper approach is to handle this in the native callback

                    // Try to decode as UTF-8 text
                    try {
                        val text = data.decodeToString()
                        // Check if it's valid JSON (text messages start with '{' or '[')
                        if (text.isNotEmpty() && (text.first() == '{' || text.first() == '[')) {
                            _textMessages.emit(text)
                        } else {
                            // Likely binary data
                            _binaryMessages.emit(data)
                        }
                    } catch (e: Exception) {
                        // Failed to decode as UTF-8, must be binary
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
        dataChannel.android.send(buffer)
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
