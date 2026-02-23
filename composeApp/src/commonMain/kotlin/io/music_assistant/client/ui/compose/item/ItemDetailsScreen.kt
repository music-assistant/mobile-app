@file:OptIn(ExperimentalMaterial3Api::class)

package io.music_assistant.client.ui.compose.item

import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.AddToQueue
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlaylistAddCircle
import androidx.compose.material.icons.filled.QueuePlayNext
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import compose.icons.TablerIcons
import compose.icons.tablericons.FolderMinus
import compose.icons.tablericons.FolderPlus
import compose.icons.tablericons.Heart
import compose.icons.tablericons.HeartBroken
import io.music_assistant.client.data.model.client.AppMediaItem
import io.music_assistant.client.data.model.client.AppMediaItemFixtures
import io.music_assistant.client.data.model.server.MediaItemChapter
import io.music_assistant.client.data.model.server.MediaType
import io.music_assistant.client.data.model.server.QueueOption
import io.music_assistant.client.ui.compose.common.DataState
import io.music_assistant.client.ui.compose.common.OverflowMenu
import io.music_assistant.client.ui.compose.common.OverflowMenuOption
import io.music_assistant.client.ui.compose.common.ToastHost
import io.music_assistant.client.ui.compose.common.ToastState
import io.music_assistant.client.ui.compose.common.items.AlbumWithMenu
import io.music_assistant.client.ui.compose.common.items.PodcastEpisodeWithMenu
import io.music_assistant.client.ui.compose.common.items.TrackWithMenu
import io.music_assistant.client.ui.compose.common.providers.ProviderIcon
import io.music_assistant.client.ui.compose.common.rememberToastState
import io.music_assistant.client.ui.compose.common.viewmodel.ActionsViewModel
import io.music_assistant.client.ui.theme.AppTheme
import io.music_assistant.client.utils.SessionState
import kotlinx.coroutines.launch
import org.jetbrains.compose.ui.tooling.preview.Preview
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun ItemDetailsScreen(
    itemId: String,
    mediaType: MediaType,
    providerId: String,
    onBack: () -> Unit,
    onNavigateToItem: (String, MediaType, String) -> Unit,
) {
    val viewModel: ItemDetailsViewModel = koinViewModel()
    val actionsViewModel: ActionsViewModel = koinViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val serverUrl by viewModel.serverUrl.collectAsStateWithLifecycle(null)
    val toastState = rememberToastState()
    val isRowMode by viewModel.itemsRowMode.collectAsStateWithLifecycle(false)

    LaunchedEffect(itemId, mediaType) {
        viewModel.loadItem(itemId, mediaType, providerId)
    }

    // Collect toasts
    LaunchedEffect(Unit) {
        viewModel.toasts.collect { toast ->
            toastState.showToast(toast)
        }
    }

    ItemDetails(
        state,
        serverUrl,
        onBack,
        isRowMode,
        toastState,
        onNavigateToItem,
        actionsViewModel::getEditablePlaylists,
        actionsViewModel::addToPlaylist,
        actionsViewModel::onLibraryClick,
        actionsViewModel::onFavoriteClick,
        actionsViewModel::onMarkPlayed,
        actionsViewModel::onMarkUnplayed,
        { id, pos ->
            actionsViewModel.removeFromPlaylist(
                id,
                pos,
                viewModel::reload
            )
        },
        { modifier, provider ->
            actionsViewModel.getProviderIcon(provider)
                ?.let { ProviderIcon(modifier, it) }
        },
        viewModel::onPlayClick,
        viewModel::toggleItemsRowMode,
        viewModel::onChapterClick,
        viewModel::onPlayClick
    )
}

