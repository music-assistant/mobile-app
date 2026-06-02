package io.music_assistant.client.ui.compose.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import io.music_assistant.client.api.Request
import io.music_assistant.client.api.ServiceClient
import io.music_assistant.client.data.MainDataSource
import io.music_assistant.client.data.model.client.MediaType
import io.music_assistant.client.data.model.client.QueueOption
import io.music_assistant.client.data.model.client.SortConfig
import io.music_assistant.client.data.model.client.SortOption
import io.music_assistant.client.data.model.client.items.AppMediaItem
import io.music_assistant.client.data.model.client.items.Genre
import io.music_assistant.client.data.repository.MediaItemChange
import io.music_assistant.client.data.repository.MediaItemRepository
import io.music_assistant.client.settings.SettingsRepository
import io.music_assistant.client.settings.ViewMode
import io.music_assistant.client.ui.Timings
import io.music_assistant.client.ui.compose.common.DataState
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@OptIn(FlowPreview::class)
class ItemListViewModel(
    private val mediaType: MediaType,
    private val apiClient: ServiceClient,
    private val mainDataSource: MainDataSource,
    private val settingsRepository: SettingsRepository,
    private val mediaItemRepository: MediaItemRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(
        State(
            dataState = DataState.Loading(),
            mediaType = mediaType,
        ),
    )
    val state = _state.asStateFlow()

    init {
        viewModelScope.launch {
            _state.map { Triple(it.searchQuery, it.sortOption, it.onlyFavorites) }
                .distinctUntilChanged()
                .debounce { Timings.INPUT_DEBOUNCE }
                .collect { loadFirstPage() }
        }

        viewModelScope.launch {
            settingsRepository.viewMode(mediaType).collect { mode ->
                _state.update { it.copy(viewMode = mode) }
            }
        }

        // Reflect server-confirmed favorite changes on an already-open list, so
        // a toggle from any surface (player, context menu, another client)
        // updates the matching row without a refetch.
        viewModelScope.launch {
            mediaItemRepository.itemChanges.collect { change ->
                if (change is MediaItemChange.Updated) patchFavorite(change.item)
            }
        }
    }

    private fun patchFavorite(updated: AppMediaItem) {
        if (updated.mediaType != mediaType) return
        _state.update { st ->
            val data = st.dataState as? DataState.Data ?: return@update st
            val match = { item: AppMediaItem -> item.matchesIdentityOf(updated) }
            if (data.data.none(match)) return@update st
            val patched = if (st.onlyFavorites && updated.favorite != true) {
                // Dropped from the favorites filter; it would be gone on refetch.
                data.data.filterNot(match)
            } else {
                data.data.map { if (match(it)) updated else it }
            }
            st.copy(dataState = DataState.Data(patched))
        }
    }

    // A favorited non-library item returns from the server re-keyed under the
    // `library` provider with a new itemId, so fall back to provider-mapping
    // identity — the convention the other `itemChanges` consumers use.
    private fun AppMediaItem.matchesIdentityOf(other: AppMediaItem): Boolean =
        (mediaType == other.mediaType && provider == other.provider && itemId == other.itemId) ||
            hasAnyMappingFrom(other)

    fun toggleViewMode() {
        val current = settingsRepository.viewMode(mediaType).value
        settingsRepository.setViewMode(mediaType, current.toggled())
    }

    fun toggleFavorites() {
        _state.update {
            it.copy(onlyFavorites = !it.onlyFavorites)
        }
    }

    fun onSearchQueryChanged(query: String) {
        _state.update { it.copy(searchQuery = query) }
    }

    fun onSortChanged(sortOption: SortOption) {
        _state.update { it.copy(sortOption = sortOption) }
    }

    fun loadMore() {
        val currentState = _state.value

        // Don't load if already loading, no more data, or not in Data state
        if (currentState.isLoadingMore || !currentState.hasMore || currentState.dataState !is DataState.Data) {
            return
        }

        viewModelScope.launch {
            val searchQuery = currentState.searchQuery.takeIf { it.length >= 3 }
            val orderBy = currentState.sortOption.toServerString()
            val onlyFavorites = currentState.onlyFavorites

            _state.update {
                it.copy(isLoadingMore = true)
            }

            val request = getRequest(
                this@ItemListViewModel.mediaType,
                currentState.offset,
                orderBy,
                searchQuery,
                onlyFavorites,
            )
            val result = mediaItemRepository.fetchMediaItems(request)

            result.getOrNull()
                ?.let { newItems ->
                    val currentItems = currentState.dataState.data
                    val allItems = currentItems + newItems
                    updateStateWithData(
                        items = allItems,
                        offset = currentState.offset + PAGE_SIZE,
                        hasMore = newItems.size >= PAGE_SIZE,
                    )
                } ?: run {
                Logger.e("Error loading more for $mediaType:", result.exceptionOrNull())
                // Stop loading more on error
                _state.update {
                    it.copy(
                        isLoadingMore = false,
                        hasMore = false,
                    )
                }
            }
        }
    }

    private fun getRequest(
        mediaType: MediaType,
        offset: Int,
        orderBy: String,
        searchQuery: String?,
        onlyFavorites: Boolean,
    ): Request {
        val favorites = onlyFavorites.takeIf { it }
        val request = when (mediaType) {
            MediaType.ARTIST -> Request.Artist.listLibrary(
                limit = PAGE_SIZE,
                offset = offset,
                search = searchQuery,
                orderBy = orderBy,
                favorite = favorites,
            )

            MediaType.ALBUM -> Request.Album.listLibrary(
                limit = PAGE_SIZE,
                offset = offset,
                search = searchQuery,
                orderBy = orderBy,
                favorite = favorites,
            )

            MediaType.TRACK -> Request.Track.list(
                limit = PAGE_SIZE,
                offset = offset,
                search = searchQuery,
                orderBy = orderBy,
                favorite = favorites,
            )

            MediaType.PLAYLIST -> Request.Playlist.listLibrary(
                limit = PAGE_SIZE,
                offset = offset,
                search = searchQuery,
                orderBy = orderBy,
                favorite = favorites,
            )

            MediaType.AUDIOBOOK -> Request.Audiobook.listLibrary(
                limit = PAGE_SIZE,
                offset = offset,
                search = searchQuery,
                orderBy = orderBy,
                favorite = favorites,
            )

            MediaType.PODCAST -> Request.Podcast.listLibrary(
                limit = PAGE_SIZE,
                offset = offset,
                search = searchQuery,
                orderBy = orderBy,
                favorite = favorites,
            )

            MediaType.RADIO -> Request.RadioStation.listLibrary(
                limit = PAGE_SIZE,
                offset = offset,
                search = searchQuery,
                orderBy = orderBy,
                favorite = favorites,
            )

            MediaType.GENRE -> Request.Genre.listLibrary(
                limit = PAGE_SIZE,
                offset = offset,
                search = searchQuery,
                orderBy = orderBy,
                favorite = favorites,
            )

            else -> throw IllegalArgumentException("Invalid MediaType for ItemListViewModel!")
        }

        return request
    }

    fun createPlaylist(name: String) {
        viewModelScope.launch {
            apiClient.sendRequest(Request.Playlist.create(name))
        }
    }

    fun onPlayClick(
        item: AppMediaItem,
        option: QueueOption,
        radio: Boolean,
    ) {
        viewModelScope.launch {
            val queueId = mainDataSource.selectedPlayer?.queueOrPlayerId ?: return@launch

            item.mediaUri?.let { mediaUri ->
                apiClient.sendRequest(
                    Request.Library.play(
                        media = listOf(mediaUri),
                        queueOrPlayerId = queueId,
                        option = option,
                        radioMode = radio && item !is Genre,
                    ),
                )
            }
        }
    }

    private fun loadFirstPage() {
        viewModelScope.launch {
            val searchQuery = state.value.searchQuery.takeIf { it.length >= 0 }
            val orderBy = state.value.sortOption.toServerString()
            updateState(DataState.Loading())

            val request = getRequest(
                this@ItemListViewModel.mediaType,
                0,
                orderBy,
                searchQuery,
                state.value.onlyFavorites,
            )
            val result = mediaItemRepository.fetchMediaItems(request)

            result.getOrNull()
                ?.filter { it.mediaType == mediaType }
                ?.let { items ->
                    updateStateWithData(
                        items = items,
                        offset = PAGE_SIZE,
                        hasMore = items.size >= PAGE_SIZE,
                    )
                } ?: run {
                Logger.e("Error loading $mediaType:", result.exceptionOrNull())
                updateState(DataState.Error())
            }
        }
    }

    private fun updateState(dataState: DataState<List<AppMediaItem>>) {
        _state.update {
            it.copy(dataState = dataState)
        }
    }

    private fun updateStateWithData(
        items: List<AppMediaItem>,
        offset: Int,
        hasMore: Boolean,
    ) {
        val deduped = items.distinctBy { Triple(it.mediaType, it.provider, it.itemId) }
        _state.update {
            it.copy(
                dataState = DataState.Data(deduped),
                offset = offset,
                hasMore = hasMore,
                isLoadingMore = false,
            )
        }
    }

    companion object Companion {
        private const val PAGE_SIZE = 50
    }

    data class State(
        val dataState: DataState<List<AppMediaItem>>,
        val mediaType: MediaType,
        val isLoadingMore: Boolean = false,
        val hasMore: Boolean = true,
        val searchQuery: String = "",
        val sortOption: SortOption = SortConfig.defaultFor(mediaType),
        val viewMode: ViewMode = ViewMode.GRID,
        val offset: Int = 0,
        val onlyFavorites: Boolean = false,
    )
}
