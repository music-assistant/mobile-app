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
                    // Try to decode as UTF-8 text; binary audio data will fail or not start with JSON
                    try {
                        val text = data.decodeToString()
                        if (text.isNotEmpty() && (text.first() == '{' || text.first() == '[')) {
                            // SCTP may deliver multiple messages concatenated in one callback.
                            // Split into individual JSON objects and emit each one separately.
                            var remaining = text
                            while (remaining.isNotEmpty()) {
                                val end = findJsonEnd(remaining)
                                if (end < 0) {
                                    _textMessages.emit(remaining) // incomplete — emit as-is, let parser fail
                                    break
                                }
                                _textMessages.emit(remaining.substring(0, end + 1))
                                remaining = remaining.substring(end + 1).trimStart()
                            }
                        } else {
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

    /**
     * Finds the index of the closing character of the first top-level JSON value in [s].
     * Handles nested objects/arrays and string literals (including escaped chars).
     * Returns -1 if no complete value is found.
     */
    private fun findJsonEnd(s: String): Int {
        var depth = 0
        var inString = false
        var escaped = false
        for (i in s.indices) {
            val c = s[i]
            when {
                escaped -> escaped = false
                inString && c == '\\' -> escaped = true
                c == '"' -> inString = !inString
                !inString && (c == '{' || c == '[') -> depth++
                !inString && (c == '}' || c == ']') -> {
                    depth--
                    if (depth == 0) return i
                }
            }
        }
        return -1
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
