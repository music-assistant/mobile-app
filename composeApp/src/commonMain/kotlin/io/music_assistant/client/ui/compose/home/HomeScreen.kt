@file:OptIn(ExperimentalMaterial3Api::class)
@file:Suppress("MagicNumber")

package io.music_assistant.client.ui.compose.home

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarState
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import compose.icons.TablerIcons
import compose.icons.tablericons.GripVertical
import io.music_assistant.client.data.model.client.ClickContext
import io.music_assistant.client.data.model.client.MediaType
import io.music_assistant.client.data.model.client.items.Album
import io.music_assistant.client.data.model.client.items.AppMediaItem
import io.music_assistant.client.data.model.client.items.Artist
import io.music_assistant.client.data.model.client.items.Audiobook
import io.music_assistant.client.data.model.client.items.Genre
import io.music_assistant.client.data.model.client.items.Playlist
import io.music_assistant.client.data.model.client.items.Podcast
import io.music_assistant.client.data.model.client.items.PodcastEpisode
import io.music_assistant.client.data.model.client.items.RadioStation
import io.music_assistant.client.data.model.client.items.RecommendationFolder
import io.music_assistant.client.data.model.client.items.Track
import io.music_assistant.client.settings.SettingsRepository
import io.music_assistant.client.ui.compose.common.DataState
import io.music_assistant.client.ui.compose.common.items.AlbumWithMenu
import io.music_assistant.client.ui.compose.common.items.ArtistWithMenu
import io.music_assistant.client.ui.compose.common.items.AudiobookWithMenu
import io.music_assistant.client.ui.compose.common.items.GenreWithMenu
import io.music_assistant.client.ui.compose.common.items.LibraryActions
import io.music_assistant.client.ui.compose.common.items.PlayHandler
import io.music_assistant.client.ui.compose.common.items.PlaylistActions
import io.music_assistant.client.ui.compose.common.items.PlaylistWithMenu
import io.music_assistant.client.ui.compose.common.items.PodcastEpisodeWithMenu
import io.music_assistant.client.ui.compose.common.items.PodcastWithMenu
import io.music_assistant.client.ui.compose.common.items.ProgressActions
import io.music_assistant.client.ui.compose.common.items.ProvideClickActions
import io.music_assistant.client.ui.compose.common.items.RadioWithMenu
import io.music_assistant.client.ui.compose.common.items.TrackWithMenu
import io.music_assistant.client.ui.compose.common.items.lazyListKey
import io.music_assistant.client.ui.compose.common.moveToEnabledBoundary
import io.music_assistant.client.ui.compose.common.viewmodel.ActionsViewModel
import io.music_assistant.client.ui.compose.nav.BackHandler
import io.music_assistant.client.ui.compose.nav.ScreenState
import io.music_assistant.client.ui.compose.nav.TopBarLayout
import io.music_assistant.client.utils.SessionState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import musicassistantclient.composeapp.generated.resources.Res
import musicassistantclient.composeapp.generated.resources.all_albums
import musicassistantclient.composeapp.generated.resources.all_artists
import musicassistantclient.composeapp.generated.resources.all_audiobooks
import musicassistantclient.composeapp.generated.resources.all_genres
import musicassistantclient.composeapp.generated.resources.all_playlists
import musicassistantclient.composeapp.generated.resources.all_podcasts
import musicassistantclient.composeapp.generated.resources.all_radio
import musicassistantclient.composeapp.generated.resources.all_tracks
import musicassistantclient.composeapp.generated.resources.home_edit_rows
import musicassistantclient.composeapp.generated.resources.home_save_rows
import musicassistantclient.composeapp.generated.resources.nav_home
import musicassistantclient.composeapp.generated.resources.refresh
import org.jetbrains.compose.resources.stringResource
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

