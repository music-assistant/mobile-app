package io.music_assistant.client.ui.compose.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import io.music_assistant.client.api.Request
import io.music_assistant.client.api.ServiceClient
import io.music_assistant.client.data.MainDataSource
import io.music_assistant.client.data.model.client.LibraryFilters
import io.music_assistant.client.data.model.client.MediaType
import io.music_assistant.client.data.model.client.QueueOption
import io.music_assistant.client.data.model.client.SortConfig
import io.music_assistant.client.data.model.client.SortOption
import io.music_assistant.client.data.model.client.items.AppMediaItem
import io.music_assistant.client.data.model.client.items.Genre
import io.music_assistant.client.data.model.server.ServerProviderInstance
import io.music_assistant.client.data.repository.MediaItemChange
import io.music_assistant.client.data.repository.MediaItemRepository
import io.music_assistant.client.settings.SettingsRepository
import io.music_assistant.client.settings.ViewMode
import io.music_assistant.client.ui.Timings
import io.music_assistant.client.ui.compose.common.DataState
import io.music_assistant.client.utils.resultAs
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import musicassistantclient.composeapp.generated.resources.Res
import musicassistantclient.composeapp.generated.resources.toast_error_create_playlist
import org.jetbrains.compose.resources.getString

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

    private val _toasts = MutableSharedFlow<String>()
    val toasts = _toasts.asSharedFlow()

    // Options for the provider/genre dynamic filters, loaded lazily the first time
    // the filter sheet asks for them (see loadFilterOptions).
    private val _providerOptions =
        MutableStateFlow<DataState<List<SelectOption<String>>>>(DataState.NoData())
    val providerOptions = _providerOptions.asStateFlow()
    private val _genreOptions =
        MutableStateFlow<DataState<List<SelectOption<Int>>>>(DataState.NoData())
    val genreOptions = _genreOptions.asStateFlow()
    private var optionsRequested = false

    init {
        viewModelScope.launch {
            _state.map {
                listOf(it.searchQuery, it.sortOption, it.filters)
            }
                .distinctUntilChanged()
                .debounce { Timings.INPUT_DEBOUNCE }
                .collect { loadFirstPage() }
        }

        viewModelScope.launch {
            settingsRepository.viewMode(mediaType).collect { mode ->
                _state.update { it.copy(viewMode = mode) }
            }
        }

        // Settings are the source of truth for filters; fold emissions into state
        // (Apply-based writes land here and trigger the debounced refetch above).
        viewModelScope.launch {
            settingsRepository.libraryFilters(mediaType).collect { filters ->
                _state.update { it.copy(filters = filters) }
            }
        }

        // Reflect server-confirmed library lifecycle changes on an already-open
        // list, so a mutation from any surface (this screen, context menu, another
        // client) keeps the list in sync without a manual refresh.
        viewModelScope.launch {
            mediaItemRepository.itemChanges.collect { change ->
                when (change) {
                    // In-place row patch, no refetch.
                    is MediaItemChange.Updated -> patchFavorite(change.item)
                    // Refetch first page so the new row lands in its sorted/filtered
                    // position (matches the "page refresh" semantics for creation).
                    is MediaItemChange.Added ->
                        if (change.item.mediaType == mediaType) loadFirstPage()
                    // Cheap in-place removal; ordering of the rest is unaffected.
                    is MediaItemChange.Deleted -> removeItem(change.item)
                }
            }
        }
    }

    private fun removeItem(deleted: AppMediaItem) {
        if (deleted.mediaType != mediaType) return
        _state.update { st ->
            val data = st.dataState as? DataState.Data ?: return@update st
            val remaining = data.data.filterNot { it.matchesIdentityOf(deleted) }
            if (remaining.size == data.data.size) return@update st
            st.copy(dataState = DataState.Data(remaining))
        }
    }

    private fun patchFavorite(updated: AppMediaItem) {
        if (updated.mediaType != mediaType) return
        _state.update { st ->
            val data = st.dataState as? DataState.Data ?: return@update st
            val match = { item: AppMediaItem -> item.matchesIdentityOf(updated) }
            if (data.data.none(match)) return@update st
            val patched = if (st.filters.favorite && updated.favorite != true) {
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

    // Persistence is the source of truth (like view mode); the settings flow
    // collector folds the new value back into state and triggers a refetch.
    fun setFilters(filters: LibraryFilters) {
        settingsRepository.setLibraryFilters(mediaType, filters)
    }

    // Idempotent: fetch options once per VM. The genres list has no provider/genre
    // filters, so skip the calls entirely there.
    fun loadFilterOptions() {
        if (optionsRequested || mediaType == MediaType.GENRE) return
        optionsRequested = true
        loadProviderOptions()
        loadGenreOptions()
    }

    private fun loadProviderOptions() {
        val feature = providerFeatureFor(mediaType) ?: return
        viewModelScope.launch {
            _providerOptions.update { DataState.Loading() }
            val providers = apiClient.sendRequest(Request.Library.providers())
                .resultAs<List<ServerProviderInstance>>()
            _providerOptions.update {
                providers
                    // Only music providers that are available and actually serve this
                    // media type (so e.g. a radio-only provider isn't offered on Artists).
                    ?.filter { it.type == "music" && it.available && feature in it.supportedFeatures }
                    ?.map { SelectOption(it.instanceId, it.name ?: it.domain ?: it.instanceId) }
                    ?.sortedBy { it.label.lowercase() }
                    ?.let { DataState.Data(it) }
                    ?: DataState.Error()
            }
        }
    }

    private fun loadGenreOptions() {
        viewModelScope.launch {
            _genreOptions.update { DataState.Loading() }
            val result = mediaItemRepository.fetchMediaItems(
                // media_type scopes to genres that have items of this list's type,
                // which also drops empty/irrelevant genres server-side.
                Request.Genre.listLibrary(
                    limit = GENRE_OPTIONS_LIMIT,
                    orderBy = "sort_name",
                    mediaType = mediaType.serverValue,
                ),
            )
            _genreOptions.update {
                result.getOrNull()
                    ?.filterIsInstance<Genre>()
                    // Only library genres (integer id) are valid `genre` filter values.
                    ?.mapNotNull { g -> g.itemId.toIntOrNull()?.let { SelectOption(it, g.displayName) } }
                    ?.let { DataState.Data(it) }
                    ?: DataState.Error()
            }
        }
    }

    // Library capability a provider must declare to be offered on this media type's
    // list. Null for types without a provider filter (e.g. GENRE).
    private fun providerFeatureFor(mediaType: MediaType): String? = when (mediaType) {
        MediaType.ARTIST -> "library_artists"
        MediaType.ALBUM -> "library_albums"
        MediaType.TRACK -> "library_tracks"
        MediaType.PLAYLIST -> "library_playlists"
        MediaType.RADIO -> "library_radios"
        MediaType.PODCAST -> "library_podcasts"
        MediaType.AUDIOBOOK -> "library_audiobooks"
        else -> null
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

            _state.update {
                it.copy(isLoadingMore = true)
            }

            val request = getRequest(
                this@ItemListViewModel.mediaType,
                currentState.offset,
                orderBy,
                searchQuery,
                currentState.filters,
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
        filters: LibraryFilters,
    ): Request {
        val favorite = filters.favorite.takeIf { it }
        val providers = filters.providers.takeIf { it.isNotEmpty() }
        val genres = filters.genres.takeIf { it.isNotEmpty() }
        val request = when (mediaType) {
            MediaType.ARTIST -> Request.Artist.listLibrary(
                limit = PAGE_SIZE,
                offset = offset,
                search = searchQuery,
                orderBy = orderBy,
                favorite = favorite,
                albumArtistsOnly = filters.albumArtistsOnly,
                providers = providers,
                genres = genres,
            )

            MediaType.ALBUM -> Request.Album.listLibrary(
                limit = PAGE_SIZE,
                offset = offset,
                search = searchQuery,
                orderBy = orderBy,
                favorite = favorite,
                albumTypes = filters.albumTypes.map { it.serverValue },
                providers = providers,
                genres = genres,
            )

            MediaType.TRACK -> Request.Track.list(
                limit = PAGE_SIZE,
                offset = offset,
                search = searchQuery,
                orderBy = orderBy,
                favorite = favorite,
                providers = providers,
                genres = genres,
            )

            MediaType.PLAYLIST -> Request.Playlist.listLibrary(
                limit = PAGE_SIZE,
                offset = offset,
                search = searchQuery,
                orderBy = orderBy,
                favorite = favorite,
                providers = providers,
                genres = genres,
            )

            MediaType.AUDIOBOOK -> Request.Audiobook.listLibrary(
                limit = PAGE_SIZE,
                offset = offset,
                search = searchQuery,
                orderBy = orderBy,
                favorite = favorite,
                providers = providers,
                genres = genres,
            )

            MediaType.PODCAST -> Request.Podcast.listLibrary(
                limit = PAGE_SIZE,
                offset = offset,
                search = searchQuery,
                orderBy = orderBy,
                favorite = favorite,
                providers = providers,
                genres = genres,
            )

            MediaType.RADIO -> Request.RadioStation.listLibrary(
                limit = PAGE_SIZE,
                offset = offset,
                search = searchQuery,
                orderBy = orderBy,
                favorite = favorite,
                providers = providers,
                genres = genres,
            )

            MediaType.GENRE -> Request.Genre.listLibrary(
                limit = PAGE_SIZE,
                offset = offset,
                search = searchQuery,
                orderBy = orderBy,
                favorite = favorite,
                providers = providers,
                hideEmpty = filters.hideEmpty.hideEmpty,
                mediaType = filters.genreMediaType?.serverValue,
            )

            else -> throw IllegalArgumentException("Invalid MediaType for ItemListViewModel!")
        }

        return request
    }

    // The list refresh is driven by the resulting MediaItemAddedEvent (see the
    // itemChanges collector), so success needs no explicit refetch here.
    fun createPlaylist(name: String) {
        viewModelScope.launch {
            apiClient.sendRequest(Request.Playlist.create(name))
                .onFailure {
                    Logger.e("Failed to create playlist", it)
                    _toasts.emit(getString(Res.string.toast_error_create_playlist))
                }
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
                state.value.filters,
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
        private const val GENRE_OPTIONS_LIMIT = 1000
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
        // Per-type filter set; only the fields applicable to [mediaType] are surfaced.
        val filters: LibraryFilters = LibraryFilters.DEFAULT,
    )
}
