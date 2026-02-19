package io.music_assistant.client.ui.compose.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.music_assistant.client.data.model.client.AppMediaItem
import io.music_assistant.client.data.model.server.QueueOption
import io.music_assistant.client.ui.compose.common.items.AlbumWithMenu
import io.music_assistant.client.ui.compose.common.items.ArtistWithMenu
import io.music_assistant.client.ui.compose.common.items.AudiobookWithMenu
import io.music_assistant.client.ui.compose.common.items.PlaylistWithMenu
import io.music_assistant.client.ui.compose.common.items.PodcastEpisodeWithMenu
import io.music_assistant.client.ui.compose.common.items.PodcastWithMenu
import io.music_assistant.client.ui.compose.common.items.RadioWithMenu
import io.music_assistant.client.ui.compose.common.items.TrackWithMenu
import io.music_assistant.client.ui.compose.common.viewmodel.ActionsViewModel

@Composable
fun AdaptiveMediaGrid(
    modifier: Modifier = Modifier,
    items: List<AppMediaItem>,
    serverUrl: String?,
    isLoadingMore: Boolean = false,
    hasMore: Boolean = true,
    isRowMode: Boolean = false,
    onNavigateClick: (AppMediaItem) -> Unit,
    onPlayClick: ((AppMediaItem, QueueOption, Boolean) -> Unit),
    onLoadMore: () -> Unit = {},
    gridState: LazyGridState = rememberLazyGridState(),
    playlistActions: ActionsViewModel.PlaylistActions,
    libraryActions: ActionsViewModel.LibraryActions,
    progressActions: ActionsViewModel.ProgressActions? = null,
) {
    // Detect when we're near the end and trigger load more
    val shouldLoadMore by remember {
        derivedStateOf {
            val layoutInfo = gridState.layoutInfo
            val totalItems = layoutInfo.totalItemsCount
            val lastVisibleItem = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0

            // Load more when we're within 10 items of the end
            hasMore && !isLoadingMore && totalItems > 0 && lastVisibleItem >= totalItems - 10
        }
    }

    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore) {
            onLoadMore()
        }
    }

    LazyVerticalGrid(
        modifier = modifier,
        state = gridState,
        columns = GridCells.Adaptive(minSize = 96.dp),
        contentPadding = PaddingValues(8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(
            items = items,
            key = { it.itemId },
            span = if (isRowMode) {
                { GridItemSpan(maxLineSpan) }
            } else null
        ) { item ->
            val rowModifier = if (isRowMode) Modifier.fillMaxWidth() else Modifier
            when (item) {
                is AppMediaItem.Artist -> ArtistWithMenu(
                    item = item,
                    rowMode = isRowMode,
                    showSubtitle = true,
                    serverUrl = serverUrl,
                    onNavigateClick = onNavigateClick,
                    onPlayOption = onPlayClick,
                    libraryActions = libraryActions,
                    providerIconFetcher = null
                )

                is AppMediaItem.Album -> AlbumWithMenu(
                    item = item,
                    rowMode = isRowMode,
                    showSubtitle = true,
                    serverUrl = serverUrl,
                    onNavigateClick = onNavigateClick,
                    onPlayOption = onPlayClick,
                    libraryActions = libraryActions,
                    providerIconFetcher = null
                )

                is AppMediaItem.Playlist -> PlaylistWithMenu(
                    item = item,
                    rowMode = isRowMode,
                    showSubtitle = true,
                    serverUrl = serverUrl,
                    onNavigateClick = onNavigateClick,
                    onPlayOption = onPlayClick,
                    libraryActions = libraryActions,
                    providerIconFetcher = null
                )

                is AppMediaItem.Podcast -> PodcastWithMenu(
                    item = item,
                    rowMode = isRowMode,
                    showSubtitle = true,
                    serverUrl = serverUrl,
                    onNavigateClick = onNavigateClick,
                    onPlayOption = onPlayClick,
                    libraryActions = libraryActions,
                    providerIconFetcher = null
                )

                is AppMediaItem.Track -> TrackWithMenu(
                    item = item,
                    rowMode = isRowMode,
                    serverUrl = serverUrl,
                    onPlayOption = onPlayClick,
                    playlistActions = playlistActions,
                    libraryActions = libraryActions,
                    providerIconFetcher = null
                )

                is AppMediaItem.PodcastEpisode -> PodcastEpisodeWithMenu(
                    item = item,
                    rowMode = isRowMode,
                    serverUrl = serverUrl,
                    onPlayOption = onPlayClick,
                    playlistActions = playlistActions,
                    libraryActions = libraryActions,
                    progressActions = progressActions,
                    providerIconFetcher = null
                )

                is AppMediaItem.Audiobook -> AudiobookWithMenu(
                    item = item,
                    rowMode = isRowMode,
                    showSubtitle = true,
                    serverUrl = serverUrl,
                    onNavigateClick = onNavigateClick,
                    onPlayOption = onPlayClick,
                    libraryActions = libraryActions,
                    progressActions = progressActions,
                    providerIconFetcher = null
                )

                is AppMediaItem.RadioStation -> RadioWithMenu(
                    item = item,
                    rowMode = isRowMode,
                    serverUrl = serverUrl,
                    onPlayOption = onPlayClick,
                    playlistActions = playlistActions,
                    libraryActions = libraryActions,
                    providerIconFetcher = null
                )

                else -> {
                    // Unsupported item type - skip
                }
            }
        }

        // Loading indicator at the bottom
        if (isLoadingMore) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
        }
    }
}
