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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import compose.icons.TablerIcons
import compose.icons.tablericons.Plus
import io.music_assistant.client.data.model.client.AppMediaItem
import io.music_assistant.client.data.model.client.SortConfig
import io.music_assistant.client.data.model.client.SortOption
import io.music_assistant.client.data.model.server.MediaType
import io.music_assistant.client.data.model.server.QueueOption
import io.music_assistant.client.ui.compose.common.DataState
import io.music_assistant.client.ui.compose.common.SortChip
import io.music_assistant.client.ui.compose.common.ToastHost
import io.music_assistant.client.ui.compose.common.ToastState
import io.music_assistant.client.ui.compose.common.rememberToastState
import io.music_assistant.client.ui.compose.common.viewmodel.ActionsViewModel
import io.music_assistant.client.ui.compose.nav.Screen
import musicassistantclient.composeapp.generated.resources.Res
import musicassistantclient.composeapp.generated.resources.action_favorite
import musicassistantclient.composeapp.generated.resources.cd_add_playlist
import musicassistantclient.composeapp.generated.resources.cd_toggle_view_mode
import musicassistantclient.composeapp.generated.resources.common_cancel
import musicassistantclient.composeapp.generated.resources.common_clear
import musicassistantclient.composeapp.generated.resources.common_create
import musicassistantclient.composeapp.generated.resources.library_empty
import musicassistantclient.composeapp.generated.resources.library_error
import musicassistantclient.composeapp.generated.resources.library_quick_search
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
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun LibraryScreen(
    contentPadding: PaddingValues,
    initialTabType: MediaType?,
    onNavigateClick: (AppMediaItem) -> Unit,
) {
    val viewModel: LibraryViewModel = koinViewModel()
    val actionsViewModel: ActionsViewModel = koinInject()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val serverUrl by viewModel.serverUrl.collectAsStateWithLifecycle(null)
    val isRowMode by viewModel.itemsRowMode.collectAsStateWithLifecycle(false)
    val toastState = rememberToastState()

    // Map MediaType to Tab
    val initialTab = when (initialTabType) {
        MediaType.ARTIST -> LibraryViewModel.Tab.ARTISTS
        MediaType.ALBUM -> LibraryViewModel.Tab.ALBUMS
        MediaType.TRACK -> LibraryViewModel.Tab.TRACKS
        MediaType.PLAYLIST -> LibraryViewModel.Tab.PLAYLISTS
        MediaType.AUDIOBOOK -> LibraryViewModel.Tab.AUDIOBOOKS
        MediaType.PODCAST -> LibraryViewModel.Tab.PODCASTS
        MediaType.RADIO -> LibraryViewModel.Tab.RADIOS
        MediaType.GENRE -> LibraryViewModel.Tab.GENRES
        null -> LibraryViewModel.Tab.ARTISTS
        else -> LibraryViewModel.Tab.ARTISTS
    }

    // Apply initialTab once per NavEntry lifetime. A fresh Library NavKey (e.g. "All Albums"
    // from Home) instantiates a new entry with a reset flag; popping back from ItemDetails
    // restores the flag, so the user's in-library tab selection is preserved.
    var initialTabApplied by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(initialTab) {
        if (!initialTabApplied) {
            viewModel.onTabSelected(initialTab)
            initialTabApplied = true
        }
    }

    // Collect toasts
    LaunchedEffect(Unit) {
        actionsViewModel.toasts.collect { toast ->
            toastState.showToast(toast)
        }
    }

    Screen(
        topBar = { scrollBehavior ->
            LibraryTopBar(
                tabs = state.tabs,
                onTabSelected = viewModel::onTabSelected,
                isRowMode = isRowMode,
                onToggleViewMode = viewModel::toggleItemsRowMode,
                scrollBehavior = scrollBehavior,
            )
        },
    ) {
        Library(
            contentPadding = contentPadding,
            state = state,
            serverUrl = serverUrl,
            isRowMode = isRowMode,
            toastState = toastState,
            onNavigateClick = onNavigateClick,
            onPlayClick = viewModel::onPlayClick,
            onCreatePlaylistClick = viewModel::onCreatePlaylistClick,
            onLoadMore = viewModel::loadMore,
            onSearchQueryChanged = viewModel::onSearchQueryChanged,
            onOnlyFavoritesClicked = viewModel::onOnlyFavoritesClicked,
            onSortChanged = viewModel::onSortChanged,
            onDismissCreatePlaylistDialog = viewModel::onDismissCreatePlaylistDialog,
            onCreatePlaylist = viewModel::createPlaylist,
            playlistActions = ActionsViewModel.PlaylistActions(
                onLoadPlaylists = actionsViewModel::getEditablePlaylists,
                onAddToPlaylist = actionsViewModel::addToPlaylist,
            ),
            libraryActions = ActionsViewModel.LibraryActions(
                onLibraryClick = actionsViewModel::onLibraryClick,
                onFavoriteClick = actionsViewModel::onFavoriteClick,
            ),
            progressActions = ActionsViewModel.ProgressActions(
                onMarkPlayed = actionsViewModel::onMarkPlayed,
                onMarkUnplayed = actionsViewModel::onMarkUnplayed,
            ),
        )
    }
}

