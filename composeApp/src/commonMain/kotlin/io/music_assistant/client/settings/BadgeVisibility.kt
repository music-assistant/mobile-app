package io.music_assistant.client.settings

/**
 * Which item badges the user wants to see, on the grid tiles and in the badge line of a row.
 * The explicit mark and the favorite heart are not part of it: they always show.
 *
 * @property progress the played state of an audiobook or a podcast episode.
 * @property type the media type chip.
 * @property source the provider icon (the music service or the library).
 */
data class BadgeVisibility(
    val progress: Boolean = true,
    val type: Boolean = true,
    val source: Boolean = true,
)
