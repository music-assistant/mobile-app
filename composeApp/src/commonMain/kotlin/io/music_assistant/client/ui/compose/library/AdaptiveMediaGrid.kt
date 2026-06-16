package io.music_assistant.client.ui.compose.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
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
import io.music_assistant.client.settings.ViewMode
import io.music_assistant.client.ui.compose.common.items.AlbumWithMenu
import io.music_assistant.client.ui.compose.common.items.ArtistWithMenu
import io.music_assistant.client.ui.compose.common.items.AudiobookWithMenu
import io.music_assistant.client.ui.compose.common.items.FolderCell
import io.music_assistant.client.ui.compose.common.items.GenreWithMenu
import io.music_assistant.client.ui.compose.common.items.LibraryActions
import io.music_assistant.client.ui.compose.common.items.PlayHandler
import io.music_assistant.client.ui.compose.common.items.PlaylistActions
import io.music_assistant.client.ui.compose.common.items.PlaylistWithMenu
import io.music_assistant.client.ui.compose.common.items.PodcastEpisodeWithMenu
import io.music_assistant.client.ui.compose.common.items.PodcastWithMenu
import io.music_assistant.client.ui.compose.common.items.ProgressActions
import io.music_assistant.client.ui.compose.common.items.RadioWithMenu
import io.music_assistant.client.ui.compose.common.items.TrackWithMenu
import io.music_assistant.client.ui.compose.common.items.lazyListKey
import io.music_assistant.client.utils.gridItemMinSize

@Composable
fun AdaptiveMediaGrid(
    modifier: Modifier = Modifier,
    items: List<AppMediaItem>,
    isLoadingMore: Boolean = false,
    hasMore: Boolean = true,
    viewMode: ViewMode = ViewMode.GRID,
    onNavigateClick: (AppMediaItem) -> Unit,
    onPlayClick: PlayHandler<AppMediaItem>,
    onLoadMore: () -> Unit = {},
    gridState: LazyGridState = rememberLazyGridState(),
    playlistActions: PlaylistActions,
    libraryActions: LibraryActions,
    progressActions: ProgressActions? = null,
    contentPadding: PaddingValues,
) {
    val isRow = viewMode == ViewMode.LIST
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
        columns = GridCells.Adaptive(minSize = gridItemMinSize()),
        contentPadding = contentPadding + PaddingValues(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        items(
            items = items,
            key = { it.lazyListKey() },
            span = if (isRow) {
                { GridItemSpan(maxLineSpan) }
            } else {
                null
            },
        ) { item ->
            when (item) {
                is Artist -> ArtistWithMenu(
                    item = item,
                    viewMode = viewMode,
                    onNavigateClick = onNavigateClick,
                    onPlayOption = onPlayClick,
                    libraryActions = libraryActions,
                    providerIconFetcher = null,
                )

                is Album -> AlbumWithMenu(
                    item = item,
                    viewMode = viewMode,
                    onNavigateClick = onNavigateClick,
                    onPlayOption = onPlayClick,
                    playlistActions = playlistActions,
                    libraryActions = libraryActions,
                    providerIconFetcher = null,
                )

                is Playlist -> PlaylistWithMenu(
                    item = item,
                    viewMode = viewMode,
                    onNavigateClick = onNavigateClick,
                    onPlayOption = onPlayClick,
                    libraryActions = libraryActions,
                    providerIconFetcher = null,
                )

                is Podcast -> PodcastWithMenu(
                    item = item,
                    viewMode = viewMode,
                    onNavigateClick = onNavigateClick,
                    onPlayOption = onPlayClick,
                    libraryActions = libraryActions,
                    providerIconFetcher = null,
                )

                is Track -> TrackWithMenu(
                    item = item,
                    viewMode = viewMode,
                    onPlayOption = onPlayClick,
                    playlistActions = playlistActions,
                    libraryActions = libraryActions,
                    providerIconFetcher = null,
                )

                is PodcastEpisode -> PodcastEpisodeWithMenu(
                    item = item,
                    viewMode = viewMode,
                    onPlayOption = onPlayClick,
                    playlistActions = playlistActions,
                    libraryActions = libraryActions,
                    progressActions = progressActions,
                    providerIconFetcher = null,
                )

                is Audiobook -> AudiobookWithMenu(
                    item = item,
                    viewMode = viewMode,
                    onNavigateClick = onNavigateClick,
                    onPlayOption = onPlayClick,
                    playlistActions = playlistActions,
                    libraryActions = libraryActions,
                    progressActions = progressActions,
                    providerIconFetcher = null,
                )

                is Genre -> GenreWithMenu(
                    item = item,
                    viewMode = viewMode,
                    onNavigateClick = onNavigateClick,
                    onPlayOption = onPlayClick,
                    libraryActions = libraryActions,
                    providerIconFetcher = null,
                )

                is RadioStation -> RadioWithMenu(
                    item = item,
                    viewMode = viewMode,
                    onPlayOption = onPlayClick,
                    playlistActions = playlistActions,
                    libraryActions = libraryActions,
                    providerIconFetcher = null,
                )

                is RecommendationFolder -> FolderCell(
                    item = item,
                    viewMode = viewMode,
                    onNavigateClick = onNavigateClick,
                )
            }
        }

        // Loading indicator at the bottom
        if (isLoadingMore) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            }
        }
    }
}
