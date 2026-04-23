@file:OptIn(ExperimentalMaterial3Api::class)

package io.music_assistant.client.ui.compose.item

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.plus
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.music_assistant.client.data.model.client.AppMediaItem
import io.music_assistant.client.data.model.client.AppMediaItemFixtures
import io.music_assistant.client.data.model.client.PlayableItem
import io.music_assistant.client.data.model.client.SortConfig
import io.music_assistant.client.data.model.client.SortOption
import io.music_assistant.client.data.model.client.SubItemContext
import io.music_assistant.client.data.model.server.MediaItemChapter
import io.music_assistant.client.data.model.server.MediaType
import io.music_assistant.client.data.model.server.QueueOption
import io.music_assistant.client.ui.compose.common.DataState
import io.music_assistant.client.ui.compose.common.SortChip
import io.music_assistant.client.ui.compose.common.ToastHost
import io.music_assistant.client.ui.compose.common.ToastState
import io.music_assistant.client.ui.compose.common.items.AlbumWithMenu
import io.music_assistant.client.ui.compose.common.items.ArtistWithMenu
import io.music_assistant.client.ui.compose.common.items.PodcastEpisodeWithMenu
import io.music_assistant.client.ui.compose.common.items.TrackWithMenu
import io.music_assistant.client.ui.compose.common.providers.ProviderIcon
import io.music_assistant.client.ui.compose.common.rememberToastState
import io.music_assistant.client.ui.compose.common.viewmodel.ActionsViewModel
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
        viewModel::onPlayClick,
        viewModel::onAlbumsSortChanged,
        viewModel::onPlayableItemsSortChanged,
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
    onChildPlayClick: (AppMediaItem, QueueOption, Boolean) -> Unit = { _, _, _ -> },
    onAlbumsSortChanged: (SubItemContext, SortOption) -> Unit = { _, _ -> },
    onPlayableItemsSortChanged: (SubItemContext, SortOption) -> Unit = { _, _ -> },
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
        onAlbumsSortChanged = onAlbumsSortChanged,
        onPlayableItemsSortChanged = onPlayableItemsSortChanged,
        contentPadding = contentPadding,
    )
}

private enum class ItemDetailsTab(val title: String, val sortContext: SubItemContext?) {
    ARTIST_ALBUMS("Albums", SubItemContext.ARTIST_ALBUMS),
    ARTIST_TRACKS("Tracks", SubItemContext.ARTIST_TRACKS),
    ALBUM_TRACKS("Tracks", SubItemContext.ALBUM_TRACKS),
    PLAYLIST_TRACKS("Tracks", SubItemContext.PLAYLIST_TRACKS),
    PODCAST_EPISODES("Episodes", SubItemContext.PODCAST_EPISODES),
    AUDIOBOOK_CHAPTERS("Chapters", null),
    GENRE_ARTISTS("Artists", null),
    GENRE_ALBUMS("Albums", null),
}

private fun tabsFor(item: AppMediaItem): List<ItemDetailsTab> = when (item) {
    is AppMediaItem.Artist -> listOf(ItemDetailsTab.ARTIST_ALBUMS, ItemDetailsTab.ARTIST_TRACKS)
    is AppMediaItem.Album -> listOf(ItemDetailsTab.ALBUM_TRACKS)
    is AppMediaItem.Playlist -> listOf(ItemDetailsTab.PLAYLIST_TRACKS)
    is AppMediaItem.Podcast -> listOf(ItemDetailsTab.PODCAST_EPISODES)
    is AppMediaItem.Audiobook -> listOf(ItemDetailsTab.AUDIOBOOK_CHAPTERS)
    is AppMediaItem.Genre -> listOf(ItemDetailsTab.GENRE_ARTISTS, ItemDetailsTab.GENRE_ALBUMS)
    else -> emptyList()
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
    onAlbumsSortChanged: (SubItemContext, SortOption) -> Unit,
    onPlayableItemsSortChanged: (SubItemContext, SortOption) -> Unit,
    contentPadding: PaddingValues,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        when (val itemState = state.itemState) {
            is DataState.Loading -> CenteredProgress()

            is DataState.Error -> CenteredText(
                text = "Error loading item",
                color = MaterialTheme.colorScheme.error,
            )

            is DataState.Stale,
            is DataState.Data -> {
                val item = when (itemState) {
                    is DataState.Data -> itemState.data
                    is DataState.Stale -> itemState.data
                }
                ItemContent(
                    item = item,
                    state = state,
                    serverUrl = serverUrl,
                    isRowMode = isRowMode,
                    onNavigateClick = onNavigateClick,
                    onPlayItemClick = onPlayItemClick,
                    onPlayChildClick = onPlayChildClick,
                    onChapterClick = onChapterClick,
                    playlistActions = playlistActions,
                    progressActions = progressActions,
                    onRemoveFromPlaylist = onRemoveFromPlaylist,
                    libraryActions = libraryActions,
                    providerIconFetcher = providerIconFetcher,
                    onBack = onBack,
                    onToggleViewMode = onToggleViewMode,
                    onAlbumsSortChanged = onAlbumsSortChanged,
                    onPlayableItemsSortChanged = onPlayableItemsSortChanged,
                    contentPadding = contentPadding
                )
            }

            is DataState.NoData -> CenteredText("No data available")
        }

        ToastHost(
            toastState = toastState,
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 48.dp)
        )
    }
}

