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
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.capitalize
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.music_assistant.client.data.model.client.AppMediaItem
import io.music_assistant.client.data.model.server.MediaType
import io.music_assistant.client.data.model.server.QueueOption
import io.music_assistant.client.settings.ViewMode
import io.music_assistant.client.ui.compose.common.DataState
import io.music_assistant.client.ui.compose.common.ToastHost
import io.music_assistant.client.ui.compose.common.ToastState
import io.music_assistant.client.ui.compose.common.clearFocusOnScroll
import io.music_assistant.client.ui.compose.common.items.AlbumWithMenu
import io.music_assistant.client.ui.compose.common.items.ArtistWithMenu
import io.music_assistant.client.ui.compose.common.items.AudiobookWithMenu
import io.music_assistant.client.ui.compose.common.items.GenreWithMenu
import io.music_assistant.client.ui.compose.common.items.PlaylistWithMenu
import io.music_assistant.client.ui.compose.common.items.PodcastWithMenu
import io.music_assistant.client.ui.compose.common.items.RadioWithMenu
import io.music_assistant.client.ui.compose.common.items.TrackWithMenu
import io.music_assistant.client.ui.compose.common.providers.ProviderIcon
import io.music_assistant.client.ui.compose.common.rememberToastState
import io.music_assistant.client.ui.compose.common.viewmodel.ActionsViewModel
import io.music_assistant.client.ui.compose.home.CategoryRow
import io.music_assistant.client.ui.compose.nav.Screen
import musicassistantclient.composeapp.generated.resources.Res
import musicassistantclient.composeapp.generated.resources.search_error
import musicassistantclient.composeapp.generated.resources.search_in_library_only
import musicassistantclient.composeapp.generated.resources.search_no_results
import musicassistantclient.composeapp.generated.resources.search_start
import musicassistantclient.composeapp.generated.resources.search_title
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun SearchScreen(
    onNavigateToItem: (String, MediaType, String) -> Unit,
    viewModel: SearchViewModel = koinViewModel(),
    actionsViewModel: ActionsViewModel = koinViewModel(),
    contentPadding: PaddingValues,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val serverUrl by viewModel.serverUrl.collectAsStateWithLifecycle(null)
    val toastState = rememberToastState()

    LaunchedEffect(Unit) {
        actionsViewModel.toasts.collect { toast ->
            toastState.showToast(toast)
        }
    }

    Screen(
        topBar = { scrollBehaviour ->
            LaunchedEffect(state.searchState.query) {
                scrollBehaviour.state.heightOffset = 0f
            }

            SearchTopBar(
                state.searchState,
                scrollBehavior = scrollBehaviour,
                onQueryChanged = viewModel::onQueryChanged,
                onMediaTypeToggled = viewModel::onMediaTypeToggled,
                onLibraryOnlyToggled = viewModel::onLibraryOnlyToggled,
            )
        },
    ) {
        SearchContent(
            state = state,
            serverUrl = serverUrl,
            toastState = toastState,
            onItemClick = { item ->
                when (item) {
                    is AppMediaItem.Artist,
                    is AppMediaItem.Album,
                    is AppMediaItem.Playlist,
                    is AppMediaItem.Podcast,
                    is AppMediaItem.Audiobook,
                        -> {
                        onNavigateToItem(item.itemId, item.mediaType, item.provider)
                    }

                    else -> Unit
                }
            },
            onPlayClick = viewModel::onPlayClick,
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
            providerIconFetcher = { modifier, provider ->
                actionsViewModel.getProviderIcon(provider)
                    ?.let { ProviderIcon(modifier, it) }
            },
            contentPadding = contentPadding,
        )
    }
}

@Composable
private fun SearchTopBar(
    searchState: SearchViewModel.SearchState,
    scrollBehavior: TopAppBarScrollBehavior? = null,
    onQueryChanged: (String) -> Unit,
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
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
        scrollBehavior = scrollBehavior,
    )
}

@Composable
private fun SearchContent(
    state: SearchViewModel.State,
    serverUrl: String?,
    toastState: ToastState,
    onItemClick: (AppMediaItem) -> Unit,
    onPlayClick: (AppMediaItem, QueueOption, Boolean) -> Unit,
    playlistActions: ActionsViewModel.PlaylistActions,
    libraryActions: ActionsViewModel.LibraryActions,
    progressActions: ActionsViewModel.ProgressActions? = null,
    providerIconFetcher: (@Composable (Modifier, String) -> Unit),
    contentPadding: PaddingValues,
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
                                key = { "${it.mediaType}_${it.provider}_${it.itemId}" },
                            ) { item ->
                                when (item) {
                                    is AppMediaItem.Track -> TrackWithMenu(
                                        serverUrl = serverUrl,
                                        viewMode = ViewMode.LIST,
                                        item = item,
                                        onPlayOption = onPlayClick,
                                        libraryActions = libraryActions,
                                        providerIconFetcher = providerIconFetcher,
                                    )

                                    is AppMediaItem.Artist -> ArtistWithMenu(
                                        serverUrl = serverUrl,
                                        viewMode = ViewMode.LIST,
                                        item = item,
                                        onNavigateClick = onItemClick,
                                        onPlayOption = onPlayClick,
                                        libraryActions = libraryActions,
                                        providerIconFetcher = providerIconFetcher,
                                    )

                                    is AppMediaItem.Album -> AlbumWithMenu(
                                        serverUrl = serverUrl,
                                        viewMode = ViewMode.LIST,
                                        item = item,
                                        onNavigateClick = onItemClick,
                                        onPlayOption = onPlayClick,
                                        libraryActions = libraryActions,
                                        providerIconFetcher = providerIconFetcher,
                                    )

                                    is AppMediaItem.Playlist -> PlaylistWithMenu(
                                        serverUrl = serverUrl,
                                        viewMode = ViewMode.LIST,
                                        item = item,
                                        onNavigateClick = onItemClick,
                                        onPlayOption = onPlayClick,
                                        libraryActions = libraryActions,
                                        providerIconFetcher = providerIconFetcher,
                                    )

                                    is AppMediaItem.Podcast -> PodcastWithMenu(
                                        serverUrl = serverUrl,
                                        viewMode = ViewMode.LIST,
                                        item = item,
                                        onNavigateClick = onItemClick,
                                        onPlayOption = onPlayClick,
                                        libraryActions = libraryActions,
                                        providerIconFetcher = providerIconFetcher,
                                    )

                                    is AppMediaItem.Audiobook -> AudiobookWithMenu(
                                        serverUrl = serverUrl,
                                        viewMode = ViewMode.LIST,
                                        item = item,
                                        onNavigateClick = onItemClick,
                                        onPlayOption = onPlayClick,
                                        libraryActions = libraryActions,
                                        providerIconFetcher = providerIconFetcher,
                                    )

                                    is AppMediaItem.RadioStation -> RadioWithMenu(
                                        serverUrl = serverUrl,
                                        viewMode = ViewMode.LIST,
                                        item = item,
                                        onPlayOption = onPlayClick,
                                        libraryActions = libraryActions,
                                        providerIconFetcher = providerIconFetcher,
                                    )

                                    is AppMediaItem.Genre -> GenreWithMenu(
                                        serverUrl = serverUrl,
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
                                contentPadding = contentPadding,
                            ) {
                                preparedItems.forEach { (stringTitle, items) ->
                                    if (items.isNotEmpty()) {
                                        item(key = stringTitle, contentType = "category") {
                                            CategoryRow(
                                                serverUrl = serverUrl,
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
                            text = mediaTypeSelect.type.name.lowercase().capitalize(Locale.current),
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
