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
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Android implementation of DataChannelWrapper using webrtc-kmp library.
 */
actual class DataChannelWrapper(
    private val dataChannel: DataChannel,
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
        // CRITICAL: Bypass webrtc-kmp's onMessage/onOpen/onClose flows entirely.
        // webrtc-kmp uses filterNotNull internally which SIGSEGV-crashes when the native
        // SCTP layer frees memory during flow emission. Instead, register a native
        // DataChannel.Observer directly — same bypass strategy we use for send().
        dataChannel.android.registerObserver(object : org.webrtc.DataChannel.Observer {
            override fun onBufferedAmountChange(previousAmount: Long) {}

            override fun onStateChange() {
                val mappedState = when (dataChannel.android.state()) {
                    org.webrtc.DataChannel.State.CONNECTING -> DataChannelState.Connecting
                    org.webrtc.DataChannel.State.OPEN -> DataChannelState.Open
                    org.webrtc.DataChannel.State.CLOSING -> DataChannelState.Closing
                    org.webrtc.DataChannel.State.CLOSED -> DataChannelState.Closed
                    else -> DataChannelState.Closed
                }
                _state.update { mappedState }
            }

            override fun onMessage(buffer: org.webrtc.DataChannel.Buffer) {
                // Copy data out of native ByteBuffer immediately — buffer is reused after return.
                val byteBuffer = buffer.data
                val bytes = ByteArray(byteBuffer.remaining())
                byteBuffer.get(bytes)

                if (bytes.isNotEmpty() && (bytes[0] == 0x7B.toByte() || bytes[0] == 0x5B.toByte())) {
                    if (!_textMessages.tryEmit(bytes.decodeToString())) {
                        logger.w { "Text message buffer full, dropping message" }
                    }
                } else {
                    if (!_binaryMessages.tryEmit(bytes)) {
                        logger.w { "Binary message buffer full, dropping chunk" }
                    }
                }
            }
        })
    }

    actual fun send(message: String) {
        val data = message.encodeToByteArray()

        // CRITICAL FIX: webrtc-kmp sends BINARY messages, but Music Assistant server expects TEXT
        // We bypass webrtc-kmp and use native Android WebRTC API to send as TEXT

        val buffer = org.webrtc.DataChannel.Buffer(
            ByteBuffer.wrap(data),
            false,
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