@Composable
private fun ItemContent(
    item: AppMediaItem,
    state: ItemDetailsViewModel.State,
    serverUrl: String?,
    isRowMode: Boolean,
    onNavigateClick: (AppMediaItem) -> Unit,
    onPlayItemClick: (QueueOption, Boolean) -> Unit,
    onPlayChildClick: (AppMediaItem, QueueOption, Boolean) -> Unit,
    onChapterClick: (Int) -> Unit,
    playlistActions: ActionsViewModel.PlaylistActions,
    progressActions: ActionsViewModel.ProgressActions?,
    onRemoveFromPlaylist: (String, Int) -> Unit,
    libraryActions: ActionsViewModel.LibraryActions,
    providerIconFetcher: @Composable (Modifier, String) -> Unit,
    onBack: () -> Unit,
    onToggleViewMode: () -> Unit,
    onAlbumsSortChanged: (SubItemContext, SortOption) -> Unit,
    onPlayableItemsSortChanged: (SubItemContext, SortOption) -> Unit,
    contentPadding: PaddingValues
) {
    val tabs = tabsFor(item)
    var selectedIndex by rememberSaveable(item.mediaType) { mutableStateOf(0) }
    val safeIndex = selectedIndex.coerceIn(0, (tabs.size - 1).coerceAtLeast(0))

    val heroSlot: @Composable () -> Unit = {
        ItemHeader(
            item = item,
            serverUrl = serverUrl,
            providerIconFetcher = providerIconFetcher,
            onPlayClick = onPlayItemClick
        )
    }

    Surface {
        Column(modifier = Modifier.fillMaxSize()) {
            ItemTopBar(
                item = item,
                isRowMode = isRowMode,
                onBack = onBack,
                onToggleViewMode = onToggleViewMode,
                libraryActions = libraryActions,
                playlistActions = playlistActions.takeIf { item !is AppMediaItem.Genre },
                goToArtist = if (item is AppMediaItem.Album && item.artists.isNotEmpty()) {
                    { onNavigateClick(item.artists[0]) }
                } else {
                    null
                }
            )

            if (tabs.isEmpty()) {
                heroSlot()
            } else {
                val currentTab = tabs[safeIndex]
                val tabsSlot: @Composable () -> Unit = {
                    TabsBar(
                        tabs = tabs,
                        selectedIndex = safeIndex,
                        onTabSelected = { selectedIndex = it },
                        albumsSortOption = state.albumsSortOption,
                        playableItemsSortOption = state.playableItemsSortOption,
                        onAlbumsSortChanged = onAlbumsSortChanged,
                        onPlayableItemsSortChanged = onPlayableItemsSortChanged,
                    )
                }
                val gridState = rememberLazyGridState()
                Box(modifier = Modifier.weight(1f)) {
                    TabContent(
                        tab = currentTab,
                        item = item,
                        state = state,
                        serverUrl = serverUrl,
                        isRowMode = isRowMode,
                        onNavigateClick = onNavigateClick,
                        onPlayChildClick = onPlayChildClick,
                        onChapterClick = onChapterClick,
                        playlistActions = playlistActions,
                        progressActions = progressActions,
                        onRemoveFromPlaylist = onRemoveFromPlaylist,
                        libraryActions = libraryActions,
                        providerIconFetcher = providerIconFetcher,
                        contentPadding = contentPadding,
                        heroSlot = heroSlot,
                        tabsSlot = tabsSlot,
                        gridState = gridState,
                    )
                }
            }
        }
    }
}

