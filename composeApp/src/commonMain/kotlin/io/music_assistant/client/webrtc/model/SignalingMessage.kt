package io.music_assistant.client.webrtc.model

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.listSerialDescriptor
import kotlinx.serialization.descriptors.serialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive

/**
 * Messages exchanged with the WebRTC signaling server.
 *
 * The signaling server (wss://signaling.music-assistant.io/ws) routes messages between
 * clients and Music Assistant gateways to establish WebRTC peer connections.
 */
@Serializable
sealed interface SignalingMessage {
    val type: String

    /**
     * Client → Signaling Server
     * Request connection to a Music Assistant server by Remote ID.
     */
    @Serializable
    @SerialName("connect-request")
    data class ConnectRequest(
        @SerialName("remoteId") val remoteId: String,
    ) : SignalingMessage {
        @SerialName("type")
        override val type: String = "connect-request"
    }

    /**
     * Signaling Server → Client
     * Connection accepted. Provides session ID, remote ID, and ICE servers.
     */
    @Serializable
    @SerialName("connected")
    data class Connected(
        @SerialName("sessionId") val sessionId: String? = null,
        @SerialName("remoteId") val remoteId: String? = null,
        @SerialName("iceServers") val iceServers: List<IceServer> = emptyList(),
    ) : SignalingMessage {
        @SerialName("type")
        override val type: String = "connected"
    }

    /**
     * Client → Signaling Server
     * SDP offer from client to gateway.
     */
    @Serializable
    @SerialName("offer")
    data class Offer(
        @SerialName("remoteId") val remoteId: String,
        @SerialName("sessionId") val sessionId: String,
        @SerialName("data") val data: SessionDescription,
    ) : SignalingMessage {
        @SerialName("type")
        override val type: String = "offer"
    }

    /**
     * Signaling Server → Client
     * SDP answer from gateway to client.
     */
    @Serializable
    @SerialName("answer")
    data class Answer(
        @SerialName("sessionId") val sessionId: String,
        @SerialName("data") val data: SessionDescription,
    ) : SignalingMessage {
        @SerialName("type")
        override val type: String = "answer"
    }

    /**
     * Bidirectional: Client ↔ Signaling Server ↔ Gateway
     * ICE candidate exchange for NAT traversal.
     * Note: remoteId is only present in outgoing messages (client → gateway).
     * Gateway responses omit remoteId and only include sessionId.
     */
    @Serializable
    @SerialName("ice-candidate")
    data class IceCandidate(
        @SerialName("remoteId") val remoteId: String? = null,
        @SerialName("sessionId") val sessionId: String,
        @SerialName("data") val data: IceCandidateData,
    ) : SignalingMessage {
        @SerialName("type")
        override val type: String = "ice-candidate"
    }

    /**
     * Signaling Server → Client
     * Error during connection or signaling process.
     */
    @Serializable
    @SerialName("error")
    data class Error(
        @SerialName("error") val error: String,
        @SerialName("sessionId") val sessionId: String? = null,
    ) : SignalingMessage {
        @SerialName("type")
        override val type: String = "error"
    }

    /**
     * Signaling Server → Client
     * Notification that the peer (gateway) disconnected.
     */
    @Serializable
    @SerialName("peer-disconnected")
    data class PeerDisconnected(
        @SerialName("sessionId") val sessionId: String? = null,
    ) : SignalingMessage {
        @SerialName("type")
        override val type: String = "peer-disconnected"
    }

    /**
     * Signaling Server → Client
     * Keepalive ping. Client must respond with Pong to stay connected.
     */
    @Serializable
    @SerialName("ping")
    data class Ping(
        @SerialName("type")
        override val type: String = "ping",
    ) : SignalingMessage

    /**
     * Client → Signaling Server
     * Keepalive pong response.
     */
    @Serializable
    @SerialName("pong")
    data class Pong(
        @SerialName("type")
        override val type: String = "pong",
    ) : SignalingMessage

    /**
     * Unknown message type (forward compatibility).
     * Received when server sends a message type we don't recognize.
     * Allows client to continue operating when server protocol is extended.
     */
    @Serializable
    data class Unknown(
        override val type: String,
    ) : SignalingMessage
}

/**
 * Deserializes `urls` from either a JSON array or a single string.
 * The public signaling server sends a single string, while Nabu Casa sends an array.
 */
private object FlexibleUrlListSerializer : KSerializer<List<String>> {
    override val descriptor: SerialDescriptor = listSerialDescriptor(serialDescriptor<String>())

    override fun deserialize(decoder: Decoder): List<String> {
        val jsonDecoder = decoder as JsonDecoder
        return when (val element = jsonDecoder.decodeJsonElement()) {
            is JsonArray -> element.map { it.jsonPrimitive.content }
            is JsonPrimitive -> listOf(element.content)
            else -> emptyList()
        }
    }

    override fun serialize(encoder: Encoder, value: List<String>) {
        encoder.beginCollection(descriptor, value.size).apply {
            value.forEachIndexed { index, s -> encodeStringElement(descriptor, index, s) }
            endStructure(descriptor)
        }
    }
}

/**
 * ICE server configuration for STUN/TURN.
 */
@Serializable
data class IceServer(
    @Serializable(with = FlexibleUrlListSerializer::class)
    @SerialName("urls") val urls: List<String>,
    @SerialName("username") val username: String? = null,
    @SerialName("credential") val credential: String? = null,
)

/**
 * Session Description Protocol (SDP) for WebRTC offer/answer.
 */
@Serializable
data class SessionDescription(
    @SerialName("sdp") val sdp: String,
    @SerialName("type") val type: String, // "offer" or "answer"
)

/**
 * ICE candidate data for NAT traversal.
 *
 * Example candidate string:
 * "candidate:0 1 UDP 2113937151 192.168.1.100 51472 typ host"
 */
@Serializable
data class IceCandidateData(
    @SerialName("candidate") val candidate: String,
    @SerialName("sdpMid") val sdpMid: String?,
    @SerialName("sdpMLineIndex") val sdpMLineIndex: Int?,
)
