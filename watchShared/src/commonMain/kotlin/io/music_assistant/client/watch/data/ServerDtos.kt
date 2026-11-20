package io.music_assistant.client.watch.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

/**
 * The slice of the Music Assistant wire format this watch speaks.
 *
 * Every field the server may rename or drop carries a default, and [maJson] tolerates unknown
 * keys and unknown enum values. A server that grows a field must never take down the decode of
 * the whole players or queues list.
 */
internal val maJson: Json = Json {
    encodeDefaults = true
    ignoreUnknownKeys = true
    coerceInputValues = true
    explicitNulls = false
}

@Serializable
internal data class RpcRequestDto(
    @SerialName("command") val command: String,
    @SerialName("args") val args: JsonObject? = null,
    @SerialName("message_id") val messageId: String,
)

@Serializable
internal data class ServerInfoDto(
    @SerialName("server_id") val serverId: String,
    @SerialName("name") val name: String? = null,
    @SerialName("server_version") val serverVersion: String? = null,
    @SerialName("schema_version") val schemaVersion: Int? = null,
)

@Serializable
internal data class ServerImageDto(
    @SerialName("path") val path: String = "",
    @SerialName("provider") val provider: String = "",
    @SerialName("remotely_accessible") val remotelyAccessible: Boolean = false,
    @SerialName("proxy_id") val proxyId: String? = null,
)

@Serializable
internal data class ServerMediaItemDto(
    @SerialName("name") val name: String = "",
    @SerialName("media_type") val mediaType: String? = null,
    @SerialName("image") val image: ServerImageDto? = null,
    @SerialName("artists") val artists: List<ServerMediaItemDto>? = null,
)

@Serializable
internal data class ServerPlayerMediaDto(
    @SerialName("title") val title: String? = null,
    @SerialName("artist") val artist: String? = null,
    @SerialName("album") val album: String? = null,
    @SerialName("image_url") val imageUrl: String? = null,
    @SerialName("duration") val duration: Double? = null,
    @SerialName("source_id") val sourceId: String? = null,
)

@Serializable
internal data class ServerSourceDto(
    @SerialName("id") val id: String? = null,
    @SerialName("name") val name: String? = null,
    @SerialName("can_next_previous") val canNextPrevious: Boolean? = null,
)

@Serializable
internal data class ServerPlayerDto(
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
    @SerialName("source_list") val sourceList: List<ServerSourceDto>? = null,
)

@Serializable
internal data class ServerQueueItemDto(
    @SerialName("queue_item_id") val queueItemId: String,
    @SerialName("name") val name: String? = null,
    @SerialName("duration") val duration: Double? = null,
    @SerialName("media_item") val mediaItem: ServerMediaItemDto? = null,
    @SerialName("image") val image: ServerImageDto? = null,
)

@Serializable
internal data class ServerQueueDto(
    @SerialName("queue_id") val queueId: String,
    @SerialName("available") val available: Boolean = false,
    @SerialName("items") val items: Int? = null,
    @SerialName("current_index") val currentIndex: Int? = null,
    @SerialName("ended") val ended: Boolean = false,
    @SerialName("elapsed_time") val elapsedTime: Double? = null,
    @SerialName("current_item") val currentItem: ServerQueueItemDto? = null,
)
