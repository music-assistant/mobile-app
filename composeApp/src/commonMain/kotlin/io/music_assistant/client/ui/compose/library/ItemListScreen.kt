@file:OptIn(ExperimentalMaterial3Api::class)

package io.music_assistant.client.ui.compose.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import compose.icons.TablerIcons
import compose.icons.tablericons.Plus
import io.music_assistant.client.data.model.client.ClickContext
import io.music_assistant.client.data.model.client.MediaType
import io.music_assistant.client.data.model.client.SortConfig
import io.music_assistant.client.data.model.client.SortOption
import io.music_assistant.client.data.model.client.items.AppMediaItem
import io.music_assistant.client.settings.ViewMode
import io.music_assistant.client.ui.compose.common.DataState
import io.music_assistant.client.ui.compose.common.SortChip
import io.music_assistant.client.ui.compose.common.ToastHost
import io.music_assistant.client.ui.compose.common.ToastState
import io.music_assistant.client.ui.compose.common.clearFocusOnScroll
import io.music_assistant.client.ui.compose.common.items.LibraryActions
import io.music_assistant.client.ui.compose.common.items.PlayHandler
import io.music_assistant.client.ui.compose.common.items.PlaylistActions
import io.music_assistant.client.ui.compose.common.items.ProgressActions
import io.music_assistant.client.ui.compose.common.items.ProvideClickActions
import io.music_assistant.client.ui.compose.common.rememberToastState
import io.music_assistant.client.ui.compose.common.viewmodel.ActionsViewModel
import io.music_assistant.client.ui.compose.nav.Screen
import io.music_assistant.client.ui.compose.nav.TwoRowTopAppBar
import musicassistantclient.composeapp.generated.resources.Res
import musicassistantclient.composeapp.generated.resources.cd_add_playlist
import musicassistantclient.composeapp.generated.resources.cd_toggle_view_mode
import musicassistantclient.composeapp.generated.resources.common_back
import musicassistantclient.composeapp.generated.resources.common_cancel
import musicassistantclient.composeapp.generated.resources.common_clear
import musicassistantclient.composeapp.generated.resources.common_create
import musicassistantclient.composeapp.generated.resources.filter_favorites
import musicassistantclient.composeapp.generated.resources.library_empty
import musicassistantclient.composeapp.generated.resources.library_error
import musicassistantclient.composeapp.generated.resources.library_quick_search
import musicassistantclient.composeapp.generated.resources.library_search_global
import musicassistantclient.composeapp.generated.resources.media_type_albums
import musicassistantclient.composeapp.generated.resources.media_type_artists
import musicassistantclient.composeapp.generated.resources.media_type_audiobooks
import musicassistantclient.composeapp.generated.resources.media_type_genres
import musicassistantclient.composeapp.generated.resources.media_type_playlists
import musicassistantclient.composeapp.generated.resources.media_type_podcasts
import musicassistantclient.composeapp.generated.resources.media_type_radio
import musicassistantclient.composeapp.generated.resources.media_type_tracks
import musicassistantclient.composeapp.generated.resources.playlist_add_new
import musicassistantclient.composeapp.generated.resources.playlist_create_title
import musicassistantclient.composeapp.generated.resources.playlist_name_label
import org.jetbrains.compose.resources.stringResource

