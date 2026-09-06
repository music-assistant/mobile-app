package io.music_assistant.sendspin.wire

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** A server text message after exactly one parse. Handlers never touch JSON again. */
sealed interface ServerMessage {
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
    data class Malformed(val cause: Throwable) : ServerMessage
}

object WireCodec {
    /** Never throws: unparseable input is a [ServerMessage.Malformed] value. */
    fun parse(text: String): ServerMessage = try {
        val json = SendspinJson.parseToJsonElement(text).jsonObject
        val type = json["type"]?.jsonPrimitive?.contentOrNull
            ?: return ServerMessage.Malformed(SerializationException("missing 'type'"))
        decode(type, json)
    } catch (e: SerializationException) {
        ServerMessage.Malformed(e)
    } catch (e: IllegalArgumentException) {
        ServerMessage.Malformed(e)
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
