package io.music_assistant.client.data.play

import io.music_assistant.client.data.model.client.MediaType
import io.music_assistant.client.data.model.client.QueueOption

/** One `player_queues/play_media` request. All fields together are its identity. */
data class PlayRequest(
    val queueOrPlayerId: String,
    val media: List<String>,
    val option: QueueOption,
    val endlessMixMode: Boolean = false,
    /** Server-side "start playback from this item id within [media]" (play-from-here). */
    val startItem: String? = null,
    /** `shuffle=true`: the server shuffles the media before it plays. Only `play` and `replace` honor it. */
    val shuffle: Boolean = false,
    /** `start_from_beginning`: the server ignores the saved resume position of a podcast episode. */
    val startFromBeginning: Boolean = false,
) {
    /** True when [media] holds an audiobook or an audiobook series (`<provider>://<media type>/<id>`). */
    val playsAudiobook: Boolean
        get() = media.any { uri ->
            MediaType.fromServer(uri.substringAfter("://").substringBefore('/')) in AUDIOBOOK_TYPES
        }

    private companion object {
        val AUDIOBOOK_TYPES = setOf(MediaType.AUDIOBOOK, MediaType.COLLECTION)
    }
}
