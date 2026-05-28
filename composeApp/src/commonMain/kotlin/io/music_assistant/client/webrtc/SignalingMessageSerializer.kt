package io.music_assistant.client.webrtc

import co.touchlab.kermit.Logger
import io.music_assistant.client.webrtc.model.SignalingMessage
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.json.JsonContentPolymorphicSerializer
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Custom serializer for SignalingMessage that uses the "type" field
 * to determine which subclass to deserialize.
 *
 * This is required because the signaling protocol uses a "type" discriminator field
 * rather than kotlinx.serialization's default class discriminator.
 *
 * Forward compatible: unrecognized `type` values are deserialized as [SignalingMessage.Unknown]
 * instead of crashing, so a server that introduces new message types stays decodable.
 * Missing or null `type` is a protocol violation (Unknown itself requires a non-null type)
 * and throws — the caller in SignalingClient catches and logs it per-message.
 */
object SignalingMessageSerializer :
    JsonContentPolymorphicSerializer<SignalingMessage>(SignalingMessage::class) {
    private val logger = Logger.withTag("SignalingMessageSerializer")

    override fun selectDeserializer(element: JsonElement): DeserializationStrategy<SignalingMessage> {
        val type = element.jsonObject["type"]?.jsonPrimitive?.contentOrNull
            ?: throw IllegalArgumentException("Signaling message missing or null 'type' field")

        return when (type) {
            "connect-request" -> SignalingMessage.ConnectRequest.serializer()
            "connected" -> SignalingMessage.Connected.serializer()
            "offer" -> SignalingMessage.Offer.serializer()
            "answer" -> SignalingMessage.Answer.serializer()
            "ice-candidate" -> SignalingMessage.IceCandidate.serializer()
            "error" -> SignalingMessage.Error.serializer()
            "peer-disconnected" -> SignalingMessage.PeerDisconnected.serializer()
            "ping" -> SignalingMessage.Ping.serializer()
            "pong" -> SignalingMessage.Pong.serializer()
            else -> {
                logger.w { "Received unknown signaling message type: $type (forward compatibility fallback)" }
                SignalingMessage.Unknown.serializer()
            }
        }
    }
}
