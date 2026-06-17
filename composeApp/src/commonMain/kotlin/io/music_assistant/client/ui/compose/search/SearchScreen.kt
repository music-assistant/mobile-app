@file:OptIn(ExperimentalMaterial3Api::class)

package io.music_assistant.client.ui.compose.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarState
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.music_assistant.client.data.model.client.ClickContext
import io.music_assistant.client.data.model.client.MediaType
import io.music_assistant.client.data.model.client.items.Album
import io.music_assistant.client.data.model.client.items.AppMediaItem
import io.music_assistant.client.data.model.client.items.Artist
import io.music_assistant.client.data.model.client.items.Audiobook
import io.music_assistant.client.data.model.client.items.Genre
import io.music_assistant.client.data.model.client.items.Playlist
import io.music_assistant.client.data.model.client.items.Podcast
import io.music_assistant.client.data.model.client.items.RadioStation
import io.music_assistant.client.data.model.client.items.Track
import io.music_assistant.client.data.model.client.stringResource
import io.music_assistant.client.settings.ViewMode
import io.music_assistant.client.ui.compose.common.DataState
import io.music_assistant.client.ui.compose.common.ToastHost
import io.music_assistant.client.ui.compose.common.ToastState
import io.music_assistant.client.ui.compose.common.clearFocusOnScroll
import io.music_assistant.client.ui.compose.common.items.AlbumWithMenu
import io.music_assistant.client.ui.compose.common.items.ArtistWithMenu
import io.music_assistant.client.ui.compose.common.items.AudiobookWithMenu
import io.music_assistant.client.ui.compose.common.items.GenreWithMenu
import io.music_assistant.client.ui.compose.common.items.LibraryActions
import io.music_assistant.client.ui.compose.common.items.PlayHandler
import io.music_assistant.client.ui.compose.common.items.PlaylistActions
import io.music_assistant.client.ui.compose.common.items.PlaylistWithMenu
import io.music_assistant.client.ui.compose.common.items.PodcastWithMenu
import io.music_assistant.client.ui.compose.common.items.ProgressActions
import io.music_assistant.client.ui.compose.common.items.ProvideClickActions
import io.music_assistant.client.ui.compose.common.items.RadioWithMenu
import io.music_assistant.client.ui.compose.common.items.TrackWithMenu
import io.music_assistant.client.ui.compose.common.items.lazyListKey
import io.music_assistant.client.ui.compose.common.providers.ProviderIcon
import io.music_assistant.client.ui.compose.common.rememberToastState
import io.music_assistant.client.ui.compose.common.viewmodel.ActionsViewModel
import io.music_assistant.client.ui.compose.home.CategoryRow
import io.music_assistant.client.ui.compose.nav.ScreenState
import io.music_assistant.client.ui.compose.nav.TopBarLayout
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import musicassistantclient.composeapp.generated.resources.Res
import musicassistantclient.composeapp.generated.resources.search_error
import musicassistantclient.composeapp.generated.resources.search_in_library_only
import musicassistantclient.composeapp.generated.resources.search_no_results
import musicassistantclient.composeapp.generated.resources.search_start
import musicassistantclient.composeapp.generated.resources.search_title
import org.jetbrains.compose.resources.stringResource

@Composable
fun SearchScreen(
    searchViewModel: SearchViewModel,
    onNavigateToItem: (String, MediaType, String) -> Unit,
    actionsViewModel: ActionsViewModel,
    contentPadding: PaddingValues,
    state: SearchScreenState,
    pendingSearch: GlobalSearchRequest? = null,
    onSearchConsumed: () -> Unit = {},
) {
    val searchState by searchViewModel.state.collectAsStateWithLifecycle()
    val toastState = rememberToastState()

    LaunchedEffect(Unit) {
        actionsViewModel.toasts.collect { toast ->
            toastState.showToast(toast)
        }
    }

    // Escalation from an empty in-library quick search (state hoisted in MainNavigationRoot,
    // which outlives the per-NavEntry SearchViewModel). Apply once, then clear.
    LaunchedEffect(pendingSearch) {
        pendingSearch?.let {
            searchViewModel.applyGlobalSearch(it)
            onSearchConsumed()
        }
    }

    TopBarLayout(
        topBar = {
            SearchTopBar(
                searchState.searchState,
                onQueryChanged = searchViewModel::onQueryChanged,
                onSearchTriggered = searchViewModel::onSearchTriggered,
                onMediaTypeToggled = searchViewModel::onMediaTypeToggled,
                onLibraryOnlyToggled = searchViewModel::onLibraryOnlyToggled,
            )
        },
        topAppBarState = state.topAppBarState,
    ) {
        ProvideClickActions(ClickContext.SEARCH) {
        SearchContent(
            state = searchState,
            toastState = toastState,
            onItemClick = { item ->
                when (item) {
                    is Artist,
                    is Album,
                    is Playlist,
                    is Podcast,
                    is Audiobook,
                        -> {
                        onNavigateToItem(item.itemId, item.mediaType, item.provider)
                    }

                    else -> Unit
                }
            },
            onPlayClick = { track, option, radio, _ ->
                searchViewModel.onPlayClick(track, option, radio)
            },
            playlistActions = actionsViewModel,
            libraryActions = actionsViewModel,
            progressActions = actionsViewModel,
            providerIconFetcher = { modifier, provider ->
                actionsViewModel.getProviderIcon(provider)
                    ?.let { ProviderIcon(modifier, it) }
            },
            contentPadding = contentPadding,
            lazyListState = state.lazyListState,
        )
        }
    }
}