@Composable
private fun ItemDetails(
    state: ItemDetailsViewModel.State,
    serverUrl: String? = null,
    onBack: () -> Unit = {},
    isRowMode: Boolean = true,
    toastState: ToastState = rememberToastState(),
    onNavigateToItem: (String, MediaType, String) -> Unit = { _, _, _ -> },
    geEditablePlaylists: suspend () -> List<AppMediaItem.Playlist> = suspend { emptyList() },
    addToPlaylist: (AppMediaItem, AppMediaItem.Playlist) -> Unit = { _, _ -> },
    onLibraryClick: (AppMediaItem) -> Unit = {},
    onFavoriteClick: (AppMediaItem) -> Unit = {},
    onMarkPlayed: (AppMediaItem) -> Unit = {},
    onMarkUnplayed: (AppMediaItem) -> Unit = {},
    onRemoveFromPlaylist: (String, Int) -> Unit = { _, _ -> },
    providerIconFetcher: @Composable (Modifier, String) -> Unit = { _, _ -> },
    onPlayClick: (QueueOption, Boolean) -> Unit = { _, _ -> },
    onToggleViewMode: () -> Unit = {},
    onChapterClick: (Int) -> Unit = {},
    onChildPlayClick: (AppMediaItem, QueueOption, Boolean) -> Unit = { _, _, _ -> }
) {
    Column {
        val item = (state.itemState as? DataState.Data)?.data
        val playlistActions = ActionsViewModel.PlaylistActions(
            onLoadPlaylists = geEditablePlaylists,
            onAddToPlaylist = addToPlaylist
        )

        val libraryActions = ActionsViewModel.LibraryActions(
            onLibraryClick = onLibraryClick,
            onFavoriteClick = onFavoriteClick
        )

        val progressActions = ActionsViewModel.ProgressActions(
            onMarkPlayed = onMarkPlayed,
            onMarkUnplayed = onMarkUnplayed
        )

        ItemDetailsTopBar(
            onBack = onBack,
            onPlayClick = onPlayClick,
            item = item,
            playlistActions = playlistActions.takeIf { item is AppMediaItem.Track || item is AppMediaItem.Album },
            libraryActions = libraryActions,
            isRowMode = isRowMode,
            onToggleViewMode = onToggleViewMode,
        )

        ItemDetailsContent(
            state = state,
            serverUrl = serverUrl,
            toastState = toastState,
            isRowMode = isRowMode,
            onNavigateClick = { item ->
                when (item) {
                    is AppMediaItem.Artist,
                    is AppMediaItem.Album,
                    is AppMediaItem.Playlist,
                    is AppMediaItem.Podcast,
                    is AppMediaItem.Audiobook -> {
                        onNavigateToItem(item.itemId, item.mediaType, item.provider)
                    }

                    else -> Unit
                }
            },
            onPlayClick = onChildPlayClick,
            onChapterClick = onChapterClick,
            playlistActions = playlistActions,
            progressActions = progressActions,
            onRemoveFromPlaylist = onRemoveFromPlaylist,
            libraryActions = libraryActions,
            providerIconFetcher = providerIconFetcher
        )
    }
}

