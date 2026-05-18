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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
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
import io.music_assistant.client.data.model.client.AppMediaItemFixtures
import io.music_assistant.client.data.model.client.Chapter
import io.music_assistant.client.data.model.client.ImageType
import io.music_assistant.client.data.model.client.MediaType
import io.music_assistant.client.data.model.client.QueueOption
import io.music_assistant.client.data.model.client.SortConfig
import io.music_assistant.client.data.model.client.SortOption
import io.music_assistant.client.data.model.client.SubItemContext
import io.music_assistant.client.data.model.client.items.Album
import io.music_assistant.client.data.model.client.items.AppMediaItem
import io.music_assistant.client.data.model.client.items.Artist
import io.music_assistant.client.data.model.client.items.Audiobook
import io.music_assistant.client.data.model.client.items.Genre
import io.music_assistant.client.data.model.client.items.PlayableItem
import io.music_assistant.client.data.model.client.items.Playlist
import io.music_assistant.client.data.model.client.items.Podcast
import io.music_assistant.client.data.model.client.items.PodcastEpisode
import io.music_assistant.client.data.model.client.items.Track
import io.music_assistant.client.settings.ViewMode
import io.music_assistant.client.ui.compose.common.DataState
import io.music_assistant.client.ui.compose.common.ExtractedColorsFetcher
import io.music_assistant.client.ui.compose.common.SortChip
import io.music_assistant.client.ui.compose.common.ToastHost
import io.music_assistant.client.ui.compose.common.ToastState
import io.music_assistant.client.ui.compose.common.items.AlbumWithMenu
import io.music_assistant.client.ui.compose.common.items.ArtistWithMenu
import io.music_assistant.client.ui.compose.common.items.PodcastEpisodeWithMenu
import io.music_assistant.client.ui.compose.common.items.TrackWithMenu
import io.music_assistant.client.ui.compose.common.providers.ProviderIcon
import io.music_assistant.client.ui.compose.common.rememberAnimatedPlayerColors
import io.music_assistant.client.ui.compose.common.rememberExtractedColorsFetcher
import io.music_assistant.client.ui.compose.common.rememberToastState
import io.music_assistant.client.ui.compose.common.viewmodel.ActionsViewModel
import io.music_assistant.client.ui.compose.nav.Screen
import io.music_assistant.client.ui.theme.AppTheme
import musicassistantclient.composeapp.generated.resources.Res
import musicassistantclient.composeapp.generated.resources.cd_toggle_view_mode
import musicassistantclient.composeapp.generated.resources.item_error
import musicassistantclient.composeapp.generated.resources.item_no_data
import org.jetbrains.compose.resources.stringResource

@Composable
fun ItemDetailsScreen(
    itemDetailsViewModel: ItemDetailsViewModel,
    actionsViewModel: ActionsViewModel,
    onBack: () -> Unit,
    onNavigateToItem: (String, MediaType, String) -> Unit,
    contentPadding: PaddingValues,
) {
    val state by itemDetailsViewModel.state.collectAsStateWithLifecycle()
    val toastState = rememberToastState()

    // Collect toasts
    LaunchedEffect(Unit) {
        itemDetailsViewModel.toasts.collect { toast ->
            toastState.showToast(toast)
        }
    }

    ItemDetails(
        contentPadding = contentPadding,
        state = state,
        fetchColors = rememberExtractedColorsFetcher(),
        onBack = onBack,
        viewModeProvider = { type ->
            itemDetailsViewModel.viewMode(type).collectAsStateWithLifecycle().value
        },
        onToggleViewMode = itemDetailsViewModel::toggleViewMode,
        toastState = toastState,
        onNavigateToItem = onNavigateToItem,
        geEditablePlaylists = actionsViewModel::getEditablePlaylists,
        addToPlaylist = actionsViewModel::addToPlaylist,
        onLibraryClick = actionsViewModel::onLibraryClick,
        onFavoriteClick = actionsViewModel::onFavoriteClick,
        onMarkPlayed = actionsViewModel::onMarkPlayed,
        onMarkUnplayed = actionsViewModel::onMarkUnplayed,
        onRemoveFromPlaylist = { id, pos ->
            actionsViewModel.removeFromPlaylist(id, pos, itemDetailsViewModel::reload)
        },
        providerIconFetcher = { modifier, provider ->
            actionsViewModel.getProviderIcon(provider)
                ?.let { ProviderIcon(modifier, it) }
        },
        onPlayClick = itemDetailsViewModel::onPlayClick,
        onChapterClick = itemDetailsViewModel::onChapterClick,
        onChildPlayClick = itemDetailsViewModel::onPlayClick,
        onAlbumsSortChanged = itemDetailsViewModel::onAlbumsSortChanged,
        onPlayableItemsSortChanged = itemDetailsViewModel::onPlayableItemsSortChanged,
    )
}

