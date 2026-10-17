package io.music_assistant.client.data.model.server

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Server-side queue payload. Non-critical fields default to safe values so an
 * older/newer server omitting a field doesn't abort the whole
 * `List<ServerQueue>` decode — one missing field on one queue would otherwise
 * throw `MissingFieldException` and take down the whole queue-listing RPC.
 */
@Serializable
data class ServerQueue(
    @SerialName("queue_id") val queueId: String,
    // @SerialName("active") val active: Boolean,
    // @SerialName("display_name") val displayName: String,
    @SerialName("available") val available: Boolean = false,
    @SerialName("items") val items: Int? = null,
    @SerialName("shuffle_enabled") val shuffleEnabled: Boolean = false,
    @SerialName("repeat_mode") val repeatMode: String? = null,
    /**
     * Autoplay, across the 2.10 rename. Servers from 2.10 on send [autoplayEnabled]; older
     * ones only the deprecated [dontStopTheMusicEnabled]. Null on both means the server has
     * no autoplay support at all, which is what gates the UI. Read modern-first; note the
     * write path deliberately still uses the old command (see `Request.Queue`).
     */
    @SerialName("autoplay_enabled") val autoplayEnabled: Boolean? = null,
    @SerialName("dont_stop_the_music_enabled") val dontStopTheMusicEnabled: Boolean? = null,
    @SerialName("is_dynamic") val isDynamic: Boolean = false,
    /**
     * Crossfade on the queue. Null means the server has no crossfade support at all, which
     * is what gates the UI. Unlike autoplay, this key was never renamed and needs no legacy
     * fallback: the server keeps an `alias=True` shim for every command it renames, and
     * there is no such shim for `player_queues/crossfade`. Do not mirror autoplay's
     * read-modern/write-legacy split here.
     */
    @SerialName("crossfade_enabled") val crossfadeEnabled: Boolean? = null,
    @SerialName("current_index") val currentIndex: Int? = null,
    // @SerialName("index_in_buffer") val indexInBuffer: Int? = null,
    @SerialName("elapsed_time") val elapsedTime: Double? = null,
    /**
     * Unix epoch seconds (UTC) when [elapsedTime] was last recomputed.
     * Drives the staleness gate. Nullable so a missing field can't silently
     * drop subsequent legitimate events.
     */
    @SerialName("elapsed_time_last_updated") val elapsedTimeLastUpdated: Double? = null,
    /**
     * Current playback speed (1.0 = normal). Nullable on purpose: it doubles as a
     * feature-detect gate — servers without variable-speed support omit the field,
     * so `null` means "speed control unavailable" and the UI hides the speed button.
     */
    @SerialName("playback_speed") val playbackSpeed: Double? = null,
    /**
     * Audio overlay: a looping sound effect the server mixes into the queue stream. Null
     * [overlayEnabled] means the server has no overlay support, which gates the UI.
     * [overlaySource] stays set while the overlay is off, so it can be re-enabled as is.
     * [overlayVolume] is relative to the music in percent (100 = equally loud).
     */
    @SerialName("overlay_enabled") val overlayEnabled: Boolean? = null,
    @SerialName("overlay_source") val overlaySource: ServerMediaItem? = null,
    @SerialName("overlay_volume") val overlayVolume: Int? = null,
    // @SerialName("state") val state: PlayerState,
    @SerialName("current_item") val currentItem: ServerQueueItem? = null,
    // @SerialName("next_item") val nextItem: QueueItem? = null,
    @SerialName("radio_source") val radioSource: List<ServerMediaItem>? = null,
    // @SerialName("flow_mode") val flowMode: Boolean,
    // @SerialName("resume_pos") val resumePos: Double?
    @SerialName("extra_attributes") val extraAttributes: ServerQueueExtraAttributes? = null,
)

/** The keys of the free-form `extra_attributes` map that the client reads; the rest are ignored. */
@Serializable
data class ServerQueueExtraAttributes(
    /**
     * True while the server runs a play action on the queue (`play_media`, `play_index`, ...),
     * from any client. The server pushes a queue update when it turns on and when it turns off.
     */
    @SerialName("play_action_in_progress") val playActionInProgress: Boolean = false,
)
