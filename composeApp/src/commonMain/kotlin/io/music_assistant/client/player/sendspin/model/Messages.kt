@file:OptIn(InternalSerializationApi::class, ExperimentalSerializationApi::class)

package io.music_assistant.client.player.sendspin.model

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.PolymorphicKind
import kotlinx.serialization.descriptors.buildSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.double
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.intOrNull

// Base interface for all messages
@Serializable
sealed interface SendspinMessage {
    val type: String
}

// MARK: - Client Messages

@Serializable
data class ClientAuthMessage(
    override val type: String = "auth",
    val token: String,
    @SerialName("client_id") val clientId: String,
) : SendspinMessage

@Serializable
data class ClientHelloMessage(
    override val type: String = "client/hello",
    val payload: ClientHelloPayload,
) : SendspinMessage

@Serializable
data class ClientHelloPayload(
    @SerialName("client_id") val clientId: String,
    val name: String,
    @SerialName("device_info") val deviceInfo: DeviceInfo?,
    val version: Int,
    @SerialName("supported_roles") val supportedRoles: List<VersionedRole>,
    @SerialName("player@v1_support") val playerV1Support: PlayerSupport?,
)

@Serializable
data class DeviceInfo(
    @SerialName("model") val model: String?,
    @SerialName("model_id") val modelId: String?,
    @SerialName("manufacturer") val manufacturer: String?,
    @SerialName("manufacturer_id") val manufacturerId: String?,
    @SerialName("software_version") val softwareVersion: String?,
) {
    companion object {
        // Platform-specific implementation needed
        val current = DeviceInfo(
            model = "Mobile Application",
            modelId = "mobile_app",
            manufacturer = "Music Assistant",
            manufacturerId = "music_assistant",
            softwareVersion = "1.0.0", // TODO: Get actual app version from build config or similar
        )
    }
}

@Serializable
enum class PlayerCommand {
    @SerialName("volume")
    VOLUME,

    @SerialName("mute")
    MUTE,
}

@Serializable
data class PlayerSupport(
    @SerialName("supported_formats") val supportedFormats: List<AudioFormatSpec>,
    @SerialName("buffer_capacity") val bufferCapacity: Int,
    @SerialName("supported_commands") val supportedCommands: List<PlayerCommand>,
)

@Serializable
data class MetadataSupport(
    @SerialName("supported_picture_formats") val supportedPictureFormats: List<String> = emptyList(),
)

@Serializable
data class ServerHelloMessage(
    override val type: String = "server/hello",
    val payload: ServerHelloPayload,
) : SendspinMessage

@Serializable
enum class ConnectionReason {
    @SerialName("discovery")
    DISCOVERY,

    @SerialName("playback")
    PLAYBACK,
}

@Serializable
data class ServerHelloPayload(
    @SerialName("server_id") val serverId: String,
    val name: String,
    val version: Int,
    @SerialName("active_roles") val activeRoles: List<VersionedRole>,
    @SerialName("connection_reason") val connectionReason: ConnectionReason,
)

@Serializable
data class ClientTimeMessage(
    override val type: String = "client/time",
    val payload: ClientTimePayload,
) : SendspinMessage

@Serializable
data class ClientTimePayload(
    @SerialName("client_transmitted") val clientTransmitted: Long,
)

@Serializable
data class ServerTimeMessage(
    override val type: String = "server/time",
    val payload: ServerTimePayload,
) : SendspinMessage

@Serializable
data class ServerTimePayload(
    @SerialName("client_transmitted") val clientTransmitted: Long,
    @SerialName("server_received") val serverReceived: Long,
    @SerialName("server_transmitted") val serverTransmitted: Long,
)

// MARK: - State Messages

@Serializable
enum class PlayerStateValue {
    @SerialName("synchronized")
    SYNCHRONIZED,

    @SerialName("error")
    ERROR,
}

@Serializable
data class ClientStateMessage(
    override val type: String = "client/state",
    val payload: ClientStatePayload,
) : SendspinMessage

@Serializable
data class ClientStatePayload(
    val player: PlayerStateObject?,
)

@Serializable
data class ServerStateMessage(
    override val type: String = "server/state",
    val payload: JsonElement? = null,
) : SendspinMessage

@Serializable
data class PlayerStateObject(
    val state: PlayerStateValue,
)

// MARK: - Stream Messages

@Serializable
data class StreamStartMessage(
    override val type: String = "stream/start",
    val payload: StreamStartPayload,
) : SendspinMessage

@Serializable
data class StreamStartPayload(
    val player: StreamStartPlayer?,
    val artwork: StreamStartArtwork? = null,
    val visualizer: StreamStartVisualizer? = null,
)