@Composable
fun ItemDetails(
    contentPadding: PaddingValues = PaddingValues(),
    state: ItemDetailsViewModel.State,
    fetchColors: ExtractedColorsFetcher = { null },
    onBack: () -> Unit = {},
    viewModeProvider: @Composable (MediaType) -> ViewMode = { ViewMode.LIST },
    onToggleViewMode: (MediaType) -> Unit = {},
    toastState: ToastState = rememberToastState(),
    onNavigateToItem: (String, MediaType, String) -> Unit = { _, _, _ -> },
    geEditablePlaylists: suspend () -> List<Playlist> = suspend { emptyList() },
    addToPlaylist: (AppMediaItem, Playlist) -> Unit = { _, _ -> },
    onLibraryClick: (AppMediaItem) -> Unit = {},
    onFavoriteClick: (AppMediaItem) -> Unit = {},
    onMarkPlayed: (AppMediaItem) -> Unit = {},
    onMarkUnplayed: (AppMediaItem) -> Unit = {},
    onRemoveFromPlaylist: (String, Int) -> Unit = { _, _ -> },
    providerIconFetcher: @Composable (Modifier, String) -> Unit = { _, _ -> },
    onPlayClick: (QueueOption, Boolean) -> Unit = { _, _ -> },
    onChapterClick: (Int) -> Unit = {},
    onChildPlayClick: (AppMediaItem, QueueOption, Boolean) -> Unit = { _, _, _ -> },
    onAlbumsSortChanged: (SubItemContext, SortOption) -> Unit = { _, _ -> },
    onPlayableItemsSortChanged: (SubItemContext, SortOption) -> Unit = { _, _ -> },
) {
    val playlistActions = ActionsViewModel.PlaylistActions(
        onLoadPlaylists = geEditablePlaylists,
        onAddToPlaylist = addToPlaylist,
    )

    val libraryActions = ActionsViewModel.LibraryActions(
        onLibraryClick = onLibraryClick,
        onFavoriteClick = onFavoriteClick,
    )

    val progressActions = ActionsViewModel.ProgressActions(
        onMarkPlayed = onMarkPlayed,
        onMarkUnplayed = onMarkUnplayed,
    )

    ItemChildren(
        state = state,
        toastState = toastState,
        fetchColors = fetchColors,
        viewModeProvider = viewModeProvider,
        onNavigateClick = { item ->
            when (item) {
                is Artist,
                is Album,
                is Playlist,
                is Podcast,
                is Audiobook,
                is Genre,
                    -> {
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

private enum class ItemDetailsTab(
    val title: String,
    val sortContext: SubItemContext?,
    val viewMediaType: MediaType?,
) {
    ARTIST_ALBUMS("Albums", SubItemContext.ARTIST_ALBUMS, MediaType.ALBUM),
    ARTIST_TRACKS("Tracks", SubItemContext.ARTIST_TRACKS, MediaType.TRACK),
    ALBUM_TRACKS("Tracks", SubItemContext.ALBUM_TRACKS, MediaType.TRACK),
    PLAYLIST_TRACKS("Tracks", SubItemContext.PLAYLIST_TRACKS, MediaType.TRACK),
    PODCAST_EPISODES("Episodes", SubItemContext.PODCAST_EPISODES, MediaType.TRACK),
    AUDIOBOOK_CHAPTERS("Chapters", null, null),
    GENRE_ARTISTS("Artists", null, MediaType.ARTIST),
    GENRE_ALBUMS("Albums", null, MediaType.ALBUM),
}

private fun tabsFor(item: AppMediaItem): List<ItemDetailsTab> = when (item) {
    is Artist -> listOf(ItemDetailsTab.ARTIST_ALBUMS, ItemDetailsTab.ARTIST_TRACKS)
    is Album -> listOf(ItemDetailsTab.ALBUM_TRACKS)
    is Playlist -> listOf(ItemDetailsTab.PLAYLIST_TRACKS)
    is Podcast -> listOf(ItemDetailsTab.PODCAST_EPISODES)
    is Audiobook -> listOf(ItemDetailsTab.AUDIOBOOK_CHAPTERS)
    is Genre -> listOf(ItemDetailsTab.GENRE_ARTISTS, ItemDetailsTab.GENRE_ALBUMS)
    else -> emptyList()
}

@Composable
private fun ItemChildren(
    state: ItemDetailsViewModel.State,
    toastState: ToastState,
    fetchColors: ExtractedColorsFetcher,
    viewModeProvider: @Composable (MediaType) -> ViewMode,
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
    onToggleViewMode: (MediaType) -> Unit,
    onAlbumsSortChanged: (SubItemContext, SortOption) -> Unit,
    onPlayableItemsSortChanged: (SubItemContext, SortOption) -> Unit,
    contentPadding: PaddingValues,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        when (val itemState = state.itemState) {
            is DataState.Loading -> CenteredProgress()

            is DataState.Error -> CenteredText(
                text = stringResource(Res.string.item_error),
                color = MaterialTheme.colorScheme.error,
            )

            is DataState.Stale,
            is DataState.Data,
                -> {
                val item = when (itemState) {
                    is DataState.Data -> itemState.data
                    is DataState.Stale -> itemState.data
                }
                ItemContent(
                    item = item,
                    state = state,
                    fetchColors = fetchColors,
                    viewModeProvider = viewModeProvider,
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
                    contentPadding = contentPadding,
                )
            }

            is DataState.NoData -> CenteredText(stringResource(Res.string.item_no_data))
        }

        ToastHost(
            toastState = toastState,
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 48.dp),
        )
    }
}

@Composable
private fun ItemContent(
    item: AppMediaItem,
    state: ItemDetailsViewModel.State,
    fetchColors: ExtractedColorsFetcher,
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
    viewModeProvider: @Composable (MediaType) -> ViewMode,
    onToggleViewMode: (MediaType) -> Unit,
    onAlbumsSortChanged: (SubItemContext, SortOption) -> Unit,
    onPlayableItemsSortChanged: (SubItemContext, SortOption) -> Unit,
    contentPadding: PaddingValues,
) {
    val tabs = tabsFor(item)
    var selectedIndex by rememberSaveable(item.mediaType) { mutableStateOf(0) }
    val safeIndex = selectedIndex.coerceIn(0, (tabs.size - 1).coerceAtLeast(0))

    val playerColors by rememberAnimatedPlayerColors(
        imageUrl = item.image(ImageType.THUMB)?.url,
        fallback = MaterialTheme.colorScheme.primary,
        fetchColors = fetchColors,
    )
    val controlTint = playerColors.controlTint

    val heroSlot: @Composable () -> Unit = {
        ItemHeader(
            item = item,
            providerIconFetcher = providerIconFetcher,
            onPlayClick = onPlayItemClick,
            controlTint = controlTint,
        )
    }

    Screen(
        topBar = { scrollBehaviour ->
            ItemTopBar(
                item = item,
                onBack = onBack,
                libraryActions = libraryActions,
                playlistActions = playlistActions.takeIf { item !is Genre },
                navigateToItem = onNavigateClick,
                scrollBehavior = scrollBehaviour,
            )
        },
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
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
                        viewModeProvider = viewModeProvider,
                        onToggleViewMode = onToggleViewMode,
                        controlTint = controlTint,
                    )
                }
                val gridState = rememberLazyGridState()
                Box(modifier = Modifier.weight(1f)) {
                    TabContent(
                        tab = currentTab,
                        item = item,
                        state = state,
                        viewModeProvider = viewModeProvider,
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
    viewModeProvider: @Composable (MediaType) -> ViewMode,
    onToggleViewMode: (MediaType) -> Unit,
    controlTint: Color,
) {
    val currentTab = tabs[selectedIndex]
    val sortCtx = currentTab.sortContext
    val currentSort: SortOption? = when (sortCtx) {
        SubItemContext.ARTIST_ALBUMS -> albumsSortOption
        SubItemContext.ARTIST_TRACKS,
        SubItemContext.ALBUM_TRACKS,
        SubItemContext.PLAYLIST_TRACKS,
        SubItemContext.PODCAST_EPISODES,
            -> playableItemsSortOption

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
            contentColor = controlTint,
            edgePadding = 0.dp,
            indicator = {
                TabRowDefaults.PrimaryIndicator(
                    modifier = Modifier.tabIndicatorOffset(selectedIndex, matchContentSize = true),
                    color = controlTint,
                )
            },
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
        currentTab.viewMediaType?.let { viewMediaType ->
            IconButton(onClick = { onToggleViewMode(viewMediaType) }) {
                Icon(
                    imageVector = when (viewModeProvider(viewMediaType)) {
                        ViewMode.LIST -> Icons.Default.GridView
                        ViewMode.GRID -> Icons.AutoMirrored.Filled.ViewList
                    },
                    contentDescription = stringResource(Res.string.cd_toggle_view_mode),
                )
            }
        }
    }
}

@Composable
private fun TabContent(
    tab: ItemDetailsTab,
    item: AppMediaItem,
    state: ItemDetailsViewModel.State,
    viewModeProvider: @Composable (MediaType) -> ViewMode,
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
        ItemDetailsTab.GENRE_ALBUMS,
            -> AlbumsTabContent(
            albumsState = state.albumsState,
            viewModeProvider = viewModeProvider,
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
        ItemDetailsTab.PODCAST_EPISODES,
            -> PlayablesTabContent(
            playableItemsState = state.playableItemsState,
            parentItem = item,
            viewModeProvider = viewModeProvider,
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
            viewModeProvider = viewModeProvider,
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
            chapters = (item as? Audiobook)?.chapters.orEmpty(),
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
    albumsState: DataState<List<Album>>,
    viewModeProvider: @Composable (MediaType) -> ViewMode,
    onNavigateClick: (AppMediaItem) -> Unit,
    onPlayChildClick: (AppMediaItem, QueueOption, Boolean) -> Unit,
    libraryActions: ActionsViewModel.LibraryActions,
    providerIconFetcher: @Composable (Modifier, String) -> Unit,
    contentPadding: PaddingValues,
    heroSlot: @Composable () -> Unit,
    tabsSlot: @Composable () -> Unit,
    gridState: LazyGridState,
) {
    val viewMode = viewModeProvider(MediaType.ALBUM)
    LazyVerticalGrid(
        state = gridState,
        modifier = Modifier.fillMaxSize().testTag("LazyVerticalGrid"),
        columns = GridCells.Adaptive(minSize = 108.dp),
        contentPadding = contentPadding + PaddingValues(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) { heroSlot() }
        item(span = { GridItemSpan(maxLineSpan) }) { tabsSlot() }

        when (albumsState) {
            is DataState.Data -> items(
                items = albumsState.data,
                span = when (viewMode) {
                    ViewMode.LIST -> {
                        { GridItemSpan(maxLineSpan) }
                    }

                    ViewMode.GRID -> null
                },
            ) { album ->
                AlbumWithMenu(
                    item = album,
                    viewMode = viewMode,
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
    artistsState: DataState<List<Artist>>,
    viewModeProvider: @Composable (MediaType) -> ViewMode,
    onNavigateClick: (AppMediaItem) -> Unit,
    onPlayChildClick: (AppMediaItem, QueueOption, Boolean) -> Unit,
    libraryActions: ActionsViewModel.LibraryActions,
    providerIconFetcher: @Composable (Modifier, String) -> Unit,
    contentPadding: PaddingValues,
    heroSlot: @Composable () -> Unit,
    tabsSlot: @Composable () -> Unit,
    gridState: LazyGridState,
) {
    val viewMode = viewModeProvider(MediaType.ARTIST)
    LazyVerticalGrid(
        state = gridState,
        modifier = Modifier.fillMaxSize().testTag("LazyVerticalGrid"),
        columns = GridCells.Adaptive(minSize = 108.dp),
        contentPadding = contentPadding + PaddingValues(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) { heroSlot() }
        item(span = { GridItemSpan(maxLineSpan) }) { tabsSlot() }

        when (artistsState) {
            is DataState.Data -> items(
                items = artistsState.data,
                span = when (viewMode) {
                    ViewMode.LIST -> {
                        { GridItemSpan(maxLineSpan) }
                    }

                    ViewMode.GRID -> null
                },
            ) { artist ->
                ArtistWithMenu(
                    item = artist,
                    viewMode = viewMode,
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
    viewModeProvider: @Composable (MediaType) -> ViewMode,
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
    val viewMode = viewModeProvider(MediaType.TRACK)
    LazyVerticalGrid(
        state = gridState,
        modifier = Modifier.fillMaxSize().testTag("LazyVerticalGrid"),
        columns = GridCells.Adaptive(minSize = 108.dp),
        contentPadding = contentPadding + PaddingValues(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) { heroSlot() }
        item(span = { GridItemSpan(maxLineSpan) }) { tabsSlot() }

        when (playableItemsState) {
            is DataState.Data -> {
                playableItemsState.data.forEachIndexed { index, track ->
                    item(
                        span = when (viewMode) {
                            ViewMode.LIST -> {
                                { GridItemSpan(maxLineSpan) }
                            }

                            ViewMode.GRID -> null
                        },
                    ) {
                        when (track) {
                            is Track -> TrackWithMenu(
                                item = track,
                                viewMode = viewMode,
                                onPlayOption = onPlayChildClick,
                                playlistActions = playlistActions,
                                onRemoveFromPlaylist = if (parentItem is Playlist && parentItem.isEditable) {
                                    { onRemoveFromPlaylist(parentItem.itemId, index) }
                                } else {
                                    null
                                },
                                libraryActions = libraryActions,
                                providerIconFetcher = providerIconFetcher,
                            )

                            is PodcastEpisode -> PodcastEpisodeWithMenu(
                                item = track,
                                viewMode = viewMode,
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
    chapters: List<Chapter>,
    onChapterClick: (Int) -> Unit,
    contentPadding: PaddingValues,
    heroSlot: @Composable () -> Unit,
    tabsSlot: @Composable () -> Unit,
    gridState: LazyGridState,
) {
    LazyVerticalGrid(
        state = gridState,
        modifier = Modifier.fillMaxSize().testTag("LazyVerticalGrid"),
        columns = GridCells.Adaptive(minSize = 108.dp),
        contentPadding = contentPadding + PaddingValues(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
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
    chapter: Chapter,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = chapter.name,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        val durationMinutes = (chapter.duration / 60).toInt()
        if (durationMinutes > 0) {
            Text(
                text = "${durationMinutes}m",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 8.dp),
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
                    DataState.Loading(),
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
                            AppMediaItemFixtures.album(name = "Album 2", artist = artist),
                        ),
                    ),
                    DataState.NoData(),
                ),
                viewModeProvider = { if (isRowMode) ViewMode.LIST else ViewMode.GRID },
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
                        album = album,
                    ),
                ),
            ),
            viewModeProvider = { if (isRowMode) ViewMode.LIST else ViewMode.GRID },
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
                DataState.Data(AppMediaItemFixtures.tracks(listOf("Track 1", "Track 2"))),
            ),
            viewModeProvider = { if (isRowMode) ViewMode.LIST else ViewMode.GRID },
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
                        podcast = podcast,
                    ),
                ),
            ),
            viewModeProvider = { if (isRowMode) ViewMode.LIST else ViewMode.GRID },
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
                        listOf("Chapter 1", "Chapter 2"),
                    ),
                ),
                DataState.NoData(),
                DataState.NoData(),
            ),
            viewModeProvider = { if (isRowMode) ViewMode.LIST else ViewMode.GRID },
            geEditablePlaylists = suspend { emptyList() },
        )
    }
}

@Preview
@Composable
private fun PreviewAudiobookGrid() {
    PreviewAudiobook(isRowMode = false)
}
