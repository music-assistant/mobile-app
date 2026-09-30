package io.music_assistant.client.ui.compose.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import compose.icons.TablerIcons
import compose.icons.tablericons.Plus
import io.music_assistant.client.data.model.client.ClickContext
import io.music_assistant.client.data.model.client.items.AppMediaItem
import io.music_assistant.client.settings.ViewMode
import io.music_assistant.client.ui.compose.common.DataState
import io.music_assistant.client.ui.compose.common.clearFocusOnScroll
import io.music_assistant.client.ui.compose.common.items.CreatePlaylistDialog
import io.music_assistant.client.ui.compose.common.items.LibraryActions
import io.music_assistant.client.ui.compose.common.items.PlayHandler
import io.music_assistant.client.ui.compose.common.items.PlaylistActions
import io.music_assistant.client.ui.compose.common.items.ProgressActions
import io.music_assistant.client.ui.compose.common.items.ProvideClickActions
import musicassistantclient.composeapp.generated.resources.Res
import musicassistantclient.composeapp.generated.resources.cd_add_playlist
import musicassistantclient.composeapp.generated.resources.library_empty
import musicassistantclient.composeapp.generated.resources.library_error
import musicassistantclient.composeapp.generated.resources.library_search_global
import musicassistantclient.composeapp.generated.resources.playlist_add_new
import org.jetbrains.compose.resources.stringResource

/**
 * Component for showing a list of media items from [data]. Supports pagination through
 * [onLoadMore], [isLoadingMore] and [hasMore]. The empty state can optionally link to a global
 * search with [onGlobalSearch].
 */
@Composable
fun ItemListContent(
    modifier: Modifier = Modifier,
    data: DataState<List<AppMediaItem>>,
    onNavigateClick: (AppMediaItem) -> Unit,
    onPlayClick: PlayHandler<AppMediaItem>,
    onCreatePlaylist: ((String) -> Unit)? = null,
    playlistActions: PlaylistActions,
    libraryActions: LibraryActions,
    progressActions: ProgressActions,
    contentPadding: PaddingValues,
    viewMode: ViewMode,
    hasMore: Boolean = false,
    isLoadingMore: Boolean = false,
    onLoadMore: () -> Unit = {},
    onGlobalSearch: (() -> Unit)? = null,
    clickContext: ClickContext,
) {
    var showCreatePlaylistDialog by rememberSaveable { mutableStateOf(false) }
    if (onCreatePlaylist != null && showCreatePlaylistDialog) {
        CreatePlaylistDialog(
            onDismiss = { showCreatePlaylistDialog = false },
            onCreate = {
                showCreatePlaylistDialog = false
                onCreatePlaylist(it)
            },
        )
    }

    ProvideClickActions(clickContext) {
        Box(modifier = modifier) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .clearFocusOnScroll(),
            ) {
                // Content area
                Box(modifier = Modifier.fillMaxSize()) {
                    when (data) {
                        is DataState.Loading -> LoadingState()
                        is DataState.Error -> ErrorState()
                        is DataState.NoData -> EmptyState(onGlobalSearch)
                        is DataState.Stale,
                        is DataState.Data,
                            -> {
                            val items = data.dataOrNull.orEmpty()
                            if (items.isEmpty()) {
                                EmptyState(onGlobalSearch)
                            } else {
                                Column(modifier = Modifier.fillMaxSize()) {
                                    if (onCreatePlaylist != null) {
                                        OutlinedButton(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 16.dp, vertical = 8.dp),
                                            onClick = { showCreatePlaylistDialog = true },
                                        ) {
                                            Icon(
                                                TablerIcons.Plus,
                                                contentDescription = stringResource(Res.string.cd_add_playlist),
                                            )
                                            Spacer(Modifier.width(4.dp))
                                            Text(stringResource(Res.string.playlist_add_new))
                                        }
                                    }

                                    val gridState = rememberLazyGridState()
                                    AdaptiveMediaGrid(
                                        modifier = Modifier.fillMaxSize(),
                                        items = items,
                                        isLoadingMore = isLoadingMore,
                                        hasMore = hasMore,
                                        viewMode = viewMode,
                                        onNavigateClick = onNavigateClick,
                                        onPlayClick = onPlayClick,
                                        onLoadMore = onLoadMore,
                                        gridState = gridState,
                                        playlistActions = playlistActions,
                                        libraryActions = libraryActions,
                                        progressActions = progressActions,
                                        contentPadding = contentPadding,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LoadingState() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator()
    }
}

@Composable
private fun ErrorState() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = stringResource(Res.string.library_error),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.error,
        )
    }
}

@Composable
private fun EmptyState(onGlobalSearch: (() -> Unit)? = null) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = stringResource(Res.string.library_empty),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            // Offer escalation to global search only when an actual query yielded nothing.
            if (onGlobalSearch != null) {
                OutlinedButton(onClick = onGlobalSearch) {
                    Icon(
                        Icons.Default.Search,
                        contentDescription = null,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(Res.string.library_search_global))
                }
            }
        }
    }
}
