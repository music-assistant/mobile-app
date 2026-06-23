package io.music_assistant.client.ui.compose.common.items

import io.music_assistant.client.data.model.client.items.AppMediaItem
import io.music_assistant.client.data.model.client.items.Playlist

interface PlaylistActions {
    suspend fun getEditablePlaylists(): List<Playlist>
    fun addToPlaylist(
        itemUri: String?,
        playlist: Playlist,
    )

    /**
     * Creates a playlist and suspends until the server confirms it via the library
     * change stream (bounded by a timeout). Returns the created [Playlist], or null
     * on request failure or if the confirmation didn't arrive in time.
     */
    suspend fun createPlaylist(name: String): Playlist?
}

interface LibraryActions {
    fun onLibraryClick(item: AppMediaItem)
    fun onFavoriteClick(item: AppMediaItem)
}

interface ProgressActions {
    fun onMarkPlayed(item: AppMediaItem)
    fun onMarkUnplayed(item: AppMediaItem)
}