@Composable
fun ItemListScreen(
    itemListViewModel: ItemListViewModel,
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

    val state by itemListViewModel.state.collectAsStateWithLifecycle()

    Screen(
        topBar = {
            ItemListTopBar(
                onBack = onBack,
                onToggleViewMode = itemListViewModel::toggleViewMode,
                viewMode = state.viewMode,
                searchQuery = state.searchQuery,
                onSearchQueryChanged = {
                    itemListViewModel.onSearchQueryChanged(it)
                },
                onSortChanged = { itemListViewModel.onSortChanged(it) },
                mediaType = state.mediaType,
                sortOption = state.sortOption,
                onlyFavorites = state.onlyFavorites,
                onToggleFavorites = itemListViewModel::toggleFavorites,
            )
        },
    ) {
        var showCreatePlaylistDialog by rememberSaveable { mutableStateOf(false) }
        ProvideClickActions(ClickContext.LIBRARY) {
        ItemList(
            showCreatePlaylistDialog = showCreatePlaylistDialog,
            toastState = toastState,
            onNavigateClick = onNavigateClick,
            onGlobalSearch = onGlobalSearch,
            searchQuery = state.searchQuery,
            onPlayClick = { item, option, radio, _ ->
                itemListViewModel.onPlayClick(item, option, radio)
            },
            onCreatePlaylistClick = { showCreatePlaylistDialog = true },
            onLoadMore = { itemListViewModel.loadMore() },
            onDismissCreatePlaylistDialog = { showCreatePlaylistDialog = false },
            onCreatePlaylist = itemListViewModel::createPlaylist,
            playlistActions = actionsViewModel,
            libraryActions = actionsViewModel,
            progressActions = actionsViewModel,
            contentPadding = contentPadding,
            dataState = state.dataState,
            mediaType = state.mediaType,
            isLoadingMore = state.isLoadingMore,
            hasMore = state.hasMore,
            viewMode = state.viewMode,
        )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ItemListTopBar(
    onBack: () -> Unit,
    onToggleViewMode: () -> Unit,
    viewMode: ViewMode,
    searchQuery: String,
    onSearchQueryChanged: (String) -> Unit,
    onSortChanged: (SortOption) -> Unit,
    mediaType: MediaType,
    sortOption: SortOption,
    onlyFavorites: Boolean,
    onToggleFavorites: () -> Unit,
) {
    var showSearch by remember { mutableStateOf(searchQuery.isNotEmpty()) }

    Column {
        TwoRowTopAppBar(
            title = {
                if (showSearch) {
                    val focusRequester = remember { FocusRequester() }
                    LaunchedEffect(Unit) {
                        focusRequester.requestFocus()
                    }

                    Surface(
                        shape = SearchBarDefaults.inputFieldShape,
                        color = SearchBarDefaults.colors().containerColor,
                        contentColor = contentColorFor(SearchBarDefaults.colors().containerColor),
                        tonalElevation = SearchBarDefaults.TonalElevation,
                        shadowElevation = SearchBarDefaults.ShadowElevation,
                    ) {
                        SearchBarDefaults.InputField(
                            modifier = Modifier.focusRequester(focusRequester),
                            state = TextFieldState(initialText = searchQuery),
                            onSearch = { onSearchQueryChanged(it) },
                            expanded = false,
                            onExpandedChange = {},
                            placeholder = {
                                Text(stringResource(Res.string.library_quick_search))
                            },
                            trailingIcon = if (searchQuery.isNotEmpty()) {
                                {
                                    IconButton(onClick = { onSearchQueryChanged("") }) {
                                        Icon(
                                            Icons.Default.Clear,
                                            contentDescription = stringResource(Res.string.common_clear),
                                        )
                                    }
                                }
                            } else {
                                null
                            },
                        )
                    }
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
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    FilterChip(
                        selected = onlyFavorites,
                        onClick = onToggleFavorites,
                        label = { Text(stringResource(Res.string.filter_favorites)) },
                    )

                    Row {
                        SortChip(
                            currentSort = sortOption,
                            availableFields = SortConfig.fieldsFor(mediaType),
                            onSortChanged = { onSortChanged(it) },
                        )

                        IconButton(onClick = onToggleViewMode) {
                            Icon(
                                imageVector = when (viewMode) {
                                    ViewMode.LIST -> Icons.Default.GridView
                                    ViewMode.GRID -> Icons.AutoMirrored.Filled.ViewList
                                },
                                contentDescription = stringResource(Res.string.cd_toggle_view_mode),
                            )
                        }
                    }
                }
            },
        )
    }
}

@Composable
private fun ItemList(
    modifier: Modifier = Modifier,
    showCreatePlaylistDialog: Boolean,
    toastState: ToastState,
    onNavigateClick: (AppMediaItem) -> Unit,
    onGlobalSearch: (query: String) -> Unit,
    searchQuery: String,
    onPlayClick: PlayHandler<AppMediaItem>,
    onCreatePlaylistClick: () -> Unit,
    onLoadMore: () -> Unit,
    onDismissCreatePlaylistDialog: () -> Unit,
    onCreatePlaylist: (String) -> Unit,
    playlistActions: PlaylistActions,
    libraryActions: LibraryActions,
    progressActions: ProgressActions? = null,
    contentPadding: PaddingValues,
    dataState: DataState<List<AppMediaItem>>,
    mediaType: MediaType,
    isLoadingMore: Boolean,
    hasMore: Boolean,
    viewMode: ViewMode,
) {
    Box(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .clearFocusOnScroll(),
        ) {
            // Content area
            Box(modifier = Modifier.fillMaxSize()) {
                when (val dataState = dataState) {
                    is DataState.Loading -> LoadingState()
                    is DataState.Error -> ErrorState()
                    is DataState.NoData -> EmptyState(searchQuery, onGlobalSearch)
                    is DataState.Stale,
                    is DataState.Data,
                        -> {
                        val items = dataState.dataOrNull.orEmpty()
                        if (items.isEmpty()) {
                            EmptyState(searchQuery, onGlobalSearch)
                        } else {
                            Column(modifier = Modifier.fillMaxSize()) {
                                if (mediaType == MediaType.PLAYLIST) {
                                    OutlinedButton(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 16.dp, vertical = 8.dp),
                                        onClick = onCreatePlaylistClick,
                                    ) {
                                        Icon(
                                            TablerIcons.Plus,
                                            contentDescription = stringResource(Res.string.cd_add_playlist),
                                        )
                                        Spacer(Modifier.width(4.dp))
                                        Text(stringResource(Res.string.playlist_add_new))
                                    }
                                }

                                val gridState = rememberLazyGridState()
                                AdaptiveMediaGrid(
                                    modifier = Modifier.fillMaxSize(),
                                    items = items,
                                    isLoadingMore = isLoadingMore,
                                    hasMore = hasMore,
                                    viewMode = viewMode,
                                    onNavigateClick = onNavigateClick,
                                    onPlayClick = onPlayClick,
                                    onLoadMore = onLoadMore,
                                    gridState = gridState,
                                    playlistActions = playlistActions,
                                    libraryActions = libraryActions,
                                    progressActions = progressActions,
                                    contentPadding = contentPadding,
                                )
                            }
                        }
                    }
                }
            }
        }

        // Toast host
        ToastHost(
            toastState = toastState,
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 48.dp),
        )

        // Create Playlist Dialog
        if (showCreatePlaylistDialog) {
            CreatePlaylistDialog(
                onDismiss = onDismissCreatePlaylistDialog,
                onCreate = onCreatePlaylist,
            )
        }
    }
}

