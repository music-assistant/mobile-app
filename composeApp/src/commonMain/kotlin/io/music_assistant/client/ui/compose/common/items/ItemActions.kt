package io.music_assistant.client.ui.compose.common.items

import io.music_assistant.client.data.model.client.items.AppMediaItem
import io.music_assistant.client.data.model.client.items.Playlist

interface PlaylistActions {
    suspend fun getEditablePlaylists(): List<Playlist>
    fun addToPlaylist(
        mediaItem: AppMediaItem,
        playlist: Playlist,
    )
}

interface LibraryActions {
    fun onLibraryClick(item: AppMediaItem)
    fun onFavoriteClick(item: AppMediaItem)
}

interface ProgressActions {
    fun onMarkPlayed(item: AppMediaItem)
    fun onMarkUnplayed(item: AppMediaItem)
}
