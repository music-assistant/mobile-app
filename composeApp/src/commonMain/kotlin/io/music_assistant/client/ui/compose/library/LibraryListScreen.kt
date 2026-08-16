@file:OptIn(ExperimentalMaterial3Api::class)

package io.music_assistant.client.ui.compose.library

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.music_assistant.client.data.model.client.ClickContext
import io.music_assistant.client.data.model.client.LibraryFilters
import io.music_assistant.client.data.model.client.MediaType
import io.music_assistant.client.data.model.client.SortOption
import io.music_assistant.client.data.model.client.items.AppMediaItem
import io.music_assistant.client.settings.ViewMode
import io.music_assistant.client.ui.compose.common.DataState
import io.music_assistant.client.ui.compose.common.SelectOption
import io.music_assistant.client.ui.compose.common.ToastHost
import io.music_assistant.client.ui.compose.common.items.ItemSortChip
import io.music_assistant.client.ui.compose.common.rememberToastState
import io.music_assistant.client.ui.compose.common.viewmodel.ActionsViewModel
import io.music_assistant.client.ui.compose.item.ViewModeToggle
import io.music_assistant.client.ui.compose.item.ViewModeViewModel
import io.music_assistant.client.ui.compose.nav.TopBarLayout
import io.music_assistant.client.ui.compose.nav.TwoRowTopAppBar
import io.music_assistant.client.ui.compose.search.SearchInput
import musicassistantclient.composeapp.generated.resources.Res
import musicassistantclient.composeapp.generated.resources.common_back
import musicassistantclient.composeapp.generated.resources.library_quick_search
import musicassistantclient.composeapp.generated.resources.media_type_albums
import musicassistantclient.composeapp.generated.resources.media_type_artists
import musicassistantclient.composeapp.generated.resources.media_type_audiobooks
import musicassistantclient.composeapp.generated.resources.media_type_genres
import musicassistantclient.composeapp.generated.resources.media_type_playlists
import musicassistantclient.composeapp.generated.resources.media_type_podcasts
import musicassistantclient.composeapp.generated.resources.media_type_radio
import musicassistantclient.composeapp.generated.resources.media_type_tracks
import org.jetbrains.compose.resources.stringResource

