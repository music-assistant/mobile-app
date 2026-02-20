@file:OptIn(ExperimentalForeignApi::class)

package io.music_assistant.client.webrtc

import WebRTC.RTCDataBuffer
import co.touchlab.kermit.Logger
import com.shepeliev.webrtckmp.DataChannel
import com.shepeliev.webrtckmp.DataChannelState
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
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
import platform.Foundation.NSData

/**
 * iOS implementation of DataChannelWrapper using webrtc-kmp library.
 *
 * CRITICAL: Music Assistant server expects TEXT frames for JSON commands.
 * webrtc-kmp's DataChannel.send() always sends binary frames on iOS.
 * We bypass it for text messages by accessing the native RTCDataChannel
 * directly and calling sendData with isBinary=false.
 */
actual class DataChannelWrapper(
    private val dataChannel: DataChannel
) {
    private val logger = Logger.withTag("DataChannelWrapper[iOS]")
    private val eventScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var closed: Boolean = false

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
                    // Fast path: check first byte only — avoids full UTF-8 decode on every
                    // binary audio chunk (arriving at 50-100/sec).
                    if (data.isNotEmpty() && (data[0] == '{'.code.toByte() || data[0] == '['.code.toByte())) {
                        _textMessages.emit(data.decodeToString())
                    } else {
                        _binaryMessages.emit(data)
                    }
                }
            } catch (e: Exception) {
                logger.e(e) { "Error in onMessage flow" }
            }
        }
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

        // CRITICAL FIX: webrtc-kmp sends BINARY frames on iOS, but Music Assistant server expects TEXT.
        // We bypass webrtc-kmp and use the native RTCDataChannel API to send as TEXT (isBinary=false).
        val nsData = data.toNSData()
        val buffer = RTCDataBuffer(nsData, false)
        dataChannel.ios.sendData(buffer)
    }

    actual fun sendBinary(data: ByteArray) {
        dataChannel.send(data)
    }

    actual suspend fun close() {
        if (closed) return
        closed = true
        logger.i { "Closing data channel" }
        eventScope.cancel()
        dataChannel.close()
        _state.update { DataChannelState.Closed }
    }

    private fun ByteArray.toNSData(): NSData {
        if (isEmpty()) return NSData()
        val mutableData = NSMutableData()
        usePinned { pinned ->
            mutableData.appendBytes(pinned.addressOf(0), size.toULong())
        }
        return mutableData
    }
}