@Composable
private fun TabsBar(
    tabs: List<ItemDetailsTab>,
    selectedIndex: Int,
    onTabSelected: (Int) -> Unit,
    albumsSortOption: SortOption?,
    playableItemsSortOption: SortOption?,
    onAlbumsSortChanged: (SubItemContext, SortOption) -> Unit,
    onPlayableItemsSortChanged: (SubItemContext, SortOption) -> Unit,
) {
    val currentTab = tabs[selectedIndex]
    val sortCtx = currentTab.sortContext
    val currentSort: SortOption? = when (sortCtx) {
        SubItemContext.ARTIST_ALBUMS -> albumsSortOption
        SubItemContext.ARTIST_TRACKS,
        SubItemContext.ALBUM_TRACKS,
        SubItemContext.PLAYLIST_TRACKS,
        SubItemContext.PODCAST_EPISODES -> playableItemsSortOption

        null -> null
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PrimaryScrollableTabRow(
            selectedTabIndex = selectedIndex,
            containerColor = Color.Transparent,
            edgePadding = 0.dp,
            modifier = Modifier.weight(1f),
        ) {
            tabs.forEachIndexed { i, tab ->
                Tab(
                    selected = i == selectedIndex,
                    onClick = { onTabSelected(i) },
                    text = { Text(tab.title) },
                )
            }
        }
        if (sortCtx != null && currentSort != null) {
            SortChip(
                currentSort = currentSort,
                availableFields = SortConfig.fieldsFor(sortCtx),
                onSortChanged = { opt ->
                    if (sortCtx == SubItemContext.ARTIST_ALBUMS) {
                        onAlbumsSortChanged(sortCtx, opt)
                    } else {
                        onPlayableItemsSortChanged(sortCtx, opt)
                    }
                },
            )
        }
    }
}

@Composable
private fun TabContent(
    tab: ItemDetailsTab,
    item: AppMediaItem,
    state: ItemDetailsViewModel.State,
    serverUrl: String?,
    isRowMode: Boolean,
    onNavigateClick: (AppMediaItem) -> Unit,
    onPlayChildClick: (AppMediaItem, QueueOption, Boolean) -> Unit,
    onChapterClick: (Int) -> Unit,
    playlistActions: ActionsViewModel.PlaylistActions,
    progressActions: ActionsViewModel.ProgressActions?,
    onRemoveFromPlaylist: (String, Int) -> Unit,
    libraryActions: ActionsViewModel.LibraryActions,
    providerIconFetcher: @Composable (Modifier, String) -> Unit,
    contentPadding: PaddingValues,
    heroSlot: @Composable () -> Unit,
    tabsSlot: @Composable () -> Unit,
    gridState: LazyGridState,
) {
    when (tab) {
        ItemDetailsTab.ARTIST_ALBUMS,
        ItemDetailsTab.GENRE_ALBUMS -> AlbumsTabContent(
            albumsState = state.albumsState,
            isRowMode = isRowMode,
            serverUrl = serverUrl,
            onNavigateClick = onNavigateClick,
            onPlayChildClick = onPlayChildClick,
            libraryActions = libraryActions,
            providerIconFetcher = providerIconFetcher,
            contentPadding = contentPadding,
            heroSlot = heroSlot,
            tabsSlot = tabsSlot,
            gridState = gridState,
        )

        ItemDetailsTab.ARTIST_TRACKS,
        ItemDetailsTab.ALBUM_TRACKS,
        ItemDetailsTab.PLAYLIST_TRACKS,
        ItemDetailsTab.PODCAST_EPISODES -> PlayablesTabContent(
            playableItemsState = state.playableItemsState,
            parentItem = item,
            isRowMode = isRowMode,
            serverUrl = serverUrl,
            onPlayChildClick = onPlayChildClick,
            playlistActions = playlistActions,
            progressActions = progressActions,
            onRemoveFromPlaylist = onRemoveFromPlaylist,
            libraryActions = libraryActions,
            providerIconFetcher = providerIconFetcher,
            contentPadding = contentPadding,
            heroSlot = heroSlot,
            tabsSlot = tabsSlot,
            gridState = gridState,
        )

        ItemDetailsTab.GENRE_ARTISTS -> ArtistsTabContent(
            artistsState = state.artistsState,
            isRowMode = isRowMode,
            serverUrl = serverUrl,
            onNavigateClick = onNavigateClick,
            onPlayChildClick = onPlayChildClick,
            libraryActions = libraryActions,
            providerIconFetcher = providerIconFetcher,
            contentPadding = contentPadding,
            heroSlot = heroSlot,
            tabsSlot = tabsSlot,
            gridState = gridState,
        )

        ItemDetailsTab.AUDIOBOOK_CHAPTERS -> ChaptersTabContent(
            chapters = (item as? AppMediaItem.Audiobook)?.chapters.orEmpty(),
            onChapterClick = onChapterClick,
            contentPadding = contentPadding,
            heroSlot = heroSlot,
            tabsSlot = tabsSlot,
            gridState = gridState,
        )
    }
}

