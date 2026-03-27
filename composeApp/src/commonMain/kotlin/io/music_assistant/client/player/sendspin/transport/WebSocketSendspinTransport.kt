package io.music_assistant.client.player.sendspin.transport

import io.music_assistant.client.player.sendspin.WebSocketState
import io.music_assistant.client.player.sendspin.connection.SendspinWsHandler
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * WebSocket implementation of SendspinTransport.
 * Wraps existing SendspinWsHandler to conform to the transport interface.
 */
class WebSocketSendspinTransport(
    private val sendspinWsHandler: SendspinWsHandler
) : SendspinTransport {

    constructor(
        serverUrl: String,
        networkAvailable: StateFlow<Boolean>? = null
    ) : this(SendspinWsHandler(serverUrl, networkAvailable))

    override val connectionState: Flow<WebSocketState>
        get() = sendspinWsHandler.connectionState

    override val textMessages: Flow<String>
        get() = sendspinWsHandler.textMessages

    override val binaryMessages: Flow<ByteArray>
        get() = sendspinWsHandler.binaryMessages

    override suspend fun connect() {
        sendspinWsHandler.connect()
    }

    override suspend fun sendText(message: String) {
        sendspinWsHandler.sendText(message)
    }

    override suspend fun sendBinary(data: ByteArray) {
        sendspinWsHandler.sendBinary(data)
    }

    override suspend fun disconnect() {
        sendspinWsHandler.disconnect()
    }

    override fun close() {
        sendspinWsHandler.close()
    }
}
