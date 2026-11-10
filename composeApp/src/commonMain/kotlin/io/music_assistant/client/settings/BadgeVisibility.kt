package io.music_assistant.client.settings

/**
 * Which item badges the user wants to see, on the grid tiles and in the badge line of a row.
 * The favorite heart is not part of it: it always shows.
 *
 * @property progress the played state of an audiobook or a podcast episode.
 * @property type the media type chip.
 * @property source the provider icon (the music service or the library).
 * @property explicit the explicit mark.
 */
data class BadgeVisibility(
    val progress: Boolean = true,
    val type: Boolean = true,
    val source: Boolean = true,
    val explicit: Boolean = true,
)
