@file:OptIn(ExperimentalMaterial3Api::class)

package io.music_assistant.client.ui.compose.library

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import io.music_assistant.client.data.model.client.MediaType
import io.music_assistant.client.data.model.client.QueueOption
import io.music_assistant.client.data.model.client.items.AppMediaItem
import io.music_assistant.client.ui.compose.common.DataState
import io.music_assistant.client.ui.compose.common.ToastHost
import io.music_assistant.client.ui.compose.common.ToastState
import io.music_assistant.client.ui.compose.common.clearFocusOnScroll
import io.music_assistant.client.ui.compose.common.items.LibraryActions
import io.music_assistant.client.ui.compose.common.items.PlaylistActions
import io.music_assistant.client.ui.compose.common.items.ProgressActions
import io.music_assistant.client.ui.compose.common.rememberToastState
import io.music_assistant.client.ui.compose.common.viewmodel.ActionsViewModel
import io.music_assistant.client.ui.compose.nav.Screen
import musicassistantclient.composeapp.generated.resources.Res
import musicassistantclient.composeapp.generated.resources.cd_add_playlist
import musicassistantclient.composeapp.generated.resources.common_back
import musicassistantclient.composeapp.generated.resources.common_cancel
import musicassistantclient.composeapp.generated.resources.common_create
import musicassistantclient.composeapp.generated.resources.library_empty
import musicassistantclient.composeapp.generated.resources.library_error
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
    initialTabType: MediaType?,
    actionsViewModel: ActionsViewModel,
    onNavigateClick: (AppMediaItem) -> Unit,
    onBack: () -> Unit,
) {
    val state by itemListViewModel.state.collectAsStateWithLifecycle()
    val toastState = rememberToastState()

    LaunchedEffect(Unit) {
        itemListViewModel.applyInitialTabIfNeeded(initialTabType)
    }

    // Collect toasts
    LaunchedEffect(Unit) {
        actionsViewModel.toasts.collect { toast ->
            toastState.showToast(toast)
        }
    }

    val visibleTabs = state.tabs.filter { it.enabled }
    val selectedTab = visibleTabs.find { it.isSelected }
        ?: visibleTabs.firstOrNull()
        ?: state.tabs.first()

    var showCustomizeDialog by remember { mutableStateOf(false) }
    if (showCustomizeDialog) {
        CustomizeTabsDialog(
            initialConfig = state.tabs.map { it.tab to it.enabled },
            onDismissRequest = { showCustomizeDialog = false },
            onConfirm = itemListViewModel::onTabsConfigChanged,
        )
    }

    Screen(
        topBar = { scrollBehavior ->
            ItemListTopBar(
                selectedTab = selectedTab,
                scrollBehavior = scrollBehavior,
                onBack = onBack,
            )
        },
    ) {
        ItemList(
            contentPadding = contentPadding,
            selectedTab = selectedTab,
            showCreatePlaylistDialog = state.showCreatePlaylistDialog,
            toastState = toastState,
            onNavigateClick = onNavigateClick,
            onPlayClick = itemListViewModel::onPlayClick,
            onCreatePlaylistClick = itemListViewModel::onCreatePlaylistClick,
            onLoadMore = itemListViewModel::loadMore,
            onDismissCreatePlaylistDialog = itemListViewModel::onDismissCreatePlaylistDialog,
            onCreatePlaylist = itemListViewModel::createPlaylist,
            playlistActions = actionsViewModel,
            libraryActions = actionsViewModel,
            progressActions = actionsViewModel,
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ItemListTopBar(
    selectedTab: ItemListViewModel.TabState,
    scrollBehavior: TopAppBarScrollBehavior? = null,
    onBack: () -> Unit,
) {
    TopAppBar(
        title = {
            val title = when (selectedTab.tab) {
                ItemListViewModel.Tab.ARTISTS -> stringResource(
                    Res.string.media_type_artists,
                )

                ItemListViewModel.Tab.ALBUMS -> stringResource(Res.string.media_type_albums)
                ItemListViewModel.Tab.TRACKS -> stringResource(Res.string.media_type_tracks)
                ItemListViewModel.Tab.PLAYLISTS -> stringResource(
                    Res.string.media_type_playlists,
                )

                ItemListViewModel.Tab.AUDIOBOOKS -> stringResource(
                    Res.string.media_type_audiobooks,
                )

                ItemListViewModel.Tab.PODCASTS -> stringResource(
                    Res.string.media_type_podcasts,
                )

                ItemListViewModel.Tab.RADIOS -> stringResource(Res.string.media_type_radio)
                ItemListViewModel.Tab.GENRES -> stringResource(Res.string.media_type_genres)
            }

            Text(title)
        },
        subtitle = {
//            val focusManager = LocalFocusManager.current
//            Column {
//                val modifier = Modifier.padding(end = 16.dp)
//                // Quick search input
//                OutlinedTextField(
//                    modifier = modifier.fillMaxWidth(),
//                    value = selectedTab.searchQuery,
//                    onValueChange = { onSearchQueryChanged(selectedTab.tab, it) },
//                    label = {
//                        Text(text = stringResource(Res.string.library_quick_search))
//                    },
//                    trailingIcon = if (selectedTab.searchQuery.isNotEmpty()) {
//                        {
//                            IconButton(onClick = { onSearchQueryChanged(selectedTab.tab, "") }) {
//                                Icon(
//                                    Icons.Default.Clear,
//                                    contentDescription = stringResource(Res.string.common_clear),
//                                )
//                            }
//                        }
//                    } else {
//                        null
//                    },
//                    singleLine = true,
//                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
//                    keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
//                )
//                Row(
//                    modifier = modifier,
//                ) {
//                    FilterChip(
//                        selected = selectedTab.onlyFavorites,
//                        onClick = { onOnlyFavoritesClicked(selectedTab.tab) },
//                        label = { Text(stringResource(Res.string.action_favorite)) },
//                    )
//                    Spacer(Modifier.weight(1f))
//                    SortChip(
//                        currentSort = selectedTab.sortOption,
//                        availableFields = SortConfig.fieldsFor(selectedTab.tab.mediaType),
//                        onSortChanged = { onSortChanged(selectedTab.tab, it) },
//                    )
//                    IconButton(onClick = onToggleViewMode) {
//                        Icon(
//                            imageVector = when (viewMode) {
//                                ViewMode.LIST -> Icons.Default.GridView
//                                ViewMode.GRID -> Icons.AutoMirrored.Filled.ViewList
//                            },
//                            contentDescription = stringResource(Res.string.cd_toggle_view_mode),
//                        )
//                    }
//                }
//            }
        },
        scrollBehavior = scrollBehavior,
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(Res.string.common_back))
            }
        },
    )
}