@Composable
private fun SearchTopBar(
    searchState: SearchViewModel.SearchState,
    onQueryChanged: (String) -> Unit,
    onSearchTriggered: () -> Unit,
    onMediaTypeToggled: (MediaType, Boolean) -> Unit,
    onLibraryOnlyToggled: (Boolean) -> Unit,
) {
    TopAppBar(
        title = {
            Column(modifier = Modifier.padding(vertical = 4.dp)) {
                Text(
                    modifier = Modifier
                        .height(56.dp)
                        .fillMaxWidth()
                        .wrapContentHeight(Alignment.CenterVertically),
                    text = stringResource(Res.string.search_title),
                )
                val modifier = Modifier.padding(end = 16.dp)
                SearchInput(
                    modifier = modifier,
                    query = searchState.query,
                    onQueryChanged = onQueryChanged,
                    onSearchTriggered = onSearchTriggered,
                )

                // Search filters (always visible)
                SearchFilters(
                    modifier = modifier,
                    searchState = searchState,
                    onMediaTypeToggled = onMediaTypeToggled,
                    onLibraryOnlyToggled = onLibraryOnlyToggled,
                )
            }
        },
    )
}

@Composable
private fun SearchContent(
    state: SearchViewModel.State,
    toastState: ToastState,
    onItemClick: (AppMediaItem) -> Unit,
    onPlayClick: PlayHandler<AppMediaItem>,
    playlistActions: PlaylistActions,
    libraryActions: LibraryActions,
    progressActions: ProgressActions? = null,
    providerIconFetcher: (@Composable (Modifier, String) -> Unit),
    contentPadding: PaddingValues,
    lazyListState: LazyListState,
) {
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            // Results
            when (val resultsState = state.resultsState) {
                is DataState.Loading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator()
                    }
                }

                is DataState.Error -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = stringResource(Res.string.search_error),
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }

                is DataState.Stale,
                is DataState.Data,
                    -> {
                    // Handle both Data and Stale - both contain valid search results
                    val results = when (resultsState) {
                        is DataState.Stale -> resultsState.data
                        is DataState.Data -> resultsState.data
                    }
                    when (results.nonEmptyLists.size) {
                        0 -> Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(stringResource(Res.string.search_no_results))
                        }

                        1 -> LazyColumn(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp)
                                .clearFocusOnScroll(),
                            state = lazyListState,
                            contentPadding = contentPadding,
                        ) {
                            val (title, items) = results.nonEmptyLists.first()
                            item {
                                Text(
                                    text = stringResource(title),
                                    style = MaterialTheme.typography.titleLarge,
                                )
                            }
                            item {
                                Spacer(modifier = Modifier.height(16.dp))
                            }
                            items(
                                items = items,
                                key = { it.lazyListKey() },
                            ) { item ->
                                when (item) {
                                    is Track -> TrackWithMenu(
                                        viewMode = ViewMode.LIST,
                                        item = item,
                                        onPlayOption = onPlayClick,
                                        libraryActions = libraryActions,
                                        providerIconFetcher = providerIconFetcher,
                                    )

                                    is Artist -> ArtistWithMenu(
                                        viewMode = ViewMode.LIST,
                                        item = item,
                                        onNavigateClick = onItemClick,
                                        onPlayOption = onPlayClick,
                                        libraryActions = libraryActions,
                                        providerIconFetcher = providerIconFetcher,
                                    )

                                    is Album -> AlbumWithMenu(
                                        viewMode = ViewMode.LIST,
                                        item = item,
                                        onNavigateClick = onItemClick,
                                        onPlayOption = onPlayClick,
                                        playlistActions = playlistActions,
                                        libraryActions = libraryActions,
                                        providerIconFetcher = providerIconFetcher,
                                    )

                                    is Playlist -> PlaylistWithMenu(
                                        viewMode = ViewMode.LIST,
                                        item = item,
                                        onNavigateClick = onItemClick,
                                        onPlayOption = onPlayClick,
                                        libraryActions = libraryActions,
                                        providerIconFetcher = providerIconFetcher,
                                    )

                                    is Podcast -> PodcastWithMenu(
                                        viewMode = ViewMode.LIST,
                                        item = item,
                                        onNavigateClick = onItemClick,
                                        onPlayOption = onPlayClick,
                                        libraryActions = libraryActions,
                                        providerIconFetcher = providerIconFetcher,
                                    )

                                    is Audiobook -> AudiobookWithMenu(
                                        viewMode = ViewMode.LIST,
                                        item = item,
                                        onNavigateClick = onItemClick,
                                        onPlayOption = onPlayClick,
                                        playlistActions = playlistActions,
                                        libraryActions = libraryActions,
                                        providerIconFetcher = providerIconFetcher,
                                    )

                                    is RadioStation -> RadioWithMenu(
                                        viewMode = ViewMode.LIST,
                                        item = item,
                                        onPlayOption = onPlayClick,
                                        libraryActions = libraryActions,
                                        providerIconFetcher = providerIconFetcher,
                                    )

                                    is Genre -> GenreWithMenu(
                                        viewMode = ViewMode.LIST,
                                        item = item,
                                        onNavigateClick = onItemClick,
                                        onPlayOption = onPlayClick,
                                        libraryActions = libraryActions,
                                        providerIconFetcher = providerIconFetcher,
                                    )

                                    else -> Unit
                                }
                            }
                        }

                        else -> {
                            val preparedItems = results.nonEmptyLists
                                .map { (title, items) -> Pair(stringResource(title), items) }
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clearFocusOnScroll(),
                                state = lazyListState,
                                contentPadding = contentPadding,
                            ) {
                                preparedItems.forEach { (stringTitle, items) ->
                                    if (items.isNotEmpty()) {
                                        item(key = stringTitle, contentType = "category") {
                                            CategoryRow(
                                                title = stringTitle,
                                                rowItemType = null,
                                                onNavigateClick = onItemClick,
                                                onPlayClick = onPlayClick,
                                                onAllClick = {},
                                                mediaItems = items,
                                                playlistActions = playlistActions,
                                                libraryActions = libraryActions,
                                                progressActions = progressActions,
                                                providerIconFetcher = providerIconFetcher,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                is DataState.NoData -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(stringResource(Res.string.search_start))
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
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SearchFilters(
    modifier: Modifier = Modifier,
    searchState: SearchViewModel.SearchState,
    onMediaTypeToggled: (MediaType, Boolean) -> Unit,
    onLibraryOnlyToggled: (Boolean) -> Unit,
) {
    CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides Dp.Unspecified) {
        FlowRow(
            modifier = modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            // Media type filter chips
            searchState.mediaTypes.forEach { mediaTypeSelect ->
                FilterChip(
                    selected = mediaTypeSelect.isSelected,
                    onClick = {
                        onMediaTypeToggled(
                            mediaTypeSelect.type,
                            !mediaTypeSelect.isSelected,
                        )
                    },
                    label = {
                        Text(
                            text = stringResource(mediaTypeSelect.type.stringResource()),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    },
                )
            }

            // In library only filter chip
            FilterChip(
                selected = searchState.libraryOnly,
                onClick = { onLibraryOnlyToggled(!searchState.libraryOnly) },
                label = {
                    Text(
                        text = stringResource(Res.string.search_in_library_only),
                        style = MaterialTheme.typography.bodySmall,
                    )
                },
            )
        }
    }
}

class SearchScreenState(
    val topAppBarState: TopAppBarState,
    val lazyListState: LazyListState,
    val coroutineScope: CoroutineScope,
) : ScreenState {
    override fun reset() {
        topAppBarState.heightOffset = 0f
        coroutineScope.launch {
            lazyListState.animateScrollToItem(0)
        }
    }

    companion object {
        @Composable
        fun create(): SearchScreenState {
            return SearchScreenState(
                rememberTopAppBarState(),
                rememberLazyListState(),
                rememberCoroutineScope(),
            )
        }
    }
}
