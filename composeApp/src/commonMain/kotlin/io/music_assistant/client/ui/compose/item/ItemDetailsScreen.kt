@file:OptIn(ExperimentalMaterial3Api::class)

package io.music_assistant.client.ui.compose.item

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.plus
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.music_assistant.client.data.model.client.AppMediaItem
import io.music_assistant.client.data.model.client.AppMediaItemFixtures
import io.music_assistant.client.data.model.server.MediaItemChapter
import io.music_assistant.client.data.model.server.MediaType
import io.music_assistant.client.data.model.server.QueueOption
import io.music_assistant.client.ui.compose.common.DataState
import io.music_assistant.client.ui.compose.common.ToastHost
import io.music_assistant.client.ui.compose.common.ToastState
import io.music_assistant.client.ui.compose.common.items.AlbumWithMenu
import io.music_assistant.client.ui.compose.common.items.ArtistWithMenu
import io.music_assistant.client.ui.compose.common.items.PodcastEpisodeWithMenu
import io.music_assistant.client.ui.compose.common.items.TrackWithMenu
import io.music_assistant.client.ui.compose.common.providers.ProviderIcon
import io.music_assistant.client.ui.compose.common.rememberToastState
import io.music_assistant.client.ui.compose.common.viewmodel.ActionsViewModel
import io.music_assistant.client.ui.compose.nav.Screen
import io.music_assistant.client.ui.theme.AppTheme
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun ItemDetailsScreen(
    itemId: String,
    mediaType: MediaType,
    providerId: String,
    onBack: () -> Unit,
    onNavigateToItem: (String, MediaType, String) -> Unit,
    contentPadding: PaddingValues,
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
        contentPadding,
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
fun ItemDetails(
    contentPadding: PaddingValues = PaddingValues(),
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

    ItemChildren(
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
                is AppMediaItem.Audiobook,
                is AppMediaItem.Genre -> {
                    onNavigateToItem(item.itemId, item.mediaType, item.provider)
                }

                else -> Unit
            }
        },
        onPlayItemClick = onPlayClick,
        onPlayChildClick = onChildPlayClick,
        onChapterClick = onChapterClick,
        playlistActions = playlistActions,
        progressActions = progressActions,
        onRemoveFromPlaylist = onRemoveFromPlaylist,
        libraryActions = libraryActions,
        providerIconFetcher = providerIconFetcher,
        onBack = onBack,
        onToggleViewMode = onToggleViewMode,
        contentPadding,
    )
}

