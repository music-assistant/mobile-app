package io.music_assistant.client.wear.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * The slice of the Music Assistant wire format this watch speaks.
 *
 * Every field the server may rename or drop carries a default, and [maJson] tolerates
 * unknown keys and unknown enum values. A server that grows a field must never take down
 * the decode of the whole players or queues list.
 */
val maJson: Json = Json {
    encodeDefaults = true
    ignoreUnknownKeys = true
    coerceInputValues = true
    explicitNulls = false
}

@Serializable
data class RpcRequestDto(
    @SerialName("command") val command: String,
    @SerialName("args") val args: kotlinx.serialization.json.JsonObject? = null,
    @SerialName("message_id") val messageId: String,
)

@Serializable
data class ServerInfoDto(
    @SerialName("server_id") val serverId: String,
    @SerialName("name") val name: String? = null,
    @SerialName("server_version") val serverVersion: String? = null,
    @SerialName("schema_version") val schemaVersion: Int? = null,
)

@Serializable
data class AuthUserDto(
    @SerialName("display_name") val displayName: String? = null,
    @SerialName("username") val username: String? = null,
)

@Serializable
data class AuthorizationDto(
    @SerialName("user") val user: AuthUserDto,
)

@Serializable
data class ServerImageDto(
    @SerialName("path") val path: String = "",
    @SerialName("provider") val provider: String = "",
    @SerialName("remotely_accessible") val remotelyAccessible: Boolean = false,
    @SerialName("proxy_id") val proxyId: String? = null,
)

@Serializable
data class ServerMediaItemDto(
    @SerialName("name") val name: String = "",
    @SerialName("media_type") val mediaType: String? = null,
    @SerialName("image") val image: ServerImageDto? = null,
    @SerialName("artists") val artists: List<ServerMediaItemDto>? = null,
)

@Serializable
data class ServerPlayerMediaDto(
    @SerialName("title") val title: String? = null,
    @SerialName("artist") val artist: String? = null,
    @SerialName("album") val album: String? = null,
    @SerialName("image_url") val imageUrl: String? = null,
    @SerialName("duration") val duration: Double? = null,
    @SerialName("source_id") val sourceId: String? = null,
)


@Serializable
data class ServerSourceList(
    @SerialName("id") val id: String? = null,
    @SerialName("name") val name: String? = null,
    @SerialName("passive") val passive: Boolean? = null,
    @SerialName("image_url") val imageUrl: String? = null,
    @SerialName("duration") val duration: Double? = null,
    @SerialName("source_id") val sourceId: String? = null,
    @SerialName("can_play_pause") val canPlayPause: Boolean? = null,
    @SerialName("can_seek") val canSeek: Boolean? = null,
    @SerialName("can_next_previous") val canNextPrevious: Boolean? = null,
    @SerialName("can_shuffle") val canShuffle: Boolean? = null,
    @SerialName("can_repeat") val canRepeat: Boolean? = null,
    @SerialName("shuffle_enabled") val shuffleEnabled: Boolean? = null,
    @SerialName("repeat_mode") val repeatMode: String? = null,
    @SerialName("account_id") val accountId: String? = null,
)

@Serializable
data class ServerPlayerDto(
    @SerialName("player_id") val playerId: String,
    @SerialName("display_name") val displayName: String = "",
    @SerialName("type") val type: String? = null,
    @SerialName("available") val available: Boolean = false,
    @SerialName("enabled") val enabled: Boolean = true,
    @SerialName("hidden") val hidden: Boolean? = null,
    @SerialName("hide_in_ui") val hideInUi: Boolean? = null,
    @SerialName("state") val state: String? = null,
    @SerialName("supported_features") val supportedFeatures: List<String> = emptyList(),
    @SerialName("volume_level") val volumeLevel: Float? = null,
    @SerialName("group_volume") val groupVolume: Float? = null,
    @SerialName("group_members") val groupMembers: Set<String>? = null,
    @SerialName("active_source") val activeSource: String? = null,
    @SerialName("current_media") val currentMedia: ServerPlayerMediaDto? = null,
    @SerialName("source_list") val sourceList: List<ServerSourceList>? = null,
)

@Serializable
data class ServerQueueItemDto(
    @SerialName("queue_item_id") val queueItemId: String,
    @SerialName("name") val name: String? = null,
    @SerialName("duration") val duration: Double? = null,
    @SerialName("media_item") val mediaItem: ServerMediaItemDto? = null,
    @SerialName("image") val image: ServerImageDto? = null,
)

@Serializable
data class ServerQueueDto(
    @SerialName("queue_id") val queueId: String,
    @SerialName("available") val available: Boolean = false,
    @SerialName("items") val items: Int? = null,
    @SerialName("current_index") val currentIndex: Int? = null,
    @SerialName("ended") val ended: Boolean = false,
    @SerialName("elapsed_time") val elapsedTime: Double? = null,
    @SerialName("current_item") val currentItem: ServerQueueItemDto? = null,
)