@Composable
fun LibraryListScreen(
    libraryListViewModel: LibraryListViewModel,
    viewModeViewModel: ViewModeViewModel,
    contentPadding: PaddingValues,
    actionsViewModel: ActionsViewModel,
    onNavigateClick: (AppMediaItem) -> Unit,
    onGlobalSearch: (query: String) -> Unit,
    onBack: () -> Unit,
) {
    val toastState = rememberToastState()
    // Collect toasts
    LaunchedEffect(Unit) {
        actionsViewModel.toasts.collect { toast ->
            toastState.showToast(toast)
        }
    }
    LaunchedEffect(Unit) {
        libraryListViewModel.toasts.collect { toast ->
            toastState.showToast(toast)
        }
    }

    val state by libraryListViewModel.state.collectAsStateWithLifecycle()
    val providerOptions by libraryListViewModel.providerOptions.collectAsStateWithLifecycle()
    val genreOptions by libraryListViewModel.genreOptions.collectAsStateWithLifecycle()
    val viewMode by viewModeViewModel.viewModeFor(state.mediaType).collectAsStateWithLifecycle()

    TopBarLayout(
        topBar = {
            LibraryListTopBar(
                onBack = onBack,
                onToggleViewMode = { viewModeViewModel.toggleFor(state.mediaType) },
                viewMode = viewMode,
                searchQuery = state.searchQuery,
                onSearchQueryChanged = libraryListViewModel::onSearchQueryChanged,
                onSearch = libraryListViewModel::onSearch,
                onSortChanged = { libraryListViewModel.onSortChanged(it) },
                mediaType = state.mediaType,
                sortOption = state.sortOption,
                filters = state.filters,
                onFiltersChange = libraryListViewModel::setFilters,
                providerOptions = providerOptions,
                genreOptions = genreOptions,
                onLoadFilterOptions = libraryListViewModel::loadFilterOptions,
            )
        },
    ) {
        ItemList(
            data = state.dataState,
            onNavigateClick = onNavigateClick,
            onPlayClick = { item, option, radio, _ ->
                libraryListViewModel.onPlayClick(item, option, radio)
            },
            onCreatePlaylist = if (state.mediaType == MediaType.PLAYLIST) libraryListViewModel::createPlaylist else null,
            playlistActions = actionsViewModel,
            libraryActions = actionsViewModel,
            progressActions = actionsViewModel,
            contentPadding = contentPadding,
            viewMode = viewMode,
            hasMore = state.hasMore,
            isLoadingMore = state.isLoadingMore,
            onLoadMore = libraryListViewModel::loadMore,
            onGlobalSearch = state.searchQuery.let {
                if (it.isNotBlank()) {
                    { onGlobalSearch(it) }
                } else {
                    null
                }
            },
            clickContext = ClickContext.LIBRARY,
        )

        ToastHost(
            toastState = toastState,
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 48.dp),
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun LibraryListTopBar(
    onBack: () -> Unit,
    onToggleViewMode: () -> Unit,
    viewMode: ViewMode,
    searchQuery: String,
    onSearchQueryChanged: (String) -> Unit,
    onSearch: () -> Unit,
    onSortChanged: (SortOption) -> Unit,
    mediaType: MediaType,
    sortOption: SortOption,
    filters: LibraryFilters,
    onFiltersChange: (LibraryFilters) -> Unit,
    providerOptions: DataState<List<SelectOption<String>>>,
    genreOptions: DataState<List<SelectOption<Int>>>,
    onLoadFilterOptions: () -> Unit,
) {
    var showSearch by remember { mutableStateOf(searchQuery.isNotEmpty()) }

    Column {
        TwoRowTopAppBar(
            title = {
                if (showSearch) {
                    SearchInput(
                        query = searchQuery,
                        onQueryChanged = onSearchQueryChanged,
                        onSearch = onSearch,
                    )
                } else {
                    val title = when (mediaType) {
                        MediaType.ARTIST -> stringResource(
                            Res.string.media_type_artists,
                        )

                        MediaType.ALBUM -> stringResource(Res.string.media_type_albums)
                        MediaType.TRACK -> stringResource(Res.string.media_type_tracks)
                        MediaType.PLAYLIST -> stringResource(
                            Res.string.media_type_playlists,
                        )

                        MediaType.AUDIOBOOK -> stringResource(
                            Res.string.media_type_audiobooks,
                        )

                        MediaType.PODCAST -> stringResource(
                            Res.string.media_type_podcasts,
                        )

                        MediaType.RADIO -> stringResource(Res.string.media_type_radio)
                        MediaType.GENRE -> stringResource(Res.string.media_type_genres)
                        else -> {
                            throw IllegalArgumentException("Invalid MediaType for ItemListScreen!")
                        }
                    }

                    Text(title)
                }
            },
            navigationIcon = {
                if (!showSearch) {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            stringResource(Res.string.common_back),
                        )
                    }
                }
            },
            actions = {
                if (!showSearch) {
                    LibraryFilterAction(
                        mediaType = mediaType,
                        filters = filters,
                        providerOptions = providerOptions,
                        genreOptions = genreOptions,
                        onLoadOptions = onLoadFilterOptions,
                        onApply = {
                            onFiltersChange(it)
                        },
                    )
                }
                IconButton(
                    onClick = {
                        if (showSearch) {
                            onSearchQueryChanged("")
                            showSearch = false
                        } else {
                            showSearch = true
                        }
                    },
                ) {
                    Icon(
                        imageVector = if (showSearch) {
                            Icons.Default.SearchOff
                        } else {
                            Icons.Default.Search
                        },
                        contentDescription = stringResource(Res.string.library_quick_search),
                    )
                }
            },
            secondRow = {
                ItemSortChip(
                    sortOption = sortOption,
                    mediaType = mediaType,
                    onSortChanged = { onSortChanged(it) },
                )

                ViewModeToggle(
                    viewMode = viewMode,
                    onToggleViewMode = onToggleViewMode,
                )
            },
        )
    }
}
