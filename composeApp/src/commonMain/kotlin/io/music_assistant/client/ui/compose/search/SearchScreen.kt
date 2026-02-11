@file:OptIn(ExperimentalMaterial3Api::class)

package io.music_assistant.client.ui.compose.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.capitalize
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.music_assistant.client.data.model.client.AppMediaItem
import io.music_assistant.client.data.model.client.PlayableItem
import io.music_assistant.client.data.model.server.MediaType
import io.music_assistant.client.data.model.server.QueueOption
import io.music_assistant.client.ui.compose.common.DataState
import io.music_assistant.client.ui.compose.common.ToastHost
import io.music_assistant.client.ui.compose.common.ToastState
import io.music_assistant.client.ui.compose.common.items.MediaItemAlbum
import io.music_assistant.client.ui.compose.common.items.MediaItemArtist
import io.music_assistant.client.ui.compose.common.items.MediaItemPlaylist
import io.music_assistant.client.ui.compose.common.items.MediaItemPodcast
import io.music_assistant.client.ui.compose.common.items.RadioWithMenu
import io.music_assistant.client.ui.compose.common.items.TrackWithMenu
import io.music_assistant.client.ui.compose.common.providers.ProviderIcon
import io.music_assistant.client.ui.compose.common.rememberToastState
import io.music_assistant.client.ui.compose.common.viewmodel.ActionsViewModel
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun SearchScreen(
    onBack: () -> Unit,
    onNavigateToItem: (String, MediaType, String) -> Unit,
    viewModel: SearchViewModel = koinViewModel(),
    actionsViewModel: ActionsViewModel = koinViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val serverUrl by viewModel.serverUrl.collectAsStateWithLifecycle(null)
    val toastState = rememberToastState()

    LaunchedEffect(Unit) {
        actionsViewModel.toasts.collect { toast ->
            toastState.showToast(toast)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        SearchTopBar(
            onBack = onBack,
        )

        SearchContent(
            state = state,
            serverUrl = serverUrl,
            toastState = toastState,
            onQueryChanged = viewModel::onQueryChanged,
            onMediaTypeToggled = viewModel::onMediaTypeToggled,
            onLibraryOnlyToggled = viewModel::onLibraryOnlyToggled,
            onItemClick = { item ->
                when (item) {
                    is AppMediaItem.Artist,
                    is AppMediaItem.Album,
                    is AppMediaItem.Playlist -> {
                        onNavigateToItem(item.itemId, item.mediaType, item.provider)
                    }

                    else -> Unit
                }
            },
            onTrackClick = viewModel::onTrackClick,
            playlistActions = ActionsViewModel.PlaylistActions(
                onLoadPlaylists = actionsViewModel::getEditablePlaylists,
                onAddToPlaylist = actionsViewModel::addToPlaylist
            ),
            libraryActions = ActionsViewModel.LibraryActions(
                onLibraryClick = actionsViewModel::onLibraryClick,
                onFavoriteClick = actionsViewModel::onFavoriteClick
            ),
            providerIconFetcher = { modifier, provider ->
                actionsViewModel.getProviderIcon(provider)
                    ?.let { ProviderIcon(modifier, it) }
            }
        )
    }
}

@Composable
private fun SearchTopBar(
    onBack: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 2.dp
    ) {
        Row(
            modifier = Modifier.height(64.dp).fillMaxWidth().padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.Start)
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
            }
            Text(
                text = "Global search",
                style = MaterialTheme.typography.titleLarge
            )
        }
    }
}