@Composable
private fun ItemDetailsTopBar(
    item: AppMediaItem?,
    onBack: () -> Unit,
    onPlayClick: (QueueOption, Boolean) -> Unit,
    libraryActions: ActionsViewModel.LibraryActions,
    playlistActions: ActionsViewModel.PlaylistActions?,
    isRowMode: Boolean,
    onToggleViewMode: () -> Unit,
) {
    val coroutineScope = rememberCoroutineScope()
    var showPlaylistDialog by rememberSaveable { mutableStateOf(false) }
    var playlists by remember { mutableStateOf<List<AppMediaItem.Playlist>>(emptyList()) }
    var isLoadingPlaylists by remember { mutableStateOf(false) }
    Surface(
        modifier = Modifier
            .fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 2.dp
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    modifier = Modifier.basicMarquee(),
                    text = item?.name ?: "Loading...",
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                item?.subtitle?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            item?.let {
                if (it !is AppMediaItem.Audiobook) {
                    IconButton(onClick = onToggleViewMode) {
                        Icon(
                            imageVector = if (isRowMode) Icons.Default.GridView else Icons.AutoMirrored.Filled.ViewList,
                            contentDescription = "Toggle view mode"
                        )
                    }
                }
                IconButton(
                    onClick = { onPlayClick(QueueOption.REPLACE, false) },
                ) {
                    Icon(Icons.Default.PlayArrow, "Play now")
                }

                OverflowMenu(
                    modifier = Modifier,
                    buttonContent = { onClick ->
                        Icon(
                            modifier = Modifier.clickable { onClick() },
                            imageVector = Icons.Default.ExpandMore,
                            contentDescription = null
                        )
                    },
                    options = buildList {
                        add(
                            OverflowMenuOption(
                                title = "Insert next and play",
                                icon = Icons.Default.PlaylistAddCircle
                            ) { onPlayClick(QueueOption.PLAY, false) })
                        add(
                            OverflowMenuOption(
                                title = "Insert next",
                                icon = Icons.Default.QueuePlayNext
                            ) {
                                onPlayClick(QueueOption.NEXT, false)
                            })
                        add(
                            OverflowMenuOption(
                                title = "Add to bottom",
                                icon = Icons.Default.AddToQueue
                            ) {
                                onPlayClick(
                                    QueueOption.ADD, false
                                )
                            })
                        if (item.canStartRadio) {
                            add(
                                OverflowMenuOption(
                                    title = "Start radio",
                                    icon = Icons.Default.Radio
                                ) {
                                    onPlayClick(QueueOption.REPLACE, true)
                                })
                        }
                        add(
                            OverflowMenuOption(
                                title =
                                    if (item.isInLibrary) "Remove from library"
                                    else "Add to library",
                                icon =
                                    if (item.isInLibrary) TablerIcons.FolderMinus
                                    else TablerIcons.FolderPlus
                            ) { libraryActions.onLibraryClick(item) })
                        if (item.isInLibrary) {
                            add(
                                OverflowMenuOption(
                                    title =
                                        if (item.favorite == true) "Unfavorite"
                                        else "Favorite",
                                    icon =
                                        if (item.favorite == true) TablerIcons.HeartBroken
                                        else TablerIcons.Heart
                                ) { libraryActions.onFavoriteClick(item) })
                        }
                        playlistActions?.let {
                            add(
                                OverflowMenuOption(
                                    title = "Add to Playlist",
                                    icon = Icons.AutoMirrored.Filled.PlaylistAdd
                                ) {
                                    showPlaylistDialog = true
                                    // Load playlists when dialog opens
                                    coroutineScope.launch {
                                        isLoadingPlaylists = true
                                        playlists = it.onLoadPlaylists()
                                        isLoadingPlaylists = false
                                    }
                                })
                        }
                    }
                )
            }
        }
    }
    // Add to Playlist dialog
    if (showPlaylistDialog) {
        item?.let {
            AlertDialog(
                onDismissRequest = {
                    showPlaylistDialog = false
                    playlists = emptyList()
                    isLoadingPlaylists = false
                },
                title = { Text("Add to Playlist") },
                text = {
                    if (isLoadingPlaylists) {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator()
                        }
                    } else if (playlists.isEmpty()) {
                        Text("No editable playlists available")
                    } else {
                        Column {
                            playlists.forEach { playlist ->
                                TextButton(
                                    onClick = {
                                        playlistActions?.onAddToPlaylist(item, playlist)
                                        showPlaylistDialog = false
                                        playlists = emptyList()
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = playlist.name,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }
                        }
                    }
                },
                confirmButton = {},
                dismissButton = {
                    TextButton(onClick = {
                        showPlaylistDialog = false
                        playlists = emptyList()
                        isLoadingPlaylists = false
                    }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}

@Composable
private fun ItemDetailsContent(
    state: ItemDetailsViewModel.State,
    serverUrl: String?,
    toastState: ToastState,
    isRowMode: Boolean,
    onNavigateClick: (AppMediaItem) -> Unit,
    onPlayClick: (AppMediaItem, QueueOption, Boolean) -> Unit,
    onChapterClick: (Int) -> Unit,
    playlistActions: ActionsViewModel.PlaylistActions,
    progressActions: ActionsViewModel.ProgressActions? = null,
    onRemoveFromPlaylist: (String, Int) -> Unit,
    libraryActions: ActionsViewModel.LibraryActions,
    providerIconFetcher: (@Composable (Modifier, String) -> Unit),
) {
    Box(modifier = Modifier.fillMaxSize()) {
        when (val itemState = state.itemState) {
            is DataState.Loading -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }

            is DataState.Error -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Error loading item",
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }

            is DataState.Stale,
            is DataState.Data -> {
                // Handle both Data and Stale - both contain valid item data
                val item = when (itemState) {
                    is DataState.Data -> itemState.data
                    is DataState.Stale -> itemState.data
                    else -> return@Box
                }

                LazyVerticalGrid(
                    modifier = Modifier.fillMaxSize(),
                    columns = GridCells.Adaptive(minSize = 96.dp),
                    contentPadding = PaddingValues(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // For Artist: Albums section
                    if (item is AppMediaItem.Artist) {
                        when (val albumsState = state.albumsState) {
                            is DataState.Data -> {
                                if (albumsState.data.isNotEmpty()) {
                                    item(span = { GridItemSpan(maxLineSpan) }) {
                                        SectionHeader("Albums")
                                    }
                                    items(
                                        albumsState.data,
                                        span = if (isRowMode) {
                                            { GridItemSpan(maxLineSpan) }
                                        } else null
                                    ) { album ->
                                        AlbumWithMenu(
                                            item = album,
                                            rowMode = isRowMode,
                                            showSubtitle = true,
                                            serverUrl = serverUrl,
                                            onNavigateClick = onNavigateClick,
                                            onPlayOption = onPlayClick,
                                            libraryActions = libraryActions,
                                            providerIconFetcher = providerIconFetcher
                                        )
                                    }
                                }
                            }

                            is DataState.Loading -> {
                                item(span = { GridItemSpan(maxLineSpan) }) {
                                    Box(
                                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        CircularProgressIndicator()
                                    }
                                }
                            }

                            else -> Unit
                        }
                    }

                    // For Audiobook: Chapters section (from metadata, not a separate API)
                    if (item is AppMediaItem.Audiobook) {
                        val chapters = item.chapters
                        if (!chapters.isNullOrEmpty()) {
                            item(span = { GridItemSpan(maxLineSpan) }) {
                                SectionHeader("Chapters")
                            }
                            chapters.forEach { chapter ->
                                item(span = { GridItemSpan(maxLineSpan) }) {
                                    ChapterRow(
                                        chapter = chapter,
                                        onClick = {
                                            onChapterClick(chapter.position)
                                        }
                                    )
                                }
                            }
                        }
                    }

                    // Tracks section (all types)
                    when (val tracksState = state.playableItemsState) {
                        is DataState.Data -> {
                            if (tracksState.data.isNotEmpty()) {
                                item(span = { GridItemSpan(maxLineSpan) }) {
                                    SectionHeader(
                                        when (item) {
                                            is AppMediaItem.Podcast -> "Episodes"
                                            else -> "Tracks"
                                        }
                                    )
                                }
                                tracksState.data.forEachIndexed { index, track ->
                                    item(
                                        span = if (isRowMode) {
                                            { GridItemSpan(maxLineSpan) }
                                        } else null
                                    ) {
                                        when (track) {
                                            is AppMediaItem.Track -> TrackWithMenu(
                                                item = track,
                                                serverUrl = serverUrl,
                                                rowMode = isRowMode,
                                                onPlayOption = onPlayClick,
                                                playlistActions = playlistActions,
                                                // Show "remove from playlist" only for playlist items
                                                onRemoveFromPlaylist = if (item is AppMediaItem.Playlist && item.isEditable == true) {
                                                    { onRemoveFromPlaylist(item.itemId, index) }
                                                } else null,
                                                libraryActions = libraryActions,
                                                providerIconFetcher = providerIconFetcher,
                                            )

                                            is AppMediaItem.PodcastEpisode -> PodcastEpisodeWithMenu(
                                                item = track,
                                                serverUrl = serverUrl,
                                                rowMode = isRowMode,
                                                onPlayOption = onPlayClick,
                                                playlistActions = null, // No playlist actions for podcast episodes
                                                libraryActions = libraryActions,
                                                progressActions = progressActions,
                                                providerIconFetcher = providerIconFetcher,
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        is DataState.Loading -> {
                            item(span = { GridItemSpan(maxLineSpan) }) {
                                Box(
                                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CircularProgressIndicator()
                                }
                            }
                        }

                        else -> Unit
                    }
                }
            }

            is DataState.NoData -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text("No data available")
                }
            }
        }

        // Toast host
        ToastHost(
            toastState = toastState,
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 48.dp)
        )
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(16.dp, 8.dp)
    )
}

@Composable
private fun ChapterRow(
    chapter: MediaItemChapter,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = chapter.name,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        val durationMinutes = (chapter.duration / 60).toInt()
        if (durationMinutes > 0) {
            Text(
                text = "${durationMinutes}m",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 8.dp)
            )
        }
    }
}

@Preview
@Composable
private fun PreviewArtist(isRowMode: Boolean = true) {
    AppTheme(darkTheme = false) {
        Scaffold {
            ItemDetails(
                state = ItemDetailsViewModel.State(
                    SessionState.Disconnected.NoServerData,
                    DataState.Data(AppMediaItemFixtures.artist("Artist")),
                    DataState.Data(
                        listOf(
                            AppMediaItemFixtures.album("Album 1", "Artist"),
                            AppMediaItemFixtures.album("Album 2", "Artist")
                        )
                    ),
                    DataState.NoData()
                ),
                isRowMode = isRowMode
            )
        }
    }
}

@Preview
@Composable
private fun PreviewArtistGrid() {
    PreviewArtist(isRowMode = false)
}

@Preview
@Composable
private fun PreviewAlbum(isRowMode: Boolean = true) {
    AppTheme(darkTheme = false) {
        Scaffold {
            ItemDetails(
                state = ItemDetailsViewModel.State(
                    SessionState.Disconnected.NoServerData,
                    DataState.Data(AppMediaItemFixtures.album("Title", "Artist")),
                    DataState.NoData(),
                    DataState.Data(AppMediaItemFixtures.tracks(listOf("Track 1", "Track 2")))
                ),
                isRowMode = isRowMode
            )
        }
    }
}

@Preview
@Composable
private fun PreviewAlbumGrid() {
    PreviewAlbum(isRowMode = false)
}

@Preview
@Composable
private fun PreviewPlaylist(isRowMode: Boolean = true) {
    AppTheme(darkTheme = false) {
        Scaffold {
            ItemDetails(
                state = ItemDetailsViewModel.State(
                    SessionState.Disconnected.NoServerData,
                    DataState.Data(AppMediaItemFixtures.playlist("Title")),
                    DataState.NoData(),
                    DataState.Data(AppMediaItemFixtures.tracks(listOf("Track 1", "Track 2")))
                ),
                isRowMode = isRowMode
            )
        }
    }
}

@Preview
@Composable
private fun PreviewPlaylistGrid() {
    PreviewPlaylist(isRowMode = false)
}

@Preview
@Composable
private fun PreviewPodcast(isRowMode: Boolean = true) {
    AppTheme(darkTheme = false) {
        Scaffold {
            ItemDetails(
                state = ItemDetailsViewModel.State(
                    SessionState.Disconnected.NoServerData,
                    DataState.Data(AppMediaItemFixtures.podcast("Title")),
                    DataState.NoData(),
                    DataState.Data(AppMediaItemFixtures.episodes(listOf("Episode 1", "Episode 2")))
                ),
                isRowMode = isRowMode
            )
        }
    }
}

@Preview
@Composable
private fun PreviewPodcastGrid() {
    PreviewPodcast(isRowMode = false)
}

@Preview
@Composable
private fun PreviewAudiobook() {
    AppTheme(darkTheme = false) {
        Scaffold {
            ItemDetails(
                state = ItemDetailsViewModel.State(
                    SessionState.Disconnected.NoServerData,
                    DataState.Data(AppMediaItemFixtures.audiobook(
                        "Title",
                        listOf("Chapter 1", "Chapter 2")
                    )),
                    DataState.NoData(),
                    DataState.NoData()
                )
            )
        }
    }
}
