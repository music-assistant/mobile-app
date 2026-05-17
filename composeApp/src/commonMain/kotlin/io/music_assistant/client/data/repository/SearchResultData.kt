package io.music_assistant.client.data.repository

import io.music_assistant.client.data.model.client.items.Album
import io.music_assistant.client.data.model.client.items.Artist
import io.music_assistant.client.data.model.client.items.Audiobook
import io.music_assistant.client.data.model.client.items.Genre
import io.music_assistant.client.data.model.client.items.Playlist
import io.music_assistant.client.data.model.client.items.Podcast
import io.music_assistant.client.data.model.client.items.RadioStation
import io.music_assistant.client.data.model.client.items.Track

/**
 * Client-side, type-bucketed search results. Returned by
 * [MediaItemRepository.search] so callers never touch the raw
 * `data.model.server.SearchResult` DTO or the `mediaItemFactory` directly.
 */
data class SearchResultData(
    val artists: List<Artist> = emptyList(),
    val albums: List<Album> = emptyList(),
    val tracks: List<Track> = emptyList(),
    val playlists: List<Playlist> = emptyList(),
    val audiobooks: List<Audiobook> = emptyList(),
    val podcasts: List<Podcast> = emptyList(),
    val radios: List<RadioStation> = emptyList(),
    val genres: List<Genre> = emptyList(),
) {
    companion object {
        val EMPTY = SearchResultData()
    }
}