@Composable
private fun ItemList(
    modifier: Modifier = Modifier,
    selectedTab: ItemListViewModel.TabState,
    showCreatePlaylistDialog: Boolean,
    toastState: ToastState,
    onNavigateClick: (AppMediaItem) -> Unit,
    onPlayClick: (AppMediaItem, QueueOption, Boolean) -> Unit,
    onCreatePlaylistClick: () -> Unit,
    onLoadMore: (ItemListViewModel.Tab) -> Unit,
    onDismissCreatePlaylistDialog: () -> Unit,
    onCreatePlaylist: (String) -> Unit,
    playlistActions: PlaylistActions,
    libraryActions: LibraryActions,
    progressActions: ProgressActions? = null,
    contentPadding: PaddingValues,
) {
    Box(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .clearFocusOnScroll(),
        ) {
            // Content area
            Box(modifier = Modifier.fillMaxSize()) {
                TabContent(
                    tabState = selectedTab,
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
private fun TabContent(
    tabState: ItemListViewModel.TabState,
    onNavigateClick: (AppMediaItem) -> Unit,
    onPlayClick: (AppMediaItem, QueueOption, Boolean) -> Unit,
    onCreatePlaylistClick: () -> Unit,
    onLoadMore: () -> Unit,
    playlistActions: PlaylistActions,
    libraryActions: LibraryActions,
    progressActions: ProgressActions? = null,
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
                ItemListViewModel.Tab.ARTISTS to artistsGridState,
                ItemListViewModel.Tab.ALBUMS to albumsGridState,
                ItemListViewModel.Tab.TRACKS to tracksGridState,
                ItemListViewModel.Tab.PLAYLISTS to playlistsGridState,
                ItemListViewModel.Tab.AUDIOBOOKS to audiobooksGridState,
                ItemListViewModel.Tab.PODCASTS to podcastsGridState,
                ItemListViewModel.Tab.RADIOS to radiosGridState,
                ItemListViewModel.Tab.GENRES to genresGridState,
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
            }
            if (items.isEmpty()) {
                EmptyState()
            } else {
                key(tabState.tab) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        if (tabState.tab == ItemListViewModel.Tab.PLAYLISTS) {
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
                        gridStates[tabState.tab]?.let {
                            AdaptiveMediaGrid(
                                modifier = Modifier.fillMaxSize(),
                                items = items,
                                isLoadingMore = tabState.isLoadingMore,
                                hasMore = tabState.hasMore,
                                viewMode = tabState.viewMode,
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
