package io.music_assistant.client.ui.compose.item

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.music_assistant.client.data.model.client.items.AppMediaItem
import io.music_assistant.client.data.model.client.items.Artist
import io.music_assistant.client.settings.ViewMode
import io.music_assistant.client.ui.compose.common.CenteredProgress
import io.music_assistant.client.ui.compose.common.CenteredText
import io.music_assistant.client.ui.compose.common.DataState
import io.music_assistant.client.ui.compose.common.items.LibraryActions
import io.music_assistant.client.ui.compose.common.items.PlayHandler
import io.music_assistant.client.ui.compose.common.items.PlaylistActions
import io.music_assistant.client.ui.compose.library.AdaptiveMediaGrid
import musicassistantclient.composeapp.generated.resources.Res
import musicassistantclient.composeapp.generated.resources.action_similar_artists
import musicassistantclient.composeapp.generated.resources.cd_close
import musicassistantclient.composeapp.generated.resources.library_empty
import musicassistantclient.composeapp.generated.resources.library_error
import org.jetbrains.compose.resources.stringResource

/** Sheet height as a fraction of the screen — matches the "80% height" spec. */
private const val SHEET_HEIGHT_FRACTION = 0.8f

/**
 * Bottom sheet listing artists similar to the currently viewed one, rendered with the same
 * [AdaptiveMediaGrid] the library root uses so rows/grid parity is automatic. A centered spinner
 * shows while [state] is loading; tapping an artist reuses [onNavigateClick] to open its details.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SimilarArtistsSheet(
    state: DataState<List<Artist>>,
    viewMode: ViewMode,
    onNavigateClick: (AppMediaItem) -> Unit,
    onPlayClick: PlayHandler<AppMediaItem>,
    playlistActions: PlaylistActions,
    libraryActions: LibraryActions,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        dragHandle = null,
    ) {
        Column(modifier = Modifier.fillMaxWidth().fillMaxHeight(SHEET_HEIGHT_FRACTION)) {
            Header(onDismiss)
            Box(modifier = Modifier.weight(1f).fillMaxSize()) {
                when (state) {
                    is DataState.Loading -> CenteredProgress()

                    is DataState.Error -> CenteredText(
                        text = stringResource(Res.string.library_error),
                        color = MaterialTheme.colorScheme.error,
                    )

                    is DataState.Data,
                    is DataState.Stale,
                        -> {
                        val artists = state.dataOrNull.orEmpty()
                        if (artists.isEmpty()) {
                            CenteredText(stringResource(Res.string.library_empty))
                        } else {
                            AdaptiveMediaGrid(
                                items = artists,
                                viewMode = viewMode,
                                hasMore = false,
                                onNavigateClick = onNavigateClick,
                                onPlayClick = onPlayClick,
                                playlistActions = playlistActions,
                                libraryActions = libraryActions,
                                contentPadding = PaddingValues(bottom = 16.dp),
                            )
                        }
                    }

                    is DataState.NoData -> Unit
                }
            }
        }
    }
}

@Composable
private fun Header(onDismiss: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 24.dp, top = 12.dp, bottom = 12.dp, end = 4.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(
            text = stringResource(Res.string.action_similar_artists),
            style = MaterialTheme.typography.titleMedium,
        )
        IconButton(
            onClick = onDismiss,
            modifier = Modifier.align(Alignment.CenterEnd),
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = stringResource(Res.string.cd_close),
            )
        }
    }
}
