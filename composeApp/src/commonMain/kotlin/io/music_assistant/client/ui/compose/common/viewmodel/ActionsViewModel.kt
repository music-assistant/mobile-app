package io.music_assistant.client.ui.compose.common.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.music_assistant.client.api.Request
import io.music_assistant.client.api.ServiceClient
import io.music_assistant.client.data.MainDataSource
import io.music_assistant.client.data.model.client.items.AppMediaItem
import io.music_assistant.client.data.model.client.items.Playlist
import io.music_assistant.client.data.repository.MediaItemRepository
import io.music_assistant.client.ui.compose.common.items.LibraryActions
import io.music_assistant.client.ui.compose.common.items.PlaylistActions
import io.music_assistant.client.ui.compose.common.items.ProgressActions
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import musicassistantclient.composeapp.generated.resources.Res
import musicassistantclient.composeapp.generated.resources.toast_added_to_playlist
import musicassistantclient.composeapp.generated.resources.toast_error_add_playlist
import musicassistantclient.composeapp.generated.resources.toast_error_mark_played
import musicassistantclient.composeapp.generated.resources.toast_error_mark_unplayed
import musicassistantclient.composeapp.generated.resources.toast_marked_played
import musicassistantclient.composeapp.generated.resources.toast_marked_unplayed
import musicassistantclient.composeapp.generated.resources.toast_no_uri
import org.jetbrains.compose.resources.getString

/**
 * VM that provides library and favorites actions for media items.
 * Can be used by any view that needs to add/remove items from library or favorites.
 */
class ActionsViewModel(
    private val apiClient: ServiceClient,
    private val dataSource: MainDataSource,
    private val mediaItemRepository: MediaItemRepository,
) : ViewModel(), PlaylistActions, LibraryActions, ProgressActions {
    private val _toasts = MutableSharedFlow<String>()
    val toasts = _toasts.asSharedFlow()

    /**
     * Toggles library status of the item.
     * Adds to library if not in library, removes if already in library.
     */
    override fun onLibraryClick(item: AppMediaItem) {
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
    override fun onFavoriteClick(item: AppMediaItem) {
        viewModelScope.launch {
            val newFavorite = item.favorite != true
            // Optimistic: the server's queue payload reports a stale `favorite`
            // for the now-playing track and clobbers the confirmed value, so the
            // UI is driven from this override until MediaItemUpdatedEvent reconciles.
            val result = if (newFavorite) {
                val uri = item.uri ?: return@launch
                dataSource.setFavoriteOverride(item, true)
                apiClient.sendRequest(Request.Library.addFavorite(uri))
            } else {
                dataSource.setFavoriteOverride(item, false)
                apiClient.sendRequest(
                    Request.Library.removeFavorite(item.itemId, item.mediaType),
                )
            }
            // Roll back to the pre-toggle value if the server rejected it.
            result.onFailure { dataSource.setFavoriteOverride(item, item.favorite) }
        }
    }

    override suspend fun getEditablePlaylists(): List<Playlist> =
        mediaItemRepository.fetchMediaItems(Request.Playlist.listLibrary())
            .getOrNull()
            ?.filterIsInstance<Playlist>()
            ?.filter { it.isEditable }
            ?: emptyList()

    override fun addToPlaylist(
        itemUri: String?,
        playlist: Playlist,
    ) {
        viewModelScope.launch {
            if (itemUri == null) {
                _toasts.emit(getString(Res.string.toast_no_uri))
                return@launch
            }
            apiClient.sendRequest(
                Request.Playlist.addTracks(
                    playlistId = playlist.itemId,
                    trackUris = listOf(itemUri),
                ),
            )
                .onSuccess {
                    _toasts.emit(getString(Res.string.toast_added_to_playlist, playlist.displayName))
                }
                .onFailure {
                    _toasts.emit(getString(Res.string.toast_error_add_playlist))
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
    override fun onMarkPlayed(item: AppMediaItem) {
        viewModelScope.launch {
            item.uri?.let { uri ->
                apiClient.sendRequest(Request.Library.markPlayed(uri))
                    .onSuccess { _toasts.emit(getString(Res.string.toast_marked_played)) }
                    .onFailure { _toasts.emit(getString(Res.string.toast_error_mark_played)) }
            }
        }
    }

    /**
     * Mark an audiobook or podcast episode as unplayed (resets progress).
     */
    override fun onMarkUnplayed(item: AppMediaItem) {
        viewModelScope.launch {
            item.uri?.let { uri ->
                apiClient.sendRequest(Request.Library.markUnplayed(uri))
                    .onSuccess { _toasts.emit(getString(Res.string.toast_marked_unplayed)) }
                    .onFailure { _toasts.emit(getString(Res.string.toast_error_mark_unplayed)) }
            }
        }
    }

    fun getProviderIcon(provider: String) = dataSource.providerIcon(provider)
}
