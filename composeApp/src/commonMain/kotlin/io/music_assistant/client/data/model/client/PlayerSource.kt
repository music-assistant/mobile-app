package io.music_assistant.client.data.model.client

/**
 * A source of a player: its Music Assistant queue, a live input such as Spotify Connect, or an
 * input of the device such as a TV input. The flags tell which commands the source accepts.
 */
data class PlayerSource(
    /** The player id for the queue, a uri for a live input, a provider value for a device input. */
    val id: String,
    val name: String = "",
    /** The user cannot select the source. It shows only while it is active. */
    val passive: Boolean = false,
    val canPlayPause: Boolean = false,
    val canSeek: Boolean = false,
    val canNextPrevious: Boolean = false,
)
