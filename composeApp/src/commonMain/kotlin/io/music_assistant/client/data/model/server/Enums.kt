package io.music_assistant.client.data.model.server

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class PlayerState {
    @SerialName("idle")
    IDLE,

    @SerialName("paused")
    PAUSED,

    @SerialName("playing")
    PLAYING,
}

object PlayerFeature {
    const val POWER = "power"
    const val VOLUME_SET = "volume_set"
    const val VOLUME_MUTE = "volume_mute"
    const val PAUSE = "pause"
    const val SET_MEMBERS = "set_members"
    const val MULTI_DEVICE_DSP = "multi_device_dsp"
    const val SEEK = "seek"
    const val NEXT_PREVIOUS = "next_previous"
    const val PLAY_ANNOUNCEMENT = "play_announcement"
    const val ENQUEUE = "enqueue"
    const val GAPLESS_PLAYBACK = "gapless_playback"
    const val GAPLESS_DIFFERENT_SAMPLERATE = "gapless_different_samplerate"
    const val SELECT_SOURCE = "select_source"
}

@Serializable
enum class EventType {
    @SerialName("player_added")
    PLAYER_ADDED,

    @SerialName("player_updated")
    PLAYER_UPDATED,

    @SerialName("player_removed")
    PLAYER_REMOVED,

    @SerialName("player_settings_updated")
    PLAYER_SETTINGS_UPDATED,

    @SerialName("queue_added")
    QUEUE_ADDED,

    @SerialName("queue_updated")
    QUEUE_UPDATED,

    @SerialName("queue_items_updated")
    QUEUE_ITEMS_UPDATED,

    @SerialName("queue_time_updated")
    QUEUE_TIME_UPDATED,

    @SerialName("queue_settings_updated")
    QUEUE_SETTINGS_UPDATED,

    @SerialName("application_shutdown")
    SHUTDOWN,

    @SerialName("media_item_added")
    MEDIA_ITEM_ADDED,

    @SerialName("media_item_updated")
    MEDIA_ITEM_UPDATED,

    @SerialName("media_item_deleted")
    MEDIA_ITEM_DELETED,

    @SerialName("media_item_played")
    MEDIA_ITEM_PLAYED,

    @SerialName("providers_updated")
    PROVIDERS_UPDATED,

    @SerialName("player_config_updated")
    PLAYER_CONFIG_UPDATED,

    @SerialName("sync_tasks_updated")
    SYNC_TASKS_UPDATED,

    @SerialName("tasks_updated")
    TASKS_UPDATED,

    @SerialName("auth_session")
    AUTH_SESSION,

    @SerialName("connected")
    CONNECTED,

    @SerialName("disconnected")
    DISCONNECTED,

    @SerialName("*")
    ALL,
}
