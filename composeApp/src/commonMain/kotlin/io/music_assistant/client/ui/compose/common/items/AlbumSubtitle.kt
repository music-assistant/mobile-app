package io.music_assistant.client.ui.compose.common.items

/** What an album list or grid item shows under its title. */
enum class AlbumSubtitle {
    /** The album artists. Default everywhere the artist is not already known. */
    ARTISTS,

    /** The release year, or nothing if it is unknown. Used in an artist's own album lists. */
    YEAR,
}
