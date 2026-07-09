package io.music_assistant.client.data.model.client

import kotlinx.serialization.Serializable

/**
 * Persisted, per-[MediaType] set of library-list filters. Flat by design: each
 * field maps to a `library_items` server arg, irrelevant fields simply stay at
 * their default for a given media type (so [hasActive] needs no per-type
 * masking). New fields must be defaulted to keep stored JSON forward-compatible.
 *
 * Only fields the MA server actually applies are kept here. `played_only`/
 * `artist_type`/`explicit`/`without_collections` were dropped because the server
 * ignores them (they land in `**kwargs`); genre `content_type` is deferred.
 *
 * [providers] holds music-provider `instance_id`s; [genres] holds genre library
 * ids (ints). Both are server-side `library_items` filters — a persisted id that
 * no longer exists is still sent and simply matches nothing.
 */
@Serializable
data class LibraryFilters(
    val favorite: Boolean = false,
    val albumArtistsOnly: Boolean = false,
    val albumTypes: List<AlbumType> = emptyList(),
    val hideEmpty: GenreEmptyFilter = GenreEmptyFilter.DEFAULT,
    val genreMediaType: MediaType? = null,
    val providers: List<String> = emptyList(),
    val genres: List<Int> = emptyList(),
) {
    companion object {
        val DEFAULT = LibraryFilters()
    }
}

/** True when any filter deviates from its default — drives the filter button's active tint. */
val LibraryFilters.hasActive: Boolean get() = this != LibraryFilters.DEFAULT