@Composable
private fun CreatePlaylistDialog(
    onDismiss: () -> Unit,
    onCreate: (String) -> Unit,
) {
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    var playlistName by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.playlist_create_title)) },
        text = {
            OutlinedTextField(
                modifier = Modifier.focusRequester(focusRequester),
                value = playlistName,
                onValueChange = { playlistName = it },
                label = { Text(stringResource(Res.string.playlist_name_label)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(
                    onDone = {
                        focusManager.clearFocus()
                        val trimmed = playlistName.trim()
                        if (trimmed.isNotEmpty()) onCreate(trimmed)
                    },
                ),
            )
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (playlistName.trim().isNotEmpty()) {
                        onCreate(playlistName.trim())
                    }
                },
                enabled = playlistName.trim().isNotEmpty(),
            ) {
                Text(stringResource(Res.string.common_create))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(Res.string.common_cancel))
            }
        },
    )
}

@Composable
private fun LoadingState() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator()
    }
}

@Composable
private fun ErrorState() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = stringResource(Res.string.library_error),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.error,
        )
    }
}

@Composable
private fun EmptyState(
    searchQuery: String,
    onGlobalSearch: (query: String) -> Unit,
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = stringResource(Res.string.library_empty),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            // Offer escalation to global search only when an actual query yielded nothing.
            if (searchQuery.isNotBlank()) {
                OutlinedButton(onClick = { onGlobalSearch(searchQuery) }) {
                    Icon(
                        Icons.Default.Search,
                        contentDescription = null,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(Res.string.library_search_global))
                }
            }
        }
    }
}