@Composable
private fun SearchContent(
    state: SearchViewModel.State,
    serverUrl: String?,
    toastState: ToastState,
    onQueryChanged: (String) -> Unit,
    onMediaTypeToggled: (MediaType, Boolean) -> Unit,
    onLibraryOnlyToggled: (Boolean) -> Unit,
    onItemClick: (AppMediaItem) -> Unit,
    onTrackClick: (PlayableItem, QueueOption, Boolean) -> Unit,
    playlistActions: ActionsViewModel.PlaylistActions,
    libraryActions: ActionsViewModel.LibraryActions,
    providerIconFetcher: (@Composable (Modifier, String) -> Unit),
) {
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            SearchInput(state.searchState.query, onQueryChanged)

            // Search filters (always visible)
            SearchFilters(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                searchState = state.searchState,
                onMediaTypeToggled = onMediaTypeToggled,
                onLibraryOnlyToggled = onLibraryOnlyToggled
            )

            // Results
            when (val resultsState = state.resultsState) {
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
                            text = "Error loading search results",
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }

                is DataState.Stale,
                is DataState.Data -> {
                    // Handle both Data and Stale - both contain valid search results
                    val results = when (resultsState) {
                        is DataState.Data -> resultsState.data
                        is DataState.Stale -> resultsState.data
                        else -> return@Column
                    }
                    val hasResults = results.artists.isNotEmpty() ||
                            results.albums.isNotEmpty() ||
                            results.tracks.isNotEmpty() ||
                            results.playlists.isNotEmpty() ||
                            results.podcasts.isNotEmpty() ||
                            results.radios.isNotEmpty()

                    if (!hasResults) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("No results found")
                        }
                    } else {
                        LazyVerticalGrid(
                            modifier = Modifier.fillMaxSize(),
                            columns = GridCells.Adaptive(minSize = 96.dp),
                            contentPadding = PaddingValues(8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Tracks section
                            if (results.tracks.isNotEmpty()) {
                                item(span = { GridItemSpan(maxLineSpan) }) {
                                    SectionHeader("Tracks")
                                }
                                items(results.tracks) { track ->
                                    TrackWithMenu(
                                        item = track,
                                        serverUrl = serverUrl,
                                        onTrackPlayOption = onTrackClick,
                                        playlistActions = playlistActions,
                                        libraryActions = libraryActions,
                                        providerIconFetcher = providerIconFetcher,
                                    )
                                }
                            }

                            // Artists section
                            if (results.artists.isNotEmpty()) {
                                item(span = { GridItemSpan(maxLineSpan) }) {
                                    SectionHeader("Artists")
                                }
                                items(results.artists) { artist ->
                                    MediaItemArtist(
                                        item = artist,
                                        serverUrl = serverUrl,
                                        onClick = { onItemClick(it) },
                                        providerIconFetcher = providerIconFetcher,
                                    )
                                }
                            }

                            // Albums section
                            if (results.albums.isNotEmpty()) {
                                item(span = { GridItemSpan(maxLineSpan) }) {
                                    SectionHeader("Albums")
                                }
                                items(results.albums) { album ->
                                    MediaItemAlbum(
                                        item = album,
                                        serverUrl = serverUrl,
                                        onClick = { onItemClick(it) },
                                        providerIconFetcher = providerIconFetcher,
                                    )
                                }
                            }

                            // Playlists section
                            if (results.playlists.isNotEmpty()) {
                                item(span = { GridItemSpan(maxLineSpan) }) {
                                    SectionHeader("Playlists")
                                }
                                items(results.playlists) { playlist ->
                                    MediaItemPlaylist(
                                        item = playlist,
                                        serverUrl = serverUrl,
                                        onClick = { onItemClick(it) },
                                        providerIconFetcher = providerIconFetcher,
                                    )
                                }
                            }

                            // Podcasts section
                            if (results.podcasts.isNotEmpty()) {
                                item(span = { GridItemSpan(maxLineSpan) }) {
                                    SectionHeader("Podcasts")
                                }
                                items(results.podcasts) { podcast ->
                                    MediaItemPodcast(
                                        item = podcast,
                                        serverUrl = serverUrl,
                                        onClick = { onItemClick(it) },
                                        providerIconFetcher = providerIconFetcher,
                                    )
                                }
                            }

                            // Radio section
                            if (results.radios.isNotEmpty()) {
                                item(span = { GridItemSpan(maxLineSpan) }) {
                                    SectionHeader("Radio")
                                }
                                items(results.radios) { radio ->
                                    RadioWithMenu(
                                        item = radio,
                                        serverUrl = serverUrl,
                                        onTrackPlayOption = onTrackClick,
                                        onItemClick = {
                                            (it as? AppMediaItem)?.let { i ->
                                                onItemClick(
                                                    i
                                                )
                                            }
                                        },
                                        playlistActions = playlistActions,
                                        libraryActions = libraryActions,
                                        providerIconFetcher = providerIconFetcher,
                                    )
                                }
                            }
                        }
                    }
                }

                is DataState.NoData -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Start searching...")
                    }
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SearchFilters(
    modifier: Modifier = Modifier,
    searchState: SearchViewModel.SearchState,
    onMediaTypeToggled: (MediaType, Boolean) -> Unit,
    onLibraryOnlyToggled: (Boolean) -> Unit,
) {
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Media type filter chips
        searchState.mediaTypes.forEach { mediaTypeSelect ->
            FilterChip(
                selected = mediaTypeSelect.isSelected,
                onClick = {
                    onMediaTypeToggled(
                        mediaTypeSelect.type,
                        !mediaTypeSelect.isSelected
                    )
                },
                label = {
                    Text(
                        text = mediaTypeSelect.type.name.lowercase().capitalize(Locale.current),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            )
        }

        // In library only filter chip
        FilterChip(
            selected = searchState.libraryOnly,
            onClick = { onLibraryOnlyToggled(!searchState.libraryOnly) },
            label = {
                Text(text = "In library only", style = MaterialTheme.typography.bodySmall)
            }
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
