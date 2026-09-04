package io.music_assistant.client.player.sendspin.transport

import io.music_assistant.client.player.sendspin.connection.SendspinWsHandler
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * WebSocket implementation of SendspinTransport.
 * Wraps existing SendspinWsHandler to conform to the transport interface.
 */
class WebSocketSendspinTransport(
    private val sendspinWsHandler: SendspinWsHandler,
) : SendspinTransport {
    constructor(
        serverUrl: String,
        networkAvailable: StateFlow<Boolean>? = null,
    ) : this(SendspinWsHandler(serverUrl, networkAvailable))

    override val events: Flow<InboundTransportEvent>
        get() = sendspinWsHandler.events

    override val isSingleUse: Boolean = false

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
