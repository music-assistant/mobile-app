package io.music_assistant.sendspin.wire

import io.music_assistant.sendspin.api.AudioFormatSpec
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * Wire models of the encrypted Sendspin protocol. Shapes are frozen: the
 * server must keep seeing the same bytes, so change a field only with the spec.
 */
@Serializable
internal sealed interface SendspinMessage {
    val type: String
}

// MARK: - Proxy authentication (WebSocket via the MA proxy, before Noise)

@Serializable
internal data class ClientAuthMessage(
    override val type: String = "auth",
    val token: String,
    @SerialName("client_id") val clientId: String,
) : SendspinMessage

// MARK: - Noise establishment

@Serializable
internal data class ClientInitMessage(
    override val type: String = "client/init",
    val payload: ClientInitPayload,
) : SendspinMessage

@Serializable
internal data class ClientInitPayload(
    @SerialName("client_id") val clientId: String,
    val version: Int = 1,
    val suite: String,
)

@Serializable
internal data class ServerInitMessage(
    override val type: String = "server/init",
    val payload: ServerInitPayload,
) : SendspinMessage

@Serializable
internal data class ServerInitPayload(
    @SerialName("server_id") val serverId: String,
    val version: Int,
)

@Serializable
internal data class NoiseHandshakeMessage(
    override val type: String = "noise/handshake",
    val payload: NoiseHandshakePayload,
) : SendspinMessage

@Serializable
internal data class NoiseHandshakePayload(
    /** base64url-encoded (no padding) Noise handshake message bytes. */
    val data: String,
)

// MARK: - Hello and activation

@Serializable
internal data class EncryptedClientHelloMessage(
    override val type: String = "client/hello",
    val payload: EncryptedClientHelloPayload,
) : SendspinMessage

@Serializable
internal data class EncryptedClientHelloPayload(
    val name: String,
    @SerialName("device_info") val deviceInfo: EncryptedDeviceInfo? = null,
    @SerialName("trust_level") val trustLevel: String,
    @SerialName("supported_roles") val supportedRoles: List<VersionedRole>,
    @SerialName("player@v1_support") val playerV1Support: PlayerSupport? = null,
    @SerialName("supported_pair_methods") val supportedPairMethods: List<PairMethodDescriptor>,
    @SerialName("unpaired_access") val unpairedAccess: UnpairedAccess,
)

@Serializable
internal data class EncryptedDeviceInfo(
    @SerialName("product_name") val productName: String? = null,
    val manufacturer: String? = null,
    @SerialName("software_version") val softwareVersion: String? = null,
)

@Serializable
internal data class PairMethodDescriptor(val method: String)

@Serializable
internal data class UnpairedAccess(val enabled: Boolean)

@Serializable
internal enum class PlayerCommand {
    @SerialName("volume")
    VOLUME,

    @SerialName("mute")
    MUTE,
}

@Serializable
internal data class PlayerSupport(
    @SerialName("supported_formats") val supportedFormats: List<AudioFormatSpec>,
    @SerialName("buffer_capacity") val bufferCapacity: Int,
    @SerialName("supported_commands") val supportedCommands: List<PlayerCommand>,
)

@Serializable
internal data class EncryptedServerHelloMessage(
    override val type: String = "server/hello",
    val payload: EncryptedServerHelloPayload,
) : SendspinMessage

@Serializable
internal data class EncryptedServerHelloPayload(val name: String)

@Serializable
internal data class ServerActivateMessage(
    override val type: String = "server/activate",
    val payload: ServerActivatePayload,
) : SendspinMessage

@Serializable
internal data class ServerActivatePayload(
    val activities: List<String>,
    /** Required on the first activation; persists across later activations that omit it. */
    @SerialName("active_roles") val activeRoles: List<String>? = null,
    val pairing: ActivatePairing? = null,
)

@Serializable
internal data class ActivatePairing(
    val method: String,
    @SerialName("pin_length") val pinLength: Int? = null,
    val languages: List<String>? = null,
)

// MARK: - Pairing PSK flow

@Serializable
internal data class ClientPairFinalizeMessage(
    override val type: String = "client/pair-finalize",
    val payload: ClientPairFinalizePayload,
) : SendspinMessage

@Serializable
internal data class ClientPairFinalizePayload(
    @SerialName("long_term_psk") val longTermPsk: String,
)

@Serializable
internal data class PairAbortMessage(
    override val type: String = "pair/abort",
    val payload: PairAbortPayload,
) : SendspinMessage

@Serializable
internal data class PairAbortPayload(val reason: String)

// MARK: - Clock sync

