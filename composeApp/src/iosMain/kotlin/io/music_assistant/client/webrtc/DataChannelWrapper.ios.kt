@file:OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)

package io.music_assistant.client.webrtc

import WebRTC.RTCDataBuffer
import co.touchlab.kermit.Logger
import com.shepeliev.webrtckmp.DataChannel
import com.shepeliev.webrtckmp.DataChannelState
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.allocArrayOf
import kotlinx.cinterop.memScoped
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
import platform.Foundation.create

/**
 * iOS implementation of DataChannelWrapper using webrtc-kmp library.
 *
 * Music Assistant requires TEXT frames for JSON commands, but webrtc-kmp
 * always sends binary frames on iOS. Text messages are sent via the native
 * RTCDataChannel API directly with isBinary=false.
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

    // Audio chunks arrive at ~50-100/sec; a large buffer prevents emit() from suspending
    // and blocking the WebRTC native callback thread during consumer lag.
    private val _binaryMessages = MutableSharedFlow<ByteArray>(extraBufferCapacity = 2000)
    actual val binaryMessages: Flow<ByteArray> = _binaryMessages.asSharedFlow()

    init {
        // Discriminate between text and binary messages
        eventScope.launch {
            try {
                dataChannel.onMessage.collect { data ->
                    // Check first byte only to avoid decoding every binary audio chunk.
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
        // webrtc-kmp always sends binary frames on iOS; use the native RTCDataChannel API
        // directly to send as a TEXT frame (isBinary=false) as the server requires.
        val bytes = message.encodeToByteArray()
        if (bytes.isEmpty()) return
        val nsData: NSData = memScoped {
            NSData.create(bytes = allocArrayOf(bytes), length = bytes.size.toULong())
        } ?: return
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
}