@Composable
private fun AlbumsTabContent(
    albumsState: DataState<List<AppMediaItem.Album>>,
    isRowMode: Boolean,
    serverUrl: String?,
    onNavigateClick: (AppMediaItem) -> Unit,
    onPlayChildClick: (AppMediaItem, QueueOption, Boolean) -> Unit,
    libraryActions: ActionsViewModel.LibraryActions,
    providerIconFetcher: @Composable (Modifier, String) -> Unit,
    contentPadding: PaddingValues,
    heroSlot: @Composable () -> Unit,
    tabsSlot: @Composable () -> Unit,
    gridState: LazyGridState,
) {
    LazyVerticalGrid(
        state = gridState,
        modifier = Modifier.fillMaxSize().testTag("LazyVerticalGrid"),
        columns = GridCells.Adaptive(minSize = 96.dp),
        contentPadding = contentPadding + PaddingValues(8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) { heroSlot() }
        item(span = { GridItemSpan(maxLineSpan) }) { tabsSlot() }

        when (albumsState) {
            is DataState.Data -> items(
                albumsState.data,
                span = if (isRowMode) {
                    { GridItemSpan(maxLineSpan) }
                } else null,
            ) { album ->
                AlbumWithMenu(
                    item = album,
                    rowMode = isRowMode,
                    serverUrl = serverUrl,
                    onNavigateClick = onNavigateClick,
                    onPlayOption = onPlayChildClick,
                    libraryActions = libraryActions,
                    providerIconFetcher = providerIconFetcher,
                )
            }

            is DataState.Loading -> item(span = { GridItemSpan(maxLineSpan) }) {
                InlineProgress()
            }

            else -> Unit
        }
    }
}

@Composable
private fun ArtistsTabContent(
    artistsState: DataState<List<AppMediaItem.Artist>>,
    isRowMode: Boolean,
    serverUrl: String?,
    onNavigateClick: (AppMediaItem) -> Unit,
    onPlayChildClick: (AppMediaItem, QueueOption, Boolean) -> Unit,
    libraryActions: ActionsViewModel.LibraryActions,
    providerIconFetcher: @Composable (Modifier, String) -> Unit,
    contentPadding: PaddingValues,
    heroSlot: @Composable () -> Unit,
    tabsSlot: @Composable () -> Unit,
    gridState: LazyGridState,
) {
    LazyVerticalGrid(
        state = gridState,
        modifier = Modifier.fillMaxSize().testTag("LazyVerticalGrid"),
        columns = GridCells.Adaptive(minSize = 96.dp),
        contentPadding = contentPadding + PaddingValues(8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) { heroSlot() }
        item(span = { GridItemSpan(maxLineSpan) }) { tabsSlot() }

        when (artistsState) {
            is DataState.Data -> items(
                artistsState.data,
                span = if (isRowMode) {
                    { GridItemSpan(maxLineSpan) }
                } else null,
            ) { artist ->
                ArtistWithMenu(
                    item = artist,
                    rowMode = isRowMode,
                    serverUrl = serverUrl,
                    onNavigateClick = onNavigateClick,
                    onPlayOption = onPlayChildClick,
                    libraryActions = libraryActions,
                    providerIconFetcher = providerIconFetcher,
                )
            }

            is DataState.Loading -> item(span = { GridItemSpan(maxLineSpan) }) {
                InlineProgress()
            }

            else -> Unit
        }
    }
}