@Serializable
internal data class ClientTimeMessage(
    override val type: String = "client/time",
    val payload: ClientTimePayload,
) : SendspinMessage

@Serializable
internal data class ClientTimePayload(
    @SerialName("client_transmitted") val clientTransmitted: Long,
)

@Serializable
internal data class ServerTimeMessage(
    override val type: String = "server/time",
    val payload: ServerTimePayload,
) : SendspinMessage

@Serializable
internal data class ServerTimePayload(
    @SerialName("client_transmitted") val clientTransmitted: Long,
    @SerialName("server_received") val serverReceived: Long,
    @SerialName("server_transmitted") val serverTransmitted: Long,
)

// MARK: - State

@Serializable
internal enum class PlayerStateValue {
    @SerialName("synchronized")
    SYNCHRONIZED,

    @SerialName("error")
    ERROR,
}

@Serializable
internal data class ClientStateMessage(
    override val type: String = "client/state",
    val payload: ClientStatePayload,
) : SendspinMessage

@Serializable
internal data class ClientStatePayload(
    val player: PlayerStateObject? = null,
    val available: Boolean? = null,
)

@Serializable
internal data class PlayerStateObject(val state: PlayerStateValue)

@Serializable
internal data class ServerStateMessage(
    override val type: String = "server/state",
    val payload: JsonElement? = null,
) : SendspinMessage

// MARK: - Stream

@Serializable
internal data class StreamStartMessage(
    override val type: String = "stream/start",
    val payload: StreamStartPayload,
) : SendspinMessage

@Serializable
internal data class StreamStartPayload(val player: StreamStartPlayer? = null)

@Serializable
internal data class StreamStartPlayer(
    val codec: String,
    @SerialName("sample_rate") val sampleRate: Int,
    val channels: Int,
    @SerialName("bit_depth") val bitDepth: Int,
    @SerialName("codec_header") val codecHeader: String? = null,
)

@Serializable
internal data class StreamMetadataMessage(
    override val type: String = "stream/metadata",
    val payload: StreamMetadataPayload,
) : SendspinMessage

@Serializable
internal data class StreamMetadataPayload(
    val title: String? = null,
    val artist: String? = null,
    val album: String? = null,
    @SerialName("artwork_url") val artworkUrl: String? = null,
)

@Serializable
internal data class GroupUpdateMessage(
    override val type: String = "group/update",
    val payload: GroupUpdatePayload,
) : SendspinMessage

@Serializable
internal data class GroupUpdatePayload(
    @SerialName("playback_state") val playbackState: String? = null,
    @SerialName("group_id") val groupId: String? = null,
    @SerialName("group_name") val groupName: String? = null,
)

@Serializable
internal data class SessionUpdateMessage(
    override val type: String = "session/update",
    val payload: SessionUpdatePayload,
) : SendspinMessage

@Serializable
internal data class SessionUpdatePayload(
    @SerialName("group_id") val groupId: String? = null,
    @SerialName("playback_state") val playbackState: String? = null,
    val metadata: SessionMetadata? = null,
)

@Serializable
internal data class SessionMetadata(
    val title: String? = null,
    val artist: String? = null,
    val album: String? = null,
    @SerialName("album_artist") val albumArtist: String? = null,
    val track: Int? = null,
    @SerialName("track_duration") val trackDuration: Int? = null,
    val year: Int? = null,
    @SerialName("playback_speed") val playbackSpeed: Double? = null,
    val repeat: String? = null,
    val shuffle: Boolean? = null,
    @SerialName("artwork_url") val artworkUrl: String? = null,
    val timestamp: Long? = null,
)

// MARK: - Commands

@Serializable
internal data class ServerCommandMessage(
    override val type: String = "server/command",
    val payload: ServerCommandPayload,
) : SendspinMessage

@Serializable
internal data class ServerCommandPayload(val player: PlayerCommandObject)

@Serializable
internal data class PlayerCommandObject(
    val command: String,
    val volume: Int? = null,
    val mute: Boolean? = null,
)

// MARK: - Goodbye

@Serializable
internal data class ClientGoodbyeMessage(
    override val type: String = "client/goodbye",
    val payload: GoodbyePayload? = null,
) : SendspinMessage

@Serializable
internal data class GoodbyePayload(val reason: String? = null)

/**
 * Wire reasons for `client/goodbye`, mirroring aiosendspin's `GoodbyeReason`.
 * [Shutdown] and [UserRequest] trigger immediate session teardown on the
 * server; [Restart] is a warm, reconnect-friendly disconnect (30 s grace).
 */
internal enum class GoodbyeReason(val wire: String) {
    Shutdown("shutdown"),
    Restart("restart"),
    UserRequest("user_request"),
}
