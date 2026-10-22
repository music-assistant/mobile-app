package io.music_assistant.sendspin.wire

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.decodeFromJsonElement

/** A server text message after exactly one parse. Handlers never touch JSON again. */
internal sealed interface ServerMessage {
    data object AuthOk : ServerMessage
    data class Hello(val serverName: String) : ServerMessage
    data class Activate(val payload: ServerActivatePayload) : ServerMessage
    data class Time(val payload: ServerTimePayload) : ServerMessage
    data class State(val payload: JsonElement?) : ServerMessage
    data class Command(val player: PlayerCommandObject) : ServerMessage
    data class StreamStart(val player: StreamStartPlayer?) : ServerMessage
    data object StreamEnd : ServerMessage
    data object StreamClear : ServerMessage
    data class StreamMetadata(val payload: StreamMetadataPayload) : ServerMessage
    data class SessionUpdate(val payload: SessionUpdatePayload) : ServerMessage
    data class GroupUpdate(val payload: GroupUpdatePayload) : ServerMessage
    data object PairFinalize : ServerMessage
    data class PairAbort(val reason: String?) : ServerMessage
    data object Unpair : ServerMessage
    data class NoiseHandshake(val data: String) : ServerMessage

    /** Any request whose type starts with `management/`; the handler validates the payload itself. */
    data class Management(val type: String, val payload: JsonObject?) : ServerMessage

    data class Unknown(val type: String) : ServerMessage

    /** Not a JSON object with a `type` string (and, when required, a `payload` object). */
    data class BadEnvelope(val reason: String) : ServerMessage

    /** A valid envelope whose payload does not decode into this client's model. */
    data class Malformed(val cause: Throwable) : ServerMessage
}

internal object WireCodec {
    /**
     * Never throws: unparseable input is a [ServerMessage.BadEnvelope] or
     * [ServerMessage.Malformed] value. [requirePayload] makes a missing `payload`
     * object a bad envelope (spec 1.0.0-rc1 encrypted messages).
     */
    fun parse(text: String, requirePayload: Boolean = false): ServerMessage {
        val element = try {
            SendspinJson.parseToJsonElement(text)
        } catch (e: SerializationException) {
            return ServerMessage.BadEnvelope("not JSON: ${e.message}")
        }
        val json = element as? JsonObject ?: return ServerMessage.BadEnvelope("not a JSON object")
        val type = (json["type"] as? JsonPrimitive)?.takeIf { it.isString }?.content
            ?: return ServerMessage.BadEnvelope("missing 'type' string")
        val payloadMissing = requirePayload && json["payload"] !is JsonObject
        if (payloadMissing) return ServerMessage.BadEnvelope("missing 'payload' object")
        return try {
            decode(type, json)
        } catch (e: SerializationException) {
            ServerMessage.Malformed(e)
        } catch (e: IllegalArgumentException) {
            ServerMessage.Malformed(e)
        }
    }

    inline fun <reified T : SendspinMessage> encode(message: T): String =
        SendspinJson.encodeToString(message)

    private fun decode(type: String, json: JsonObject): ServerMessage = when (type) {
        "auth_ok" -> ServerMessage.AuthOk
        "server/hello" -> ServerMessage.Hello(from<EncryptedServerHelloMessage>(json).payload.name)
        "server/activate" -> ServerMessage.Activate(from<ServerActivateMessage>(json).payload)
        "server/time" -> ServerMessage.Time(from<ServerTimeMessage>(json).payload)
        "server/state" -> ServerMessage.State(from<ServerStateMessage>(json).payload)
        "server/command" -> ServerMessage.Command(from<ServerCommandMessage>(json).payload.player)
        "stream/start" -> ServerMessage.StreamStart(from<StreamStartMessage>(json).payload.player)
        "stream/end" -> ServerMessage.StreamEnd
        "stream/clear" -> ServerMessage.StreamClear
        "stream/metadata" -> ServerMessage.StreamMetadata(from<StreamMetadataMessage>(json).payload)
        "session/update" -> ServerMessage.SessionUpdate(from<SessionUpdateMessage>(json).payload)
        "group/update" -> ServerMessage.GroupUpdate(from<GroupUpdateMessage>(json).payload)
        "server/pair-finalize" -> ServerMessage.PairFinalize
        "pair/abort" -> ServerMessage.PairAbort(from<PairAbortMessage>(json).payload.reason)
        "server/unpair" -> ServerMessage.Unpair
        "noise/handshake" -> ServerMessage.NoiseHandshake(from<NoiseHandshakeMessage>(json).payload.data)
        else -> if (type.startsWith("management/")) {
            ServerMessage.Management(type, json["payload"] as? JsonObject)
        } else {
            ServerMessage.Unknown(type)
        }
    }

    private inline fun <reified T> from(json: JsonObject): T =
        SendspinJson.decodeFromJsonElement(json)
}