@Composable
private fun ItemChildren(
    state: ItemDetailsViewModel.State,
    serverUrl: String?,
    toastState: ToastState,
    isRowMode: Boolean,
    onNavigateClick: (AppMediaItem) -> Unit,
    onPlayItemClick: (QueueOption, Boolean) -> Unit,
    onPlayChildClick: (AppMediaItem, QueueOption, Boolean) -> Unit,
    onChapterClick: (Int) -> Unit,
    playlistActions: ActionsViewModel.PlaylistActions,
    progressActions: ActionsViewModel.ProgressActions? = null,
    onRemoveFromPlaylist: (String, Int) -> Unit,
    libraryActions: ActionsViewModel.LibraryActions,
    providerIconFetcher: (@Composable (Modifier, String) -> Unit),
    onBack: () -> Unit,
    onToggleViewMode: () -> Unit,
    contentPadding: PaddingValues,
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

                Screen(topBar = { scrollBehaviour ->
                    ItemTopBar(
                        item = item,
                        isRowMode = isRowMode,
                        onBack = onBack,
                        onToggleViewMode = onToggleViewMode,
                        libraryActions = libraryActions,
                        playlistActions = playlistActions.takeIf { item !is AppMediaItem.Genre },
                        scrollBehavior = scrollBehaviour
                    )
                }) {
                    LazyVerticalGrid(
                        modifier = Modifier.fillMaxWidth()
                            .testTag("LazyVerticalGrid"),
                        columns = GridCells.Adaptive(minSize = 96.dp),
                        contentPadding = contentPadding + PaddingValues(8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            ItemHeader(
                                item = item,
                                serverUrl = serverUrl,
                                providerIconFetcher = providerIconFetcher,
                                onPlayClick = onPlayItemClick
                            )
                        }

                        // For Genre: Artists section
                        if (item is AppMediaItem.Genre) {
                            when (val artistsState = state.artistsState) {
                                is DataState.Data -> {
                                    if (artistsState.data.isNotEmpty()) {
                                        item(span = { GridItemSpan(maxLineSpan) }) {
                                            SectionHeader("Artists")
                                        }
                                        items(
                                            artistsState.data,
                                            span = if (isRowMode) {
                                                { GridItemSpan(maxLineSpan) }
                                            } else null
                                        ) { artist ->
                                            ArtistWithMenu(
                                                item = artist,
                                                rowMode = isRowMode,

                                                serverUrl = serverUrl,
                                                onNavigateClick = onNavigateClick,
                                                onPlayOption = onPlayChildClick,
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

                        // For Artist or Genre: Albums section
                        if (item is AppMediaItem.Artist || item is AppMediaItem.Genre) {
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

                                                serverUrl = serverUrl,
                                                onNavigateClick = onNavigateClick,
                                                onPlayOption = onPlayChildClick,
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
                                                    onPlayOption = onPlayChildClick,
                                                    playlistActions = playlistActions,
                                                    // Show "remove from playlist" only for playlist items
                                                    onRemoveFromPlaylist = if (item is AppMediaItem.Playlist && item.isEditable == true) {
                                                        {
                                                            onRemoveFromPlaylist(
                                                                item.itemId,
                                                                index
                                                            )
                                                        }
                                                    } else null,
                                                    libraryActions = libraryActions,
                                                    providerIconFetcher = providerIconFetcher,
                                                )

                                                is AppMediaItem.PodcastEpisode -> PodcastEpisodeWithMenu(
                                                    item = track,
                                                    serverUrl = serverUrl,
                                                    rowMode = isRowMode,
                                                    onPlayOption = onPlayChildClick,
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
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)
        )
    }
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
private fun PreviewLoading() {
    AppTheme(darkTheme = false) {
        Scaffold {
            ItemDetails(
                state = ItemDetailsViewModel.State(
                    DataState.Loading(),
                    DataState.Loading(),
                    DataState.Loading()
                ),
                geEditablePlaylists = suspend { emptyList() },
            )
        }
    }
}

@Preview
@Composable
private fun PreviewArtist(isRowMode: Boolean = true) {
    val artist = AppMediaItemFixtures.artist("Artist")

    AppTheme(darkTheme = false) {
        Scaffold {
            ItemDetails(
                state = ItemDetailsViewModel.State(
                    DataState.Data(artist),
                    DataState.Data(
                        listOf(
                            AppMediaItemFixtures.album(name = "Album 1", artist = artist),
                            AppMediaItemFixtures.album(name = "Album 2", artist = artist)
                        )
                    ),
                    DataState.NoData()
                ),
                isRowMode = isRowMode,
                geEditablePlaylists = suspend { emptyList() },
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
    val artist = AppMediaItemFixtures.artist("Artist")
    val album = AppMediaItemFixtures.album(name = "Title", artist = artist)

    AppTheme(darkTheme = false) {
        ItemDetails(
            state = ItemDetailsViewModel.State(
                DataState.Data(album),
                DataState.NoData(),
                DataState.Data(
                    AppMediaItemFixtures.tracks(
                        listOf("Track 1", "Track 2"),
                        album = album
                    )
                )
            ),
            isRowMode = isRowMode,
            geEditablePlaylists = suspend { emptyList() },
        )
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
        ItemDetails(
            state = ItemDetailsViewModel.State(
                DataState.Data(AppMediaItemFixtures.playlist("Title")),
                DataState.NoData(),
                DataState.Data(AppMediaItemFixtures.tracks(listOf("Track 1", "Track 2")))
            ),
            isRowMode = isRowMode,
            geEditablePlaylists = suspend { emptyList() },
        )
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
    val podcast = AppMediaItemFixtures.podcast()
    AppTheme(darkTheme = false) {
        ItemDetails(
            state = ItemDetailsViewModel.State(
                DataState.Data(podcast),
                DataState.NoData(),
                DataState.Data(
                    AppMediaItemFixtures.episodes(
                        listOf("Episode 1", "Episode 2"),
                        podcast = podcast
                    )
                )
            ),
            isRowMode = isRowMode,
            geEditablePlaylists = suspend { emptyList() },
        )
    }
}

@Preview
@Composable
private fun PreviewPodcastGrid() {
    PreviewPodcast(isRowMode = false)
}

@Preview
@Composable
private fun PreviewAudiobook(isRowMode: Boolean = true) {
    AppTheme(darkTheme = false) {
        ItemDetails(
            state = ItemDetailsViewModel.State(
                DataState.Data(
                    AppMediaItemFixtures.audiobook(
                        "Title",
                        listOf("Chapter 1", "Chapter 2")
                    )
                ),
                DataState.NoData(),
                DataState.NoData()
            ),
            isRowMode = isRowMode,
            geEditablePlaylists = suspend { emptyList() },
        )
    }
}

@Preview
@Composable
private fun PreviewAudiobookGrid() {
    PreviewAudiobook(isRowMode = false)
}