@Composable
fun HomeScreen(
    homeScreenViewModel: HomeScreenViewModel,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues,
    connectionState: SessionState,
    dataState: DataState<List<RecommendationFolder>>,
    onNavigateClick: (AppMediaItem) -> Unit,
    onLibraryItemClick: (MediaType) -> Unit,
    providerIconFetcher: (@Composable (Modifier, String) -> Unit),
    homeRowsConfig: List<SettingsRepository.HomeRowPref>,
    actionsViewModel: ActionsViewModel,
    state: HomeScreenState,
) {
    var editMode by remember { mutableStateOf(false) }

    val baseList = remember(dataState) {
        if (dataState is DataState.Data) {
            dataState.data
                .filter {
                    it.items?.any { item ->
                        item is Track ||
                                item is Artist ||
                                item is Album ||
                                item is Playlist ||
                                item is Audiobook ||
                                item is Podcast ||
                                item is PodcastEpisode ||
                                item is RadioStation ||
                                item is Genre
                    } == true
                }
                .distinctBy { it.lazyListKey() }
        } else {
            emptyList()
        }
    }
    // Reconciled, enabled-first ordering. Authoritative for normal-mode display.
    val working = remember(baseList, homeRowsConfig) { reconcileHomeRows(baseList, homeRowsConfig) }

    // Edit-mode working copy — isolated from external (real-time) updates while editing;
    // snapshotted fresh on entering edit mode.
    var items by remember { mutableStateOf(working) }
    val enabledCount = items.count { it.second }
    val enabledByKey = remember(items) { items.associate { it.first.lazyListKey() to it.second } }

    val displayedData =
        if (editMode) items.map { it.first } else working.filter { it.second }.map { it.first }

    val reorderableState = rememberReorderableLazyListState(state.lazyListState) { from, to ->
        // Constrain reorder to the contiguous enabled section.
        if (from.index >= enabledCount || to.index >= enabledCount) return@rememberReorderableLazyListState
        items = items.toMutableList().apply { add(to.index, removeAt(from.index)) }
    }

    BackHandler(enabled = editMode) { editMode = false }

    TopBarLayout(
        topBar = {
            LandingPageTopBar(
                editMode = editMode,
                onRefresh = { homeScreenViewModel.loadRecommendations() },
                onToggleEditMode = {
                    if (editMode) {
                        homeScreenViewModel.saveHomeRows(
                            items.map {
                                SettingsRepository.HomeRowPref(
                                    it.first.itemId,
                                    it.second,
                                )
                            },
                        )
                        editMode = false
                    } else {
                        items = working
                        editMode = true
                    }
                },
            )
        },
        topAppBarState = state.topAppBarState,
    ) {
        val rowContent: @Composable (RecommendationFolder) -> Unit = { row ->
            CategoryRow(
                title = row.displayName,
                rowItemType = row.rowItemType,
                onNavigateClick = onNavigateClick,
                onPlayClick = { item, option, radio, _ ->
                    homeScreenViewModel.onPlayClick(item, option, radio)
                },
                onAllClick = { row.rowItemType?.let { onLibraryItemClick(it) } },
                mediaItems = row.items.orEmpty(),
                playlistActions = actionsViewModel,
                libraryActions = actionsViewModel,
                progressActions = actionsViewModel,
                providerIconFetcher = providerIconFetcher,
            )
        }
        ProvideClickActions(ClickContext.HOME) {
            LazyColumn(
                state = state.lazyListState,
                contentPadding = contentPadding,
            ) {
                if (connectionState !is SessionState.Connected || dataState !is DataState.Data) {
                    item {
                        Box(
                            modifier = modifier.fillMaxWidth().height(200.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator()
                        }
                    }
                } else {
                    items(
                        items = displayedData,
                        key = { it.lazyListKey() },
                    ) { row ->
                        if (editMode) {
                            val enabled = enabledByKey[row.lazyListKey()] ?: true
                            ReorderableItem(reorderableState, key = row.lazyListKey()) {
                                Box(modifier = Modifier.fillMaxWidth()) {
                                    rowContent(row)
                                    Box(
                                        modifier = Modifier
                                            .matchParentSize()
                                            .background(
                                                MaterialTheme.colorScheme.background.copy(
                                                    alpha = if (enabled) 0.60f else 0.80f,
                                                ),
                                            )
                                            .pointerInput(Unit) {
                                                // Swallow taps & long-presses on the row;
                                                // vertical drags pass through so the
                                                // LazyColumn can still scroll.
                                                detectTapGestures(onTap = {}, onLongPress = {})
                                            },
                                    )
                                    Row(
                                        modifier = Modifier
                                            .align(Alignment.CenterEnd)
                                            .padding(end = 16.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        // Keep at least one row visible: block disabling the last enabled one.
                                        Switch(
                                            checked = enabled,
                                            enabled = !enabled || enabledCount > 1,
                                            onCheckedChange = { newEnabled ->
                                                items =
                                                    moveToEnabledBoundary(items, row, newEnabled)
                                            },
                                        )
                                        Icon(
                                            modifier = Modifier
                                                .padding(start = 12.dp)
                                                .then(
                                                    if (enabled) Modifier.draggableHandle() else Modifier,
                                                )
                                                .alpha(if (enabled) 1f else 0.3f)
                                                .size(24.dp),
                                            imageVector = TablerIcons.GripVertical,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.secondary,
                                        )
                                    }
                                }
                            }
                        } else {
                            rowContent(row)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LandingPageTopBar(
    editMode: Boolean,
    onRefresh: () -> Unit,
    onToggleEditMode: () -> Unit,
) {
    TopAppBar(
        title = { Text(stringResource(Res.string.nav_home)) },
        actions = {
            IconButton(onClick = onToggleEditMode) {
                Icon(
                    imageVector = if (editMode) Icons.Default.Done else Icons.Default.Edit,
                    contentDescription = stringResource(
                        if (editMode) Res.string.home_save_rows else Res.string.home_edit_rows,
                    ),
                )
            }

            if (!editMode) {
                IconButton(onClick = onRefresh) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = stringResource(Res.string.refresh),
                    )
                }
            }
        },
    )
}

// --- Common UI Components ---

@Composable
fun CategoryRow(
    title: String,
    rowItemType: MediaType?,
    onNavigateClick: (AppMediaItem) -> Unit,
    onPlayClick: PlayHandler<AppMediaItem>,
    onAllClick: () -> Unit,
    mediaItems: List<AppMediaItem>,
    playlistActions: PlaylistActions,
    libraryActions: LibraryActions,
    progressActions: ProgressActions? = null,
    providerIconFetcher: (@Composable (Modifier, String) -> Unit),
) {
    val rowListState = rememberLazyListState()

    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
            )
            rowItemType?.let { type ->
                val allTitle = allItemsTitle(type)
                allTitle?.let {
                    TextButton(
                        onClick = onAllClick,
                        contentPadding = PaddingValues(start = 4.dp, end = 4.dp),
                    ) {
                        Text(allTitle, style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
        LazyRow(
            state = rowListState,
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            items(
                items = mediaItems,
                key = { it.lazyListKey() },
                contentType = { item ->
                    when (item) {
                        is Track -> "Track"
                        is Artist -> "Artist"
                        is Album -> "Album"
                        is Playlist -> "Playlist"
                        is Audiobook -> "Audiobook"
                        is Podcast -> "Podcast"
                        is PodcastEpisode -> "Episode"
                        is RadioStation -> "RadioStation"
                        is Genre -> "Genre"
                        else -> "Unknown"
                    }
                },
            ) { item ->
                when (item) {
                    is Artist -> ArtistWithMenu(
                        item = item,
                        onNavigateClick = onNavigateClick,
                        onPlayOption = onPlayClick,
                        libraryActions = libraryActions,
                        providerIconFetcher = providerIconFetcher,
                    )

                    is Album -> AlbumWithMenu(
                        item = item,
                        onNavigateClick = onNavigateClick,
                        onPlayOption = onPlayClick,
                        playlistActions = playlistActions,
                        libraryActions = libraryActions,
                        providerIconFetcher = providerIconFetcher,
                    )

                    is Playlist -> PlaylistWithMenu(
                        item = item,
                        onNavigateClick = onNavigateClick,
                        onPlayOption = onPlayClick,
                        libraryActions = libraryActions,
                        providerIconFetcher = providerIconFetcher,
                    )

                    is Podcast -> PodcastWithMenu(
                        item = item,
                        onNavigateClick = onNavigateClick,
                        onPlayOption = onPlayClick,
                        libraryActions = libraryActions,
                        providerIconFetcher = providerIconFetcher,
                    )

                    is Track -> TrackWithMenu(
                        item = item,
                        onPlayOption = onPlayClick,
                        playlistActions = playlistActions,
                        libraryActions = libraryActions,
                        providerIconFetcher = providerIconFetcher,
                    )

                    is PodcastEpisode -> PodcastEpisodeWithMenu(
                        item = item,
                        onPlayOption = onPlayClick,
                        playlistActions = playlistActions,
                        libraryActions = libraryActions,
                        progressActions = progressActions,
                        providerIconFetcher = providerIconFetcher,
                    )

                    is Audiobook -> AudiobookWithMenu(
                        item = item,
                        onNavigateClick = onNavigateClick,
                        onPlayOption = onPlayClick,
                        playlistActions = playlistActions,
                        libraryActions = libraryActions,
                        progressActions = progressActions,
                        providerIconFetcher = providerIconFetcher,
                    )

                    is RadioStation -> RadioWithMenu(
                        item = item,
                        onPlayOption = onPlayClick,
                        playlistActions = playlistActions,
                        libraryActions = libraryActions,
                        providerIconFetcher = providerIconFetcher,
                    )

                    is Genre -> GenreWithMenu(
                        item = item,
                        onNavigateClick = onNavigateClick,
                        onPlayOption = onPlayClick,
                        libraryActions = libraryActions,
                        providerIconFetcher = providerIconFetcher,
                    )

                    else -> {}
                }
            }
        }
    }
}

@Composable
fun allItemsTitle(type: MediaType) = when (type) {
    MediaType.TRACK -> stringResource(Res.string.all_tracks)
    MediaType.ALBUM -> stringResource(Res.string.all_albums)
    MediaType.ARTIST -> stringResource(Res.string.all_artists)
    MediaType.PLAYLIST -> stringResource(Res.string.all_playlists)
    MediaType.AUDIOBOOK -> stringResource(Res.string.all_audiobooks)
    MediaType.PODCAST -> stringResource(Res.string.all_podcasts)
    MediaType.RADIO -> stringResource(Res.string.all_radio)
    MediaType.GENRE -> stringResource(Res.string.all_genres)
    else -> null
}

class HomeScreenState(
    val topAppBarState: TopAppBarState,
    val lazyListState: LazyListState,
    val coroutineScope: CoroutineScope,
) : ScreenState {
    override fun reset() {
        coroutineScope.launch {
            topAppBarState.heightOffset = 0f
            lazyListState.animateScrollToItem(0)
        }
    }

    companion object {
        @Composable
        fun create(): HomeScreenState {
            return HomeScreenState(
                rememberTopAppBarState(),
                rememberLazyListState(),
                rememberCoroutineScope(),
            )
        }
    }
}
