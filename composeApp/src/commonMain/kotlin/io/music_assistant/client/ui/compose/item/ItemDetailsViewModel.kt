package io.music_assistant.client.ui.compose.item

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import io.music_assistant.client.api.Request
import io.music_assistant.client.api.ServiceClient
import io.music_assistant.client.data.MainDataSource
import io.music_assistant.client.data.model.client.AppMediaItem
import io.music_assistant.client.data.model.client.AppMediaItem.Companion.toAppMediaItem
import io.music_assistant.client.data.model.client.AppMediaItem.Companion.toAppMediaItemList
import io.music_assistant.client.data.model.client.PlayableItem
import io.music_assistant.client.data.model.server.MediaType
import io.music_assistant.client.data.model.server.QueueOption
import io.music_assistant.client.data.model.server.ServerMediaItem
import io.music_assistant.client.data.model.server.events.MediaItemAddedEvent
import io.music_assistant.client.data.model.server.events.MediaItemDeletedEvent
import io.music_assistant.client.data.model.server.events.MediaItemUpdatedEvent
import io.music_assistant.client.settings.SettingsRepository
import io.music_assistant.client.ui.compose.common.DataState
import io.music_assistant.client.utils.SessionState
import io.music_assistant.client.utils.resultAs
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ItemDetailsViewModel(
    private val apiClient: ServiceClient,
    private val mainDataSource: MainDataSource,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    data class State(
        val connectionState: SessionState,
        val itemState: DataState<AppMediaItem>,
        val albumsState: DataState<List<AppMediaItem.Album>>,
        val playableItemsState: DataState<List<PlayableItem>>,
    )

    private val connectionState = apiClient.sessionState

    val serverUrl =
        apiClient.sessionState.map { (it as? SessionState.Connected)?.serverInfo?.baseUrl }

    private val _toasts = MutableSharedFlow<String>()
    val toasts = _toasts.asSharedFlow()

    val itemsRowMode = settingsRepository.itemsRowMode

    fun toggleItemsRowMode() {
        settingsRepository.setItemsRowMode(!settingsRepository.itemsRowMode.value)
    }

    private val _state = MutableStateFlow(
        State(
            connectionState = SessionState.Disconnected.Initial,
            itemState = DataState.Loading(),
            albumsState = DataState.Loading(),
            playableItemsState = DataState.Loading(),
        )
    )
    val state = _state.asStateFlow()

    init {
        viewModelScope.launch {
            connectionState.collect { connection ->
                _state.update { it.copy(connectionState = connection) }
            }
        }

        // Listen to real-time events for favorite updates
        viewModelScope.launch {
            apiClient.events.collect { event ->
                when (event) {
                    is MediaItemUpdatedEvent -> {
                        (_state.value.itemState as? DataState.Data)?.data?.let { current ->
                            event.data.takeIf { current.hasAnyMappingFrom(it) }
                                ?.toAppMediaItem()
                                ?.let { updatedItem ->
                                    _state.update {
                                        it.copy(itemState = DataState.Data(updatedItem))
                                    }
                                }
                        }

                        // Also update sub-items if they were updated
                        updateSubItemIfNeeded(event.data)
                    }

                    is MediaItemAddedEvent -> {
                        (_state.value.itemState as? DataState.Data)?.data?.let { current ->
                            event.data.takeIf { current.hasAnyMappingFrom(it) }
                                ?.toAppMediaItem()
                                ?.let { updatedItem ->
                                    _state.update {
                                        it.copy(itemState = DataState.Data(updatedItem))
                                    }
                                }
                        }

                        // Also update sub-items if they were updated
                        updateSubItemIfNeeded(event.data)
                    }

                    is MediaItemDeletedEvent -> {
                        (_state.value.itemState as? DataState.Data)?.data?.let { current ->
                            event.data.takeIf { current.hasAnyMappingFrom(it) }
                                // removing library provider from it
                                ?.let {
                                    it.providerMappings?.getOrNull(0)?.let { provider ->
                                        it.copy(
                                            itemId = provider.itemId,
                                            provider = provider.providerInstance,
                                            favorite = null,
                                            uri = "${provider.providerInstance}://${it.mediaType.name.lowercase()}/${provider.itemId}"

                                        )
                                    }
                                }
                                ?.toAppMediaItem()
                                ?.let { updatedItem ->
                                    _state.update {
                                        it.copy(itemState = DataState.Data(updatedItem))
                                    }
                                }
                        }

                        // Also update sub-items if they were updated
                        updateSubItemIfNeeded(event.data)
                    }

                    else -> Unit
                }
            }
        }
    }

    fun loadItem(itemId: String, mediaType: MediaType, providerId: String) {
        viewModelScope.launch {
            _state.update { it.copy(itemState = DataState.Loading()) }

            try {
                val item = getItemById(itemId, mediaType, providerId)
                if (item != null) {
                    _state.update { it.copy(itemState = DataState.Data(item)) }
                    loadSubItems(item)
                } else {
                    _state.update { it.copy(itemState = DataState.Error()) }
                }
            } catch (e: Exception) {
                Logger.e("Failed to load item", e)
                _state.update { it.copy(itemState = DataState.Error()) }
            }
        }
    }

    private suspend fun getItemById(
        itemId: String,
        mediaType: MediaType,
        providerId: String
    ): AppMediaItem? {
        val request = when (mediaType) {
            MediaType.ARTIST -> Request.Artist.get(itemId, providerId)
            MediaType.ALBUM -> Request.Album.get(itemId, providerId)
            MediaType.PLAYLIST -> Request.Playlist.get(itemId, providerId)
            MediaType.PODCAST -> Request.Podcast.get(itemId, providerId)
            MediaType.AUDIOBOOK -> Request.Audiobook.get(itemId, providerId)
            else -> return null
        }

        return apiClient.sendRequest(request)
            .resultAs<ServerMediaItem>()
            ?.toAppMediaItem()
    }

    private fun loadSubItems(item: AppMediaItem) {

        when (item) {
            is AppMediaItem.Artist -> {
                loadArtistAlbums(item.itemId, item.provider)
                loadArtistTracks(item.itemId, item.provider)
            }

            is AppMediaItem.Album -> {
                _state.update { it.copy(albumsState = DataState.NoData()) }
                loadAlbumTracks(item.itemId, item.provider)
            }

            is AppMediaItem.Playlist -> {
                _state.update { it.copy(albumsState = DataState.NoData()) }
                loadPlaylistTracks(item.itemId, item.provider)
            }

            is AppMediaItem.Podcast -> {
                _state.update { it.copy(albumsState = DataState.NoData()) }
                loadPodcastEpisodes(item.itemId, item.provider)
            }

            is AppMediaItem.Audiobook -> {
                _state.update { it.copy(albumsState = DataState.NoData()) }
                // Chapters come from the audiobook's metadata, not a separate API call
                _state.update {
                    it.copy(playableItemsState = DataState.NoData())
                }
            }

            else -> {
                _state.update {
                    it.copy(
                        albumsState = DataState.NoData(),
                        playableItemsState = DataState.NoData()
                    )
                }
            }
        }
    }

    private fun loadArtistAlbums(itemId: String, providerDomain: String) {
        viewModelScope.launch {
            _state.update { it.copy(albumsState = DataState.Loading()) }

            try {
                val albums = apiClient.sendRequest(
                    Request.Artist.getAlbums(
                        itemId = itemId,
                        providerInstanceIdOrDomain = providerDomain,
                        inLibraryOnly = false
                    )
                ).resultAs<List<ServerMediaItem>>()
                    ?.toAppMediaItemList()
                    ?.filterIsInstance<AppMediaItem.Album>()
                    ?: emptyList()

                _state.update { it.copy(albumsState = DataState.Data(albums)) }
            } catch (e: Exception) {
                Logger.e("Failed to load artist albums", e)
                _state.update { it.copy(albumsState = DataState.Error()) }
            }
        }
    }

    private fun loadArtistTracks(itemId: String, providerDomain: String) {
        viewModelScope.launch {
            _state.update { it.copy(playableItemsState = DataState.Loading()) }

            try {
                val tracks = apiClient.sendRequest(
                    Request.Artist.getTracks(
                        itemId = itemId,
                        providerInstanceIdOrDomain = providerDomain,
                        inLibraryOnly = false
                    )
                ).resultAs<List<ServerMediaItem>>()
                    ?.toAppMediaItemList()
                    ?.filterIsInstance<AppMediaItem.Track>()
                    ?: emptyList()

                _state.update { it.copy(playableItemsState = DataState.Data(tracks)) }
            } catch (e: Exception) {
                Logger.e("Failed to load artist tracks", e)
                _state.update { it.copy(playableItemsState = DataState.Error()) }
            }
        }
    }

    private fun loadAlbumTracks(itemId: String, provider: String) {
        viewModelScope.launch {
            _state.update { it.copy(playableItemsState = DataState.Loading()) }

            try {
                val tracks = apiClient.sendRequest(
                    Request.Album.getTracks(
                        itemId = itemId,
                        providerInstanceIdOrDomain = provider,
                        inLibraryOnly = false
                    )
                ).resultAs<List<ServerMediaItem>>()
                    ?.toAppMediaItemList()
                    ?.filterIsInstance<AppMediaItem.Track>()
                    ?: emptyList()

                _state.update { it.copy(playableItemsState = DataState.Data(tracks)) }
            } catch (e: Exception) {
                Logger.e("Failed to load album tracks", e)
                _state.update { it.copy(playableItemsState = DataState.Error()) }
            }
        }
    }

    private fun loadPlaylistTracks(itemId: String, provider: String) {
        viewModelScope.launch {
            _state.update { it.copy(playableItemsState = DataState.Loading()) }

            try {
                val tracks = apiClient.sendRequest(
                    Request.Playlist.getTracks(
                        itemId = itemId,
                        providerInstanceIdOrDomain = provider,
                        forceRefresh = null
                    )
                ).resultAs<List<ServerMediaItem>>()
                    ?.toAppMediaItemList()
                    ?.filterIsInstance<AppMediaItem.Track>()
                    ?: emptyList()

                _state.update { it.copy(playableItemsState = DataState.Data(tracks)) }
            } catch (e: Exception) {
                Logger.e("Failed to load playlist tracks", e)
                _state.update { it.copy(playableItemsState = DataState.Error()) }
            }
        }
    }

    private fun loadPodcastEpisodes(itemId: String, provider: String) {
        viewModelScope.launch {
            _state.update { it.copy(playableItemsState = DataState.Loading()) }

            try {
                val episodes = apiClient.sendRequest(
                    Request.Podcast.getEpisodes(
                        itemId = itemId,
                        providerInstanceIdOrDomain = provider,
                        inLibraryOnly = false
                    )
                ).resultAs<List<ServerMediaItem>>()
                    ?.toAppMediaItemList()
                    ?.filterIsInstance<AppMediaItem.PodcastEpisode>()
                    ?: emptyList()

                _state.update { it.copy(playableItemsState = DataState.Data(episodes)) }
            } catch (e: Exception) {
                Logger.e("Failed to load podcast episodes", e)
                _state.update { it.copy(playableItemsState = DataState.Error()) }
            }
        }
    }

    fun onPlayClick(option: QueueOption, radio: Boolean) {
        (_state.value.itemState as? DataState.Data)?.data?.let {
            onPlayClick(it, option, radio)
        }
    }

    fun onPlayClick(track: AppMediaItem, option: QueueOption, radio: Boolean) {
        viewModelScope.launch {
            track.uri?.let { uri ->
                mainDataSource.selectedPlayer?.queueOrPlayerId?.let { queueId ->
                    apiClient.sendRequest(
                        Request.Library.play(
                            media = listOf(uri),
                            queueOrPlayerId = queueId,
                            option = option,
                            radioMode = radio
                        )
                    )
                }
            }
        }
    }

    fun onChapterClick(chapterPosition: Int) {
        (_state.value.itemState as? DataState.Data)?.data?.let { item ->
            viewModelScope.launch {
                item.uri?.let { uri ->
                    mainDataSource.selectedPlayer?.queueOrPlayerId?.let { queueId ->
                        apiClient.sendRequest(
                            Request.Library.play(
                                media = listOf(uri),
                                queueOrPlayerId = queueId,
                                option = QueueOption.REPLACE,
                                radioMode = false,
                                startItem = chapterPosition.toString()
                            )
                        )
                    }
                }
            }
        }
    }

    fun reload() {
        // Reload tracks
        (state.value.itemState as? DataState.Data)?.data?.let {
            loadSubItems(it)
        }
    }

    private fun updateSubItemIfNeeded(serverItem: ServerMediaItem) {
        // Update albums list if this item is an album
        val albumsData = (_state.value.albumsState as? DataState.Data)?.data
        if (albumsData != null) {
            val updatedAlbums = albumsData.map { album ->
                if (album.itemId == serverItem.itemId) {
                    serverItem.toAppMediaItem() as? AppMediaItem.Album ?: album
                } else {
                    album
                }
            }
            _state.update { it.copy(albumsState = DataState.Data(updatedAlbums)) }
        }

        // Update tracks list if this item is a track
        val tracksData = (_state.value.playableItemsState as? DataState.Data)?.data
        if (tracksData != null) {
            val updatedTracks = tracksData.map { track ->
                if (track.itemId == serverItem.itemId) {
                    serverItem.toAppMediaItem() as? AppMediaItem.Track ?: track
                } else {
                    track
                }
            }
            _state.update { it.copy(playableItemsState = DataState.Data(updatedTracks)) }
        }
    }
}
