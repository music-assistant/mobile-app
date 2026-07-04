@file:OptIn(ExperimentalMaterial3Api::class)

package io.music_assistant.client.ui.compose.item

import androidx.compose.foundation.LocalOverscrollFactory
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
import androidx.compose.foundation.lazy.grid.LazyGridItemSpanScope
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.music_assistant.client.data.model.client.AppMediaItemFixtures
import io.music_assistant.client.data.model.client.Chapter
import io.music_assistant.client.data.model.client.ClickContext
import io.music_assistant.client.data.model.client.ImageType
import io.music_assistant.client.data.model.client.MediaType
import io.music_assistant.client.data.model.client.QueueOption
import io.music_assistant.client.data.model.client.SortConfig
import io.music_assistant.client.data.model.client.SortField
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
import io.music_assistant.client.data.model.client.stringResource
import io.music_assistant.client.data.model.client.toClickContext
import io.music_assistant.client.settings.ViewMode
import io.music_assistant.client.ui.compose.common.CenteredProgress
import io.music_assistant.client.ui.compose.common.CenteredText
import io.music_assistant.client.ui.compose.common.DataState
import io.music_assistant.client.ui.compose.common.ExtractedColors
import io.music_assistant.client.ui.compose.common.ExtractedColorsSource
import io.music_assistant.client.ui.compose.common.SortChip
import io.music_assistant.client.ui.compose.common.ToastHost
import io.music_assistant.client.ui.compose.common.ToastState
import io.music_assistant.client.ui.compose.common.items.AlbumWithMenu
import io.music_assistant.client.ui.compose.common.items.ArtistWithMenu
import io.music_assistant.client.ui.compose.common.items.LibraryActions
import io.music_assistant.client.ui.compose.common.items.PlayHandler
import io.music_assistant.client.ui.compose.common.items.PlaylistActions
import io.music_assistant.client.ui.compose.common.items.PodcastEpisodeWithMenu
import io.music_assistant.client.ui.compose.common.items.ProgressActions
import io.music_assistant.client.ui.compose.common.items.ProvideClickActions
import io.music_assistant.client.ui.compose.common.items.TrackWithMenu
import io.music_assistant.client.ui.compose.common.items.lazyListOccurrenceKeys
import io.music_assistant.client.ui.compose.common.items.playableLazyListOccurrenceKeys
import io.music_assistant.client.ui.compose.common.items.supportsAddToPlaylist
import io.music_assistant.client.ui.compose.common.providers.ProviderIcon
import io.music_assistant.client.ui.compose.common.rememberAnimatedPlayerColors
import io.music_assistant.client.ui.compose.common.rememberExtractedColorsSource
import io.music_assistant.client.ui.compose.common.rememberToastState
import io.music_assistant.client.ui.compose.common.viewmodel.ActionsViewModel
import io.music_assistant.client.ui.compose.nav.TopBarLayout
import io.music_assistant.client.ui.fullBleed
import io.music_assistant.client.ui.theme.AppTheme
import io.music_assistant.client.utils.gridItemMinSize
import musicassistantclient.composeapp.generated.resources.Res
import musicassistantclient.composeapp.generated.resources.album_disc_header
import musicassistantclient.composeapp.generated.resources.cd_toggle_view_mode
import musicassistantclient.composeapp.generated.resources.item_error
import musicassistantclient.composeapp.generated.resources.item_no_data
import musicassistantclient.composeapp.generated.resources.library_empty
import musicassistantclient.composeapp.generated.resources.library_error
import musicassistantclient.composeapp.generated.resources.media_type_chapters
import musicassistantclient.composeapp.generated.resources.media_type_episodes
import org.jetbrains.compose.resources.StringResource
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
        onBack = onBack,
        viewModeProvider = { type ->
            itemDetailsViewModel.viewMode(type).collectAsStateWithLifecycle().value
        },
        onToggleViewMode = itemDetailsViewModel::toggleViewMode,
        toastState = toastState,
        onNavigateToItem = onNavigateToItem,
        geEditablePlaylists = actionsViewModel::getEditablePlaylists,
        createPlaylist = actionsViewModel::createPlaylist,
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
        onTabSelected = itemDetailsViewModel::onTabSelected,
        onLoadSimilarArtists = itemDetailsViewModel::loadSimilarArtists,
    )
}

