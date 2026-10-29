package io.music_assistant.client.data.model.client

import kotlinx.serialization.Serializable

/**
 * Mirrors the server's `ArtistType`. `library_artist_types` lists the types the library holds, and
 * `music/artists/library_items` takes one as its `artist_type` filter. `UNKNOWN` is omitted:
 * [fromServer] gives null for it, and it is never a useful filter.
 */
@Serializable
enum class ArtistType(val serverValue: String) {
    SINGER("singer"),
    AUTHOR("author"),
    NARRATOR("narrator"),
    ;

    companion object {
        private val byServerValue = entries.associateBy { it.serverValue }
        fun fromServer(raw: String?): ArtistType? = raw?.let { byServerValue[it] }

        /** The types the Audiobooks list can show instead of the books. */
        val audiobookOptions = listOf(AUTHOR, NARRATOR)
    }
}