@Serializable
data class StreamStartPlayer(
    val codec: String,
    @SerialName("sample_rate") val sampleRate: Int,
    val channels: Int,
    @SerialName("bit_depth") val bitDepth: Int,
    @SerialName("codec_header") val codecHeader: String? = null,
)

@Serializable
data class StreamStartArtwork(val dummy: Int = 0)

@Serializable
data class StreamStartVisualizer(val dummy: Int = 0)

@Serializable
data class StreamEndMessage(
    override val type: String = "stream/end",
) : SendspinMessage

@Serializable
data class GroupUpdateMessage(
    override val type: String = "group/update",
    val payload: GroupUpdatePayload,
) : SendspinMessage

@Serializable
data class GroupUpdatePayload(
    @SerialName("playback_state") val playbackState: String? = null,
    @SerialName("group_id") val groupId: String? = null,
    @SerialName("group_name") val groupName: String? = null,
)

// MARK: - Metadata Messages

@Serializable
data class StreamMetadataMessage(
    override val type: String = "stream/metadata",
    val payload: StreamMetadataPayload,
) : SendspinMessage

@Serializable
data class StreamMetadataPayload(
    val title: String?,
    val artist: String?,
    val album: String?,
    @SerialName("artwork_url") val artworkUrl: String?,
)

@Serializable
data class SessionUpdateMessage(
    override val type: String = "session/update",
    val payload: SessionUpdatePayload,
) : SendspinMessage

@Serializable
data class SessionUpdatePayload(
    @SerialName("group_id") val groupId: String?,
    @SerialName("playback_state") val playbackState: String?,
    val metadata: SessionMetadata?,
)

@Serializable
data class SessionMetadata(
    val title: String?,
    val artist: String?,
    val album: String?,
    @SerialName("album_artist") val albumArtist: String?,
    val track: Int?,
    @SerialName("track_duration") val trackDuration: Int?,
    val year: Int?,
    @SerialName("playback_speed") val playbackSpeed: Double?,
    val repeat: String?,
    val shuffle: Boolean?,
    @SerialName("artwork_url") val artworkUrl: String?,
    val timestamp: Long?,
)

@Serializable
data class StreamClearMessage(
    override val type: String = "stream/clear",
) : SendspinMessage

// MARK: - Command Messages

@Serializable
data class ClientCommandMessage(
    override val type: String = "client/command",
    val payload: CommandPayload,
) : SendspinMessage

@Serializable
data class ServerCommandMessage(
    override val type: String = "server/command",
    val payload: ServerCommandPayload,
) : SendspinMessage

@Serializable
data class ServerCommandPayload(
    val player: PlayerCommandObject,
)

@Serializable
data class PlayerCommandObject(
    val command: String,
    val volume: Int? = null,
    val mute: Boolean? = null,  // Server sends "mute", not "muted"
)

@Serializable
data class CommandPayload(
    val command: String,
    val value: CommandValue? = null,
)

@Serializable(with = CommandValueSerializer::class)
sealed class CommandValue {
    data class IntValue(val value: Int) : CommandValue()
    data class DoubleValue(val value: Double) : CommandValue()
    data class BoolValue(val value: Boolean) : CommandValue()
    data class StringValue(val value: String) : CommandValue()
}

object CommandValueSerializer : KSerializer<CommandValue> {
    override val descriptor = buildSerialDescriptor("CommandValue", PolymorphicKind.SEALED)

    override fun serialize(encoder: Encoder, value: CommandValue) {
        when (value) {
            is CommandValue.IntValue -> encoder.encodeInt(value.value)
            is CommandValue.DoubleValue -> encoder.encodeDouble(value.value)
            is CommandValue.BoolValue -> encoder.encodeBoolean(value.value)
            is CommandValue.StringValue -> encoder.encodeString(value.value)
        }
    }

    override fun deserialize(decoder: Decoder): CommandValue {
        val jsonDecoder = decoder as JsonDecoder
        val element = jsonDecoder.decodeJsonElement()

        return when {
            element is JsonPrimitive && element.isString ->
                CommandValue.StringValue(element.content)

            element is JsonPrimitive && element.booleanOrNull != null ->
                CommandValue.BoolValue(element.boolean)

            element is JsonPrimitive && element.intOrNull != null ->
                CommandValue.IntValue(element.int)

            element is JsonPrimitive && element.doubleOrNull != null ->
                CommandValue.DoubleValue(element.double)

            else -> throw SerializationException("Unknown CommandValue type")
        }
    }
}

// MARK: - Goodbye Messages

@Serializable
data class ClientGoodbyeMessage(
    override val type: String = "client/goodbye",
    val payload: GoodbyePayload? = null,
) : SendspinMessage

@Serializable
data class GoodbyePayload(
    val reason: String? = null,
)