@Composable
fun ItemDetails(
    contentPadding: PaddingValues = PaddingValues(),
    state: ItemDetailsViewModel.State,
    onBack: () -> Unit = {},
    viewModeProvider: @Composable (MediaType) -> ViewMode = { ViewMode.LIST },
    onToggleViewMode: (MediaType) -> Unit = {},
    toastState: ToastState = rememberToastState(),
    onNavigateToItem: (String, MediaType, String) -> Unit = { _, _, _ -> },
    geEditablePlaylists: suspend () -> List<Playlist> = suspend { emptyList() },
    fetchColors: ExtractedColorsSource? = null,
    createPlaylist: suspend (String) -> Playlist? = { null },
    addToPlaylist: (String?, Playlist) -> Unit = { _, _ -> },
    onLibraryClick: (AppMediaItem) -> Unit = {},
    onFavoriteClick: (AppMediaItem) -> Unit = {},
    onMarkPlayed: (AppMediaItem) -> Unit = {},
    onMarkUnplayed: (AppMediaItem) -> Unit = {},
    onRemoveFromPlaylist: (String, Int) -> Unit = { _, _ -> },
    providerIconFetcher: @Composable (Modifier, String) -> Unit = { _, _ -> },
    onPlayClick: (QueueOption, Boolean) -> Unit = { _, _ -> },
    onChapterClick: (Int) -> Unit = {},
    onChildPlayClick: PlayHandler<AppMediaItem> = { _, _, _, _ -> },
    onAlbumsSortChanged: (SubItemContext, SortOption) -> Unit = { _, _ -> },
    onPlayableItemsSortChanged: (SubItemContext, SortOption) -> Unit = { _, _ -> },
    onTabSelected: (ItemDetailsTab) -> Unit = {},
    onLoadSimilarArtists: () -> Unit = {},
) {
    val playlistActions = object : PlaylistActions {
        override suspend fun getEditablePlaylists(): List<Playlist> {
            return geEditablePlaylists()
        }

        override fun addToPlaylist(
            itemUri: String?,
            playlist: Playlist,
        ) {
            addToPlaylist(itemUri, playlist)
        }

        override suspend fun createPlaylist(name: String): Playlist? =
            createPlaylist(name)
    }

    val libraryActions = object : LibraryActions {
        override fun onLibraryClick(item: AppMediaItem) {
            onLibraryClick(item)
        }

        override fun onFavoriteClick(item: AppMediaItem) {
            onFavoriteClick(item)
        }
    }

    val progressActions = object : ProgressActions {
        override fun onMarkPlayed(item: AppMediaItem) {
            onMarkPlayed(item)
        }

        override fun onMarkUnplayed(item: AppMediaItem) {
            onMarkUnplayed(item)
        }
    }

    ItemChildren(
        state = state,
        toastState = toastState,
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
        fetchColors = fetchColors,
        onBack = onBack,
        onToggleViewMode = onToggleViewMode,
        onAlbumsSortChanged = onAlbumsSortChanged,
        onPlayableItemsSortChanged = onPlayableItemsSortChanged,
        onTabSelected = onTabSelected,
        onLoadSimilarArtists = onLoadSimilarArtists,
        contentPadding = contentPadding,
    )
}

/** Tab label. Chapters have a dedicated string; every other tab borrows its media-type label. */
private fun ItemDetailsTab.stringResource(): StringResource? = when (this) {
    ItemDetailsTab.AUDIOBOOK_CHAPTERS -> Res.string.media_type_chapters
    ItemDetailsTab.PODCAST_EPISODES -> Res.string.media_type_episodes
    else -> viewMediaType?.stringResource()
}

