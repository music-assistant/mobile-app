package io.music_assistant.client.data.play

import io.music_assistant.client.data.model.client.QueueOption

/** One `player_queues/play_media` request. All fields together are its identity. */
data class PlayRequest(
    val queueOrPlayerId: String,
    val media: List<String>,
    val option: QueueOption,
    val endlessMixMode: Boolean = false,
    /** Server-side "start playback from this item id within [media]" (play-from-here). */
    val startItem: String? = null,
)
