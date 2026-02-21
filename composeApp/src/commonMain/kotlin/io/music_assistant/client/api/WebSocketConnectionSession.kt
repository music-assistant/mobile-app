package io.music_assistant.client.api

import co.touchlab.kermit.Logger
import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import io.ktor.client.plugins.websocket.receiveDeserialized
import io.ktor.client.plugins.websocket.sendSerialized
import io.ktor.websocket.close
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.JsonObject

/**
 * WebSocket implementation of ConnectionSession.
 *
 * Wraps a Ktor DefaultClientWebSocketSession and adapts it to the ConnectionSession interface.
 * Must be created inside a ws/wss {} block - the session is only valid within that scope.
 */
class WebSocketConnectionSession(
    private val session: DefaultClientWebSocketSession
) : ConnectionSession {
    private val logger = Logger.withTag("WebSocketConnectionSession")

    override val messages: Flow<JsonObject> = flow {
        try {
            while (true) emit(session.receiveDeserialized())
        } catch (e: ClosedReceiveChannelException) {
            logger.d { "WebSocket connection closed" }
        } catch (e: Exception) {
            logger.e(e) { "Error receiving WebSocket message" }
            throw e
        }
    }

    override suspend fun send(message: JsonObject) {
        try {
            session.sendSerialized(message)
        } catch (e: Exception) {
            logger.e(e) { "Error sending WebSocket message" }
            throw e
        }
    }

    override suspend fun close() {
        try {
            session.close()
            logger.d { "WebSocket session closed" }
        } catch (e: Exception) {
            logger.e(e) { "Error closing WebSocket session" }
        }
    }
}
