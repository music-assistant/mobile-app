package io.music_assistant.client.ui.compose.common.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.music_assistant.client.api.Request
import io.music_assistant.client.api.ServiceClient
import io.music_assistant.client.data.MainDataSource
import io.music_assistant.client.data.model.client.items.AppMediaItem
import io.music_assistant.client.data.model.client.items.Playlist
import io.music_assistant.client.data.repository.MediaItemRepository
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

/**
 * VM that provides library and favorites actions for media items.
 * Can be used by any view that needs to add/remove items from library or favorites.
 */
class ActionsViewModel(
    private val apiClient: ServiceClient,
    private val dataSource: MainDataSource,
    private val mediaItemRepository: MediaItemRepository,
) : ViewModel() {
    private val _toasts = MutableSharedFlow<String>()
    val toasts = _toasts.asSharedFlow()

    /**
     * Toggles library status of the item.
     * Adds to library if not in library, removes if already in library.
     */
    fun onLibraryClick(item: AppMediaItem) {
        viewModelScope.launch {
            if (item.isInLibrary) {
                apiClient.sendRequest(
                    Request.Library.remove(item.itemId, item.mediaType),
                )
            } else {
                item.uri?.let {
                    apiClient.sendRequest(Request.Library.add(it))
                }
            }
        }
    }

    /**
     * Sets exact or toggles favorite status of the item.
     */
    fun onFavoriteClick(item: AppMediaItem, newState: Boolean? = null) {
        viewModelScope.launch {
            if (newState ?: (item.favorite != true)) {
                item.uri?.let {
                    apiClient.sendRequest(Request.Library.addFavorite(it))
                }
            } else {
                apiClient.sendRequest(
                    Request.Library.removeFavorite(item.itemId, item.mediaType),
                )
            }
        }
    }

    suspend fun getEditablePlaylists(): List<Playlist> =
        mediaItemRepository.fetchMediaItems(Request.Playlist.listLibrary())
            .getOrNull()
            ?.filterIsInstance<Playlist>()
            ?.filter { it.isEditable }
            ?: emptyList()

    fun addToPlaylist(
        mediaItem: AppMediaItem,
        playlist: Playlist,
    ) {
        viewModelScope.launch {
            val itemUri = mediaItem.uri
                ?: run {
                    _toasts.emit("Media item has no URI")
                    return@launch
                }
            apiClient.sendRequest(
                Request.Playlist.addTracks(
                    playlistId = playlist.itemId,
                    trackUris = listOf(itemUri),
                ),
            )
                .map { "Added to ${playlist.displayName}" }
                .onSuccess { message ->
                    _toasts.emit(message)
                }
                .onFailure {
                    _toasts.emit("Error adding to playlist")
                }
        }
    }

    fun removeFromPlaylist(
        playlistId: String,
        position: Int,
        onSuccess: () -> Unit,
    ) {
        viewModelScope.launch {
            apiClient.sendRequest(
                Request.Playlist.removeTracks(
                    playlistId = playlistId,
                    positions = listOf(position + 1), // +1 because server uses 1-based indexing
                ),
            ).onSuccess {
                onSuccess()
            }
        }
    }

    /**
     * Mark an audiobook or podcast episode as fully played.
     */
    fun onMarkPlayed(item: AppMediaItem) {
        viewModelScope.launch {
            item.uri?.let { uri ->
                apiClient.sendRequest(Request.Library.markPlayed(uri))
                    .onSuccess { _toasts.emit("Marked as played") }
                    .onFailure { _toasts.emit("Error marking as played") }
            }
        }
    }

    /**
     * Mark an audiobook or podcast episode as unplayed (resets progress).
     */
    fun onMarkUnplayed(item: AppMediaItem) {
        viewModelScope.launch {
            item.uri?.let { uri ->
                apiClient.sendRequest(Request.Library.markUnplayed(uri))
                    .onSuccess { _toasts.emit("Marked as unplayed") }
                    .onFailure { _toasts.emit("Error marking as unplayed") }
            }
        }
    }

    fun getProviderIcon(provider: String) = dataSource.providerIcon(provider)

    data class PlaylistActions(
        val onLoadPlaylists: suspend () -> List<Playlist>,
        val onAddToPlaylist: (AppMediaItem, Playlist) -> Unit,
    )

    data class LibraryActions(
        val onLibraryClick: ((AppMediaItem) -> Unit),
        val onFavoriteClick: ((AppMediaItem) -> Unit),
    )

    data class ProgressActions(
        val onMarkPlayed: ((AppMediaItem) -> Unit),
        val onMarkUnplayed: ((AppMediaItem) -> Unit),
    )
}