@Composable
private fun LibraryTopBar(
    tabs: List<LibraryViewModel.TabState>,
    onTabSelected: (LibraryViewModel.Tab) -> Unit,
    isRowMode: Boolean,
    onToggleViewMode: () -> Unit,
    scrollBehavior: TopAppBarScrollBehavior? = null,
) {
    TopAppBar(
        title = {
            PrimaryScrollableTabRow(
                containerColor = Color.Transparent,
                edgePadding = 0.dp,
                selectedTabIndex = tabs.indexOfFirst { it.isSelected },
            ) {
                tabs.forEach { tabState ->
                    Tab(
                        selected = tabState.isSelected,
                        onClick = { onTabSelected(tabState.tab) },
                        text = {
                            Text(
                                when (tabState.tab) {
                                    LibraryViewModel.Tab.ARTISTS -> stringResource(Res.string.media_type_artists)
                                    LibraryViewModel.Tab.ALBUMS -> stringResource(Res.string.media_type_albums)
                                    LibraryViewModel.Tab.TRACKS -> stringResource(Res.string.media_type_tracks)
                                    LibraryViewModel.Tab.PLAYLISTS -> stringResource(Res.string.media_type_playlists)
                                    LibraryViewModel.Tab.AUDIOBOOKS -> stringResource(Res.string.media_type_audiobooks)
                                    LibraryViewModel.Tab.PODCASTS -> stringResource(Res.string.media_type_podcasts)
                                    LibraryViewModel.Tab.RADIOS -> stringResource(Res.string.media_type_radio)
                                    LibraryViewModel.Tab.GENRES -> stringResource(Res.string.media_type_genres)
                                },
                            )
                        },
                    )
                }
            }
        },
        actions = {
            IconButton(onClick = onToggleViewMode) {
                Icon(
                    imageVector = if (isRowMode) Icons.Default.GridView else Icons.AutoMirrored.Filled.ViewList,
                    contentDescription = stringResource(Res.string.cd_toggle_view_mode),
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
        scrollBehavior = scrollBehavior,
    )
}

@Composable
private fun Library(
    modifier: Modifier = Modifier,
    state: LibraryViewModel.State,
    serverUrl: String?,
    isRowMode: Boolean,
    toastState: ToastState,
    onNavigateClick: (AppMediaItem) -> Unit,
    onPlayClick: (AppMediaItem, QueueOption, Boolean) -> Unit,
    onCreatePlaylistClick: () -> Unit,
    onLoadMore: (LibraryViewModel.Tab) -> Unit,
    onSearchQueryChanged: (LibraryViewModel.Tab, String) -> Unit,
    onOnlyFavoritesClicked: (LibraryViewModel.Tab) -> Unit,
    onSortChanged: (LibraryViewModel.Tab, SortOption) -> Unit,
    onDismissCreatePlaylistDialog: () -> Unit,
    onCreatePlaylist: (String) -> Unit,
    playlistActions: ActionsViewModel.PlaylistActions,
    libraryActions: ActionsViewModel.LibraryActions,
    progressActions: ActionsViewModel.ProgressActions? = null,
    contentPadding: PaddingValues,
) {
    val selectedTab = state.tabs.find { it.isSelected } ?: state.tabs.first()

    Box(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxSize(),
        ) {
            // Quick search input
            OutlinedTextField(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                value = selectedTab.searchQuery,
                onValueChange = { onSearchQueryChanged(selectedTab.tab, it) },
                label = {
                    Text(text = stringResource(Res.string.library_quick_search))
                },
                trailingIcon = if (selectedTab.searchQuery.isNotEmpty()) {
                    {
                        IconButton(onClick = { onSearchQueryChanged(selectedTab.tab, "") }) {
                            Icon(
                                Icons.Default.Clear,
                                contentDescription = stringResource(Res.string.common_clear),
                            )
                        }
                    }
                } else {
                    null
                },
                singleLine = true,
            )
            Row(
                modifier = Modifier.padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(
                    selected = selectedTab.onlyFavorites,
                    onClick = { onOnlyFavoritesClicked(selectedTab.tab) },
                    label = { Text(stringResource(Res.string.action_favorite)) },
                )
                SortChip(
                    currentSort = selectedTab.sortOption,
                    availableFields = SortConfig.fieldsFor(selectedTab.tab.mediaType),
                    onSortChanged = { onSortChanged(selectedTab.tab, it) },
                )
            }

            // Content area
            Box(modifier = Modifier.fillMaxSize()) {
                TabContent(
                    tabState = selectedTab,
                    serverUrl = serverUrl,
                    isRowMode = isRowMode,
                    onNavigateClick = onNavigateClick,
                    onPlayClick = onPlayClick,
                    onCreatePlaylistClick = onCreatePlaylistClick,
                    onLoadMore = { onLoadMore(selectedTab.tab) },
                    playlistActions = playlistActions,
                    libraryActions = libraryActions,
                    progressActions = progressActions,
                    contentPadding,
                )
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
        if (state.showCreatePlaylistDialog) {
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
private fun TabContent(
    tabState: LibraryViewModel.TabState,
    serverUrl: String?,
    isRowMode: Boolean,
    onNavigateClick: (AppMediaItem) -> Unit,
    onPlayClick: (AppMediaItem, QueueOption, Boolean) -> Unit,
    onCreatePlaylistClick: () -> Unit,
    onLoadMore: () -> Unit,
    playlistActions: ActionsViewModel.PlaylistActions,
    libraryActions: ActionsViewModel.LibraryActions,
    progressActions: ActionsViewModel.ProgressActions? = null,
    contentPadding: PaddingValues,
) {
    // Create separate grid states for each tab to preserve scroll position
    val artistsGridState = rememberLazyGridState()
    val albumsGridState = rememberLazyGridState()
    val tracksGridState = rememberLazyGridState()
    val playlistsGridState = rememberLazyGridState()
    val audiobooksGridState = rememberLazyGridState()
    val podcastsGridState = rememberLazyGridState()
    val radiosGridState = rememberLazyGridState()
    val genresGridState = rememberLazyGridState()

    val gridStates =
        remember(
            artistsGridState,
            albumsGridState,
            tracksGridState,
            playlistsGridState,
            audiobooksGridState,
            podcastsGridState,
            radiosGridState,
            genresGridState,
        ) {
            mapOf(
                LibraryViewModel.Tab.ARTISTS to artistsGridState,
                LibraryViewModel.Tab.ALBUMS to albumsGridState,
                LibraryViewModel.Tab.TRACKS to tracksGridState,
                LibraryViewModel.Tab.PLAYLISTS to playlistsGridState,
                LibraryViewModel.Tab.AUDIOBOOKS to audiobooksGridState,
                LibraryViewModel.Tab.PODCASTS to podcastsGridState,
                LibraryViewModel.Tab.RADIOS to radiosGridState,
                LibraryViewModel.Tab.GENRES to genresGridState,
            )
        }

    when (val dataState = tabState.dataState) {
        is DataState.Loading -> LoadingState()
        is DataState.Error -> ErrorState()
        is DataState.NoData -> EmptyState()
        is DataState.Stale,
        is DataState.Data,
        -> {
            // Handle both Data and Stale - both contain valid library data
            val items = when (dataState) {
                is DataState.Data -> dataState.data
                is DataState.Stale -> dataState.data
                else -> emptyList()
            }
            if (items.isEmpty()) {
                EmptyState()
            } else {
                key(tabState.tab) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        if (tabState.tab == LibraryViewModel.Tab.PLAYLISTS) {
                            OutlinedButton(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                                onClick = onCreatePlaylistClick,
                            ) {
                                Icon(TablerIcons.Plus, contentDescription = stringResource(Res.string.cd_add_playlist))
                                Spacer(Modifier.width(4.dp))
                                Text(stringResource(Res.string.playlist_add_new))
                            }
                        }
                        gridStates[tabState.tab]?.let {
                            AdaptiveMediaGrid(
                                modifier = Modifier.fillMaxSize(),
                                items = items,
                                serverUrl = serverUrl,
                                isLoadingMore = tabState.isLoadingMore,
                                hasMore = tabState.hasMore,
                                isRowMode = isRowMode,
                                onNavigateClick = onNavigateClick,
                                onPlayClick = onPlayClick,
                                onLoadMore = onLoadMore,
                                gridState = it,
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
private fun EmptyState() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = stringResource(Res.string.library_empty),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