@Composable
private fun ItemChildren(
    state: ItemDetailsViewModel.State,
    toastState: ToastState,
    viewModeProvider: @Composable (MediaType) -> ViewMode,
    onNavigateClick: (AppMediaItem) -> Unit,
    onPlayItemClick: (QueueOption, Boolean) -> Unit,
    onPlayChildClick: PlayHandler<AppMediaItem>,
    onChapterClick: (Int) -> Unit,
    playlistActions: PlaylistActions,
    progressActions: ProgressActions? = null,
    onRemoveFromPlaylist: (String, Int) -> Unit,
    libraryActions: LibraryActions,
    providerIconFetcher: (@Composable (Modifier, String) -> Unit),
    fetchColors: ExtractedColorsSource?,
    onBack: () -> Unit,
    onToggleViewMode: (MediaType) -> Unit,
    onAlbumsSortChanged: (SubItemContext, SortOption) -> Unit,
    onPlayableItemsSortChanged: (SubItemContext, SortOption) -> Unit,
    onTabSelected: (ItemDetailsTab) -> Unit,
    onLoadSimilarArtists: () -> Unit,
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
                    fetchColors = fetchColors,
                    onBack = onBack,
                    onToggleViewMode = onToggleViewMode,
                    onAlbumsSortChanged = onAlbumsSortChanged,
                    onPlayableItemsSortChanged = onPlayableItemsSortChanged,
                    contentPadding = contentPadding,
                    onTabSelected = onTabSelected,
                    onLoadSimilarArtists = onLoadSimilarArtists,
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
    onNavigateClick: (AppMediaItem) -> Unit,
    onPlayItemClick: (QueueOption, Boolean) -> Unit,
    onPlayChildClick: PlayHandler<AppMediaItem>,
    onChapterClick: (Int) -> Unit,
    playlistActions: PlaylistActions,
    progressActions: ProgressActions?,
    onRemoveFromPlaylist: (String, Int) -> Unit,
    libraryActions: LibraryActions,
    providerIconFetcher: @Composable (Modifier, String) -> Unit,
    fetchColors: ExtractedColorsSource?,
    onBack: () -> Unit,
    viewModeProvider: @Composable (MediaType) -> ViewMode,
    onToggleViewMode: (MediaType) -> Unit,
    onAlbumsSortChanged: (SubItemContext, SortOption) -> Unit,
    onPlayableItemsSortChanged: (SubItemContext, SortOption) -> Unit,
    contentPadding: PaddingValues,
    onTabSelected: (ItemDetailsTab) -> Unit,
    onLoadSimilarArtists: () -> Unit,
) {
    // Tabs, the loading gate, and the selected tab are all derived in ItemDetailsViewModel.State.
    val tabs = state.tabs

    // Similar-artists sheet visibility. Saveable so it survives rotation; the load is driven by the
    // effect below rather than the click, so after process death the reopened sheet re-fetches
    // (VM state reset to NoData) instead of showing a blank list.
    var showSimilarArtists by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(showSimilarArtists) {
        if (showSimilarArtists) onLoadSimilarArtists()
    }

    // Artwork-driven header colors. Library items carry no server palette, so colors are
    // extracted locally from the thumbnail (cached by DominantColorViewModel) — same path
    // as the player. The fetcher is Koin-backed, so fall back to a no-op when one isn't
    // supplied and there's no Koin graph (under @Preview or in tests).
    val resolvedColorsSource: ExtractedColorsSource = fetchColors
        ?: if (LocalInspectionMode.current) {
            object : ExtractedColorsSource {
                override fun peek(imageUrl: String): ExtractedColors? = null
                override suspend fun fetch(imageUrl: String): ExtractedColors? = null
            }
        } else {
            rememberExtractedColorsSource()
        }
    val colors by rememberAnimatedPlayerColors(
        imageUrl = item.image(ImageType.THUMB)?.url,
        fallback = MaterialTheme.colorScheme.primaryContainer,
        source = resolvedColorsSource,
    )

    val heroSlot: @Composable () -> Unit = {
        ProvideClickActions(ClickContext.DETAIL) {
            ItemHeader(
                item = item,
                colors = colors,
                providerIconFetcher = providerIconFetcher,
                onPlayClick = onPlayItemClick,
            )
        }
    }

    TopBarLayout(
        topBar = {
            ItemTopBar(
                item = item,
                colors = colors,
                onBack = onBack,
                libraryActions = libraryActions,
                playlistActions = playlistActions.takeIf { item.supportsAddToPlaylist },
                navigateToItem = onNavigateClick,
                onSimilarArtistsClick = { showSimilarArtists = true },
            )
        },
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // selectedTab is null exactly while sub-lists load (for an item that has tabs), so it
            // doubles as the loading gate: show the hero + a single spinner, tabs hidden.
            val currentTab = state.selectedTab
            if (tabs.isEmpty()) {
                heroSlot()
            } else {
                val gridState = rememberLazyGridState()
                if (currentTab == null) {
                    Box(modifier = Modifier.weight(1f)) {
                        DetailGrid(
                            contentPadding = contentPadding,
                            heroSlot = heroSlot,
                            tabsSlot = null,
                            gridState = gridState,
                        ) {
                            fullSpanItem(DETAIL_LOADING_KEY) { InlineProgress() }
                        }
                    }
                } else {
                    val safeIndex = tabs.indexOf(currentTab).coerceAtLeast(0)
                    val tabsSlot: @Composable () -> Unit = {
                        TabsBar(
                            tabs = tabs,
                            selectedIndex = safeIndex,
                            controlTint = colors.controlTint,
                            onTabSelected = { onTabSelected(tabs[it]) },
                            albumsSortOption = state.albumsSortOption,
                            playableItemsSortOption = state.playableItemsSortOption,
                            onAlbumsSortChanged = onAlbumsSortChanged,
                            onPlayableItemsSortChanged = onPlayableItemsSortChanged,
                            viewModeProvider = viewModeProvider,
                            onToggleViewMode = onToggleViewMode,
                        )
                    }
                    val tabContext = currentTab.sortContext?.toClickContext()
                    Box(modifier = Modifier.weight(1f)) {
                        ProvideClickActions(tabContext) {
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
    }

    if (showSimilarArtists) {
        SimilarArtistsSheet(
            state = state.similarArtistsState,
            viewMode = viewModeProvider(MediaType.ARTIST),
            onNavigateClick = onNavigateClick,
            onPlayClick = onPlayChildClick,
            playlistActions = playlistActions,
            libraryActions = libraryActions,
            onDismiss = { showSimilarArtists = false },
        )
    }
}

@Composable
private fun TabsBar(
    tabs: List<ItemDetailsTab>,
    selectedIndex: Int,
    controlTint: Color,
    onTabSelected: (Int) -> Unit,
    albumsSortOption: SortOption?,
    playableItemsSortOption: SortOption?,
    onAlbumsSortChanged: (SubItemContext, SortOption) -> Unit,
    onPlayableItemsSortChanged: (SubItemContext, SortOption) -> Unit,
    viewModeProvider: @Composable (MediaType) -> ViewMode,
    onToggleViewMode: (MediaType) -> Unit,
) {
    val currentTab = tabs[selectedIndex]
    val sortCtx = currentTab.sortContext
    val currentSort: SortOption? = when (sortCtx) {
        SubItemContext.ARTIST_ALBUMS -> albumsSortOption
        SubItemContext.ARTIST_TRACKS,
        SubItemContext.ALBUM_TRACKS,
        SubItemContext.PLAYLIST_ITEMS,
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
            // Underline under the active tab only, tinted to the control accent (the default
            // PrimaryIndicator is colorScheme.primary). No full-width bottom divider.
            indicator = {
                TabRowDefaults.PrimaryIndicator(
                    modifier = Modifier.tabIndicatorOffset(selectedIndex),
                    color = controlTint,
                )
            },
            divider = {},
            modifier = Modifier.weight(1f),
        ) {
            tabs.forEachIndexed { i, tab ->
                val selected = i == selectedIndex
                Tab(
                    selected = selected,
                    onClick = { onTabSelected(i) },
                    text = {
                        Text(
                            text = tab.stringResource()?.let { stringResource(it) }.orEmpty(),
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                            color = controlTint,
                        )
                    },
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
    onPlayChildClick: PlayHandler<AppMediaItem>,
    onChapterClick: (Int) -> Unit,
    playlistActions: PlaylistActions,
    progressActions: ProgressActions?,
    onRemoveFromPlaylist: (String, Int) -> Unit,
    libraryActions: LibraryActions,
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
            playlistActions = playlistActions,
            libraryActions = libraryActions,
            providerIconFetcher = providerIconFetcher,
            contentPadding = contentPadding,
            heroSlot = heroSlot,
            tabsSlot = tabsSlot,
            gridState = gridState,
        )

        ItemDetailsTab.ARTIST_TRACKS,
        ItemDetailsTab.ALBUM_TRACKS,
        ItemDetailsTab.PLAYLIST_ITEMS,
        ItemDetailsTab.PODCAST_EPISODES,
            -> PlayablesTabContent(
            playableItemsState = state.playableItemsState,
            parentItem = item,
            playableItemsSortOption = state.playableItemsSortOption,
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

/**
 * Emits the shared full-span header rows: the hero (bled out of the grid's [gridPadding] so its
 * gradient reaches the real edges) and — unless [tabsSlot] is null (loading state) — the tabs bar.
 * [gridPadding] must be the same value passed to the grid's `contentPadding`, so the bleed exactly
 * cancels the inset.
 */
private fun LazyGridScope.detailHeaderItems(
    gridPadding: PaddingValues,
    heroSlot: @Composable () -> Unit,
    tabsSlot: (@Composable () -> Unit)?,
) {
    fullSpanItem(DETAIL_HERO_KEY) {
        Box(modifier = Modifier.fullBleed(gridPadding)) { heroSlot() }
    }
    tabsSlot?.let { slot ->
        fullSpanItem(DETAIL_TABS_KEY) { slot() }
    }
}

/**
 * The grid scaffold shared by every tab and by the loading state: identical columns/padding/spacing
 * and the [heroSlot] + optional [tabsSlot] header, then [body]. Centralizing it keeps the hero's
 * geometry identical across loading → loaded, so nothing shifts when the tabs appear.
 */
@Composable
private fun DetailGrid(
    contentPadding: PaddingValues,
    heroSlot: @Composable () -> Unit,
    tabsSlot: (@Composable () -> Unit)?,
    gridState: LazyGridState,
    body: LazyGridScope.() -> Unit,
) {
    val gridPadding = contentPadding + PaddingValues(4.dp)
    // Drop the grid's overscroll: the iOS Cupertino rubber-band drags the full-bleed header
    // gradient with it, making the background misbehave. Same fix as CollapsibleQueue.
    CompositionLocalProvider(LocalOverscrollFactory provides null) {
        LazyVerticalGrid(
            state = gridState,
            modifier = Modifier.fillMaxSize().testTag("LazyVerticalGrid"),
            columns = GridCells.Adaptive(minSize = gridItemMinSize()),
            contentPadding = gridPadding,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            detailHeaderItems(gridPadding, heroSlot, tabsSlot)
            body()
        }
    }
}

private fun LazyGridScope.fullSpanItem(
    key: String,
    content: @Composable () -> Unit,
) = item(
    key = key,
    span = { GridItemSpan(maxLineSpan) },
) { content() }

/**
 * Resolves a list tab's [state] to grid rows: Error → error message, empty (or NoData) → empty
 * message, otherwise delegates to [items]. Loading isn't handled here — it's gated out before the
 * tab is ever shown.
 */
private inline fun <T> LazyGridScope.tabListBody(
    state: DataState<List<T>>,
    crossinline items: LazyGridScope.(List<T>) -> Unit,
) {
    val data = when (state) {
        is DataState.Data -> state.data
        is DataState.Stale -> state.data
        is DataState.Error -> {
            fullSpanItem(DETAIL_ERROR_KEY) {
                CenteredText(
                    text = stringResource(Res.string.library_error),
                    color = MaterialTheme.colorScheme.error,
                )
            }
            return
        }

        else -> emptyList()
    }
    if (data.isEmpty()) {
        fullSpanItem(DETAIL_EMPTY_KEY) { CenteredText(stringResource(Res.string.library_empty)) }
    } else {
        items(data)
    }
}

@Composable
private fun AlbumsTabContent(
    albumsState: DataState<List<Album>>,
    viewModeProvider: @Composable (MediaType) -> ViewMode,
    onNavigateClick: (AppMediaItem) -> Unit,
    onPlayChildClick: PlayHandler<AppMediaItem>,
    playlistActions: PlaylistActions,
    libraryActions: LibraryActions,
    providerIconFetcher: @Composable (Modifier, String) -> Unit,
    contentPadding: PaddingValues,
    heroSlot: @Composable () -> Unit,
    tabsSlot: @Composable () -> Unit,
    gridState: LazyGridState,
) {
    val viewMode = viewModeProvider(MediaType.ALBUM)
    DetailGrid(contentPadding, heroSlot, tabsSlot, gridState) {
        tabListBody(albumsState) { albums ->
            // not a @Composable scope, so remember() is unavailable here
            val albumKeys = albums.lazyListOccurrenceKeys()
            itemsIndexed(
                items = albums,
                key = { index, _ -> albumKeys[index] },
                span = when (viewMode) {
                    ViewMode.LIST -> { _, _ -> GridItemSpan(maxLineSpan) }
                    ViewMode.GRID -> null
                },
            ) { _, album ->
                AlbumWithMenu(
                    item = album,
                    viewMode = viewMode,
                    onNavigateClick = onNavigateClick,
                    onPlayOption = onPlayChildClick,
                    playlistActions = playlistActions,
                    libraryActions = libraryActions,
                    providerIconFetcher = providerIconFetcher,
                )
            }
        }
    }
}

@Composable
private fun ArtistsTabContent(
    artistsState: DataState<List<Artist>>,
    viewModeProvider: @Composable (MediaType) -> ViewMode,
    onNavigateClick: (AppMediaItem) -> Unit,
    onPlayChildClick: PlayHandler<AppMediaItem>,
    libraryActions: LibraryActions,
    providerIconFetcher: @Composable (Modifier, String) -> Unit,
    contentPadding: PaddingValues,
    heroSlot: @Composable () -> Unit,
    tabsSlot: @Composable () -> Unit,
    gridState: LazyGridState,
) {
    val viewMode = viewModeProvider(MediaType.ARTIST)
    DetailGrid(contentPadding, heroSlot, tabsSlot, gridState) {
        tabListBody(artistsState) { artists ->
            val artistKeys = artists.lazyListOccurrenceKeys()
            itemsIndexed(
                items = artists,
                key = { index, _ -> artistKeys[index] },
                span = when (viewMode) {
                    ViewMode.LIST -> { _, _ -> GridItemSpan(maxLineSpan) }
                    ViewMode.GRID -> null
                },
            ) { _, artist ->
                ArtistWithMenu(
                    item = artist,
                    viewMode = viewMode,
                    onNavigateClick = onNavigateClick,
                    onPlayOption = onPlayChildClick,
                    libraryActions = libraryActions,
                    providerIconFetcher = providerIconFetcher,
                )
            }
        }
    }
}

@Composable
private fun PlayablesTabContent(
    playableItemsState: DataState<List<PlayableItem>>,
    parentItem: AppMediaItem,
    playableItemsSortOption: SortOption?,
    viewModeProvider: @Composable (MediaType) -> ViewMode,
    onPlayChildClick: PlayHandler<AppMediaItem>,
    playlistActions: PlaylistActions,
    progressActions: ProgressActions?,
    onRemoveFromPlaylist: (String, Int) -> Unit,
    libraryActions: LibraryActions,
    providerIconFetcher: @Composable (Modifier, String) -> Unit,
    contentPadding: PaddingValues,
    heroSlot: @Composable () -> Unit,
    tabsSlot: @Composable () -> Unit,
    gridState: LazyGridState,
) {
    val viewMode = viewModeProvider(MediaType.TRACK)
    // Shared row body for both the flat and the disc-sectioned layouts.
    val trackItem: @Composable (index: Int, track: PlayableItem) -> Unit = { index, track ->
        when (track) {
            is Track -> TrackWithMenu(
                item = track,
                viewMode = viewMode,
                showTrackNumber = parentItem is Album,
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
    val listSpan: (LazyGridItemSpanScope.() -> GridItemSpan)? =
        if (viewMode == ViewMode.LIST) ({ GridItemSpan(maxLineSpan) }) else null
    DetailGrid(contentPadding, heroSlot, tabsSlot, gridState) {
        tabListBody(playableItemsState) { tracks ->
            val trackKeys = tracks.playableLazyListOccurrenceKeys()
            // Section a multi-disc album by disc, but only in its natural ("Original") order —
            // any other sort intentionally mixes discs, so headers would lie. Requiring every
            // track to carry a disc number avoids a bogus "Disc null" header on partial tags.
            val sectionByDisc = parentItem is Album &&
                playableItemsSortOption?.field == SortField.ORIGINAL &&
                tracks.all { it is Track && it.discNumber != null } &&
                tracks.mapNotNull { (it as? Track)?.discNumber }.distinct().size > 1
            if (sectionByDisc) {
                tracks.forEachIndexed { index, track ->
                    // Gate guarantees a non-null disc; Original sort keeps discs contiguous.
                    val disc = (track as? Track)?.discNumber ?: return@forEachIndexed
                    val prevDisc = (tracks.getOrNull(index - 1) as? Track)?.discNumber
                    if (index == 0 || disc != prevDisc) {
                        fullSpanItem("disc-header-$disc") { DiscHeader(disc) }
                    }
                    item(key = trackKeys[index], span = listSpan) { trackItem(index, track) }
                }
            } else {
                itemsIndexed(
                    items = tracks,
                    key = { index, _ -> trackKeys[index] },
                    span = when (viewMode) {
                        ViewMode.LIST -> { _, _ -> GridItemSpan(maxLineSpan) }
                        ViewMode.GRID -> null
                    },
                ) { index, track -> trackItem(index, track) }
            }
        }
    }
}

@Composable
private fun DiscHeader(disc: Int) {
    Text(
        text = stringResource(Res.string.album_disc_header, disc),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp),
    )
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
    DetailGrid(contentPadding, heroSlot, tabsSlot, gridState) {
        if (chapters.isEmpty()) {
            fullSpanItem(DETAIL_EMPTY_KEY) { CenteredText(stringResource(Res.string.library_empty)) }
        } else {
            chapters.forEach { chapter ->
                fullSpanItem(DETAIL_CHAPTER_KEY_PREFIX + chapter.position) {
                    ChapterRow(
                        chapter = chapter,
                        onClick = { onChapterClick(chapter.position) },
                    )
                }
            }
        }
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

// Item loaded, sub-lists still loading: hero visible, tabs hidden, single spinner.
@Preview
@Composable
private fun PreviewArtistTabsLoading() {
    val artist = AppMediaItemFixtures.artist("Artist")
    AppTheme(darkTheme = false) {
        Scaffold {
            ItemDetails(
                state = ItemDetailsViewModel.State(
                    DataState.Data(artist),
                    DataState.Loading(),
                    DataState.Loading(),
                ),
                geEditablePlaylists = suspend { emptyList() },
            )
        }
    }
}

// No albums but has tracks → auto-selects the Tracks tab (Albums tab would show the empty state).
@Preview
@Composable
private fun PreviewArtistTracksOnly() {
    val artist = AppMediaItemFixtures.artist("Artist")
    AppTheme(darkTheme = false) {
        Scaffold {
            ItemDetails(
                state = ItemDetailsViewModel.State(
                    DataState.Data(artist),
                    DataState.Data(emptyList()),
                    DataState.Data(AppMediaItemFixtures.tracks(listOf("Track 1", "Track 2"))),
                ),
                geEditablePlaylists = suspend { emptyList() },
            )
        }
    }
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

private const val DETAIL_HERO_KEY = "detail:hero"
private const val DETAIL_TABS_KEY = "detail:tabs"
private const val DETAIL_ERROR_KEY = "detail:error"
private const val DETAIL_EMPTY_KEY = "detail:empty"
private const val DETAIL_LOADING_KEY = "detail:loading"
private const val DETAIL_CHAPTER_KEY_PREFIX = "detail:chapter:"