@Composable
private fun PlayablesTabContent(
    playableItemsState: DataState<List<PlayableItem>>,
    parentItem: AppMediaItem,
    isRowMode: Boolean,
    serverUrl: String?,
    onPlayChildClick: (AppMediaItem, QueueOption, Boolean) -> Unit,
    playlistActions: ActionsViewModel.PlaylistActions,
    progressActions: ActionsViewModel.ProgressActions?,
    onRemoveFromPlaylist: (String, Int) -> Unit,
    libraryActions: ActionsViewModel.LibraryActions,
    providerIconFetcher: @Composable (Modifier, String) -> Unit,
    contentPadding: PaddingValues,
    heroSlot: @Composable () -> Unit,
    tabsSlot: @Composable () -> Unit,
    gridState: LazyGridState,
) {
    LazyVerticalGrid(
        state = gridState,
        modifier = Modifier.fillMaxSize().testTag("LazyVerticalGrid"),
        columns = GridCells.Adaptive(minSize = 96.dp),
        contentPadding = contentPadding + PaddingValues(8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) { heroSlot() }
        item(span = { GridItemSpan(maxLineSpan) }) { tabsSlot() }

        when (playableItemsState) {
            is DataState.Data -> {
                playableItemsState.data.forEachIndexed { index, track ->
                    item(
                        span = if (isRowMode) {
                            { GridItemSpan(maxLineSpan) }
                        } else null,
                    ) {
                        when (track) {
                            is AppMediaItem.Track -> TrackWithMenu(
                                item = track,
                                serverUrl = serverUrl,
                                rowMode = isRowMode,
                                onPlayOption = onPlayChildClick,
                                playlistActions = playlistActions,
                                onRemoveFromPlaylist = if (parentItem is AppMediaItem.Playlist && parentItem.isEditable == true) {
                                    { onRemoveFromPlaylist(parentItem.itemId, index) }
                                } else null,
                                libraryActions = libraryActions,
                                providerIconFetcher = providerIconFetcher,
                            )

                            is AppMediaItem.PodcastEpisode -> PodcastEpisodeWithMenu(
                                item = track,
                                serverUrl = serverUrl,
                                rowMode = isRowMode,
                                onPlayOption = onPlayChildClick,
                                playlistActions = null,
                                libraryActions = libraryActions,
                                progressActions = progressActions,
                                providerIconFetcher = providerIconFetcher,
                            )
                        }
                    }
                }
            }

            is DataState.Loading -> item(span = { GridItemSpan(maxLineSpan) }) {
                InlineProgress()
            }

            else -> Unit
        }
    }
}

@Composable
private fun ChaptersTabContent(
    chapters: List<MediaItemChapter>,
    onChapterClick: (Int) -> Unit,
    contentPadding: PaddingValues,
    heroSlot: @Composable () -> Unit,
    tabsSlot: @Composable () -> Unit,
    gridState: LazyGridState,
) {
    LazyVerticalGrid(
        state = gridState,
        modifier = Modifier.fillMaxSize().testTag("LazyVerticalGrid"),
        columns = GridCells.Adaptive(minSize = 96.dp),
        contentPadding = contentPadding,
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) { heroSlot() }
        item(span = { GridItemSpan(maxLineSpan) }) { tabsSlot() }

        chapters.forEach { chapter ->
            item(span = { GridItemSpan(maxLineSpan) }) {
                ChapterRow(
                    chapter = chapter,
                    onClick = { onChapterClick(chapter.position) },
                )
            }
        }
    }
}

@Composable
private fun CenteredProgress() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator()
    }
}

@Composable
private fun InlineProgress() {
    Box(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator()
    }
}

@Composable
private fun CenteredText(text: String, color: Color = Color.Unspecified) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = text, color = color)
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
