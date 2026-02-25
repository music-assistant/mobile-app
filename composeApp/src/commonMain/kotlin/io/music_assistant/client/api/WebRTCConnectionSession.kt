package io.music_assistant.client.api

import co.touchlab.kermit.Logger
import io.music_assistant.client.utils.myJson
import io.music_assistant.client.webrtc.WebRTCConnectionManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.serialization.json.JsonObject

/**
 * WebRTC implementation of ConnectionSession.
 *
 * Wraps a WebRTCConnectionManager and adapts it to the ConnectionSession interface.
 * Handles conversion between String (WebRTC data channel) and JsonObject (ServiceClient).
 */
class WebRTCConnectionSession(
    private val manager: WebRTCConnectionManager
) : ConnectionSession {
    private val logger = Logger.withTag("WebRTCConnectionSession")

    override val messages: Flow<JsonObject> = manager.incomingMessages.mapNotNull { jsonString ->
        try {
            myJson.decodeFromString<JsonObject>(jsonString)
        } catch (e: Exception) {
            logger.e(e) { "Failed to parse incoming WebRTC message: ${jsonString.take(200)}" }
            null
        }
    }

    override suspend fun send(message: JsonObject) {
        try {
            val jsonString = myJson.encodeToString(JsonObject.serializer(), message)
            manager.send(jsonString)
        } catch (e: Exception) {
            logger.e(e) { "Failed to send WebRTC message" }
            throw e
        }
    }

    override suspend fun close() {
        try {
            manager.disconnect()
            logger.d { "WebRTC connection closed" }
        } catch (e: Exception) {
            logger.e(e) { "Error closing WebRTC connection" }
        }
    }
}
