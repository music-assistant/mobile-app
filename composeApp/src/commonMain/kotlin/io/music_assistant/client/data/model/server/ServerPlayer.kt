package io.music_assistant.client.data.model.server

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ServerPlayer(
    @SerialName("player_id") val playerId: String,
    @SerialName("provider") val provider: String,
    @SerialName("type") val type: PlayerType,
    //@SerialName("name") val name: String,
    @SerialName("available") val available: Boolean = false,
    //@SerialName("device_info") val deviceInfo: DeviceInfo,
    @SerialName("supported_features") val supportedFeatures: List<String>,
    @SerialName("can_group_with") val canGroupWith: List<String>? = null,
    @SerialName("enabled") val enabled: Boolean,
    //@SerialName("elapsed_time") val elapsedTime: Double? = null,
    //@SerialName("elapsed_time_last_updated") val elapsedTimeLastUpdated: Double? = null,
    @SerialName("current_media") val currentMedia: PlayerMedia? = null,
    @SerialName("state") val state: PlayerState? = null,
    //@SerialName("powered") val powered: Boolean? = null,
    @SerialName("volume_level") val volumeLevel: Float? = null,
    @SerialName("volume_muted") val volumeMuted: Boolean? = null,
    @SerialName("group_members") val groupMembers: Set<String>? = null,
    @SerialName("static_group_members") val staticGroupMembers: Set<String>? = null,
    @SerialName("active_source") val activeSource: String? = null,
    //@SerialName("source_list") val sourceList: List<PlayerSource>,
    @SerialName("active_group") val activeGroup: String? = null,
    @SerialName("synced_to") val syncedTo: String? = null,
    @SerialName("group_volume") val groupVolume: Float? = null,
    @SerialName("display_name") val displayName: String,
    @SerialName("hidden") val hidden: Boolean? = null,
    //@SerialName("icon") val icon: String,
    //@SerialName("power_control") val powerControl: String,
    @SerialName("volume_control") val volumeControl: String,
    @SerialName("mute_control") val muteControl: String? = null,
    //@SerialName("enabled_by_default") val enabledByDefault: Boolean? = null,
    //@SerialName("needs_poll") val needsPoll: Boolean? = null,
    //@SerialName("poll_interval") val pollInterval: Int? = null,
    //@SerialName("extra_data") val extraData: Map<String, String>? = null,
    @SerialName("announcement_in_progress") val announcementInProgress: Boolean? = null,
)

//@Serializable
//data class DeviceInfo(
//    @SerialName("model") val model: String,
//    @SerialName("manufacturer") val manufacturer: String,
//    @SerialName("software_version") val softwareVersion: String? = null,
//    @SerialName("model_id") val modelId: String? = null,
//    @SerialName("manufacturer_id") val manufacturerId: String? = null,
//    @SerialName("ip_address") val ipAddress: String? = null,
//    @SerialName("mac_address") val macAddress: String? = null,
//    @SerialName("address") val address: String? = null,
//)

@Serializable
data class PlayerMedia(
//    @SerialName("uri") val uri: String? = null,
//    @SerialName("media_type") val mediaType: MediaType,
//    @SerialName("title") val title: String? = null,
//    @SerialName("artist") val artist: String? = null,
//    @SerialName("album") val album: String? = null,
//    @SerialName("image_url") val imageUrl: String? = null,
//    @SerialName("duration") val duration: Double? = null,
    @SerialName("queue_id") val queueId: String? = null,
//    @SerialName("queue_item_id") val queueItemId: String? = null,
//    @SerialName("custom_data") val customData: JsonObject? = null,
)

//@Serializable
//data class PlayerSource(
//    @SerialName("id") val id: String,
//    @SerialName("name") val name: String,
//    @SerialName("passive") val passive: Boolean,
//    @SerialName("can_play_pause") val canPlayPause: Boolean,
//    @SerialName("can_seek") val canSeek: Boolean,
//    @SerialName("can_next_previous") val canNextPrevious: Boolean
//)
