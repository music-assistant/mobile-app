// Compose layout values (icon sizes, padding) are visual design tokens.
@file:Suppress("MagicNumber")

package io.music_assistant.client.wear.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.CircularProgressIndicator
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.SurfaceTransformation
import androidx.wear.compose.material3.SwipeToReveal
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.lazy.rememberTransformationSpec
import androidx.wear.compose.material3.lazy.transformedHeight
import io.music_assistant.client.wear.R
import io.music_assistant.client.wear.data.WearQueueItem

/**
 * The queue of the selected player, as a window around the track that is playing.
 *
 * A tap plays the row; a swipe reveals Remove. The row that is playing is drawn filled, so
 * the user's place in the queue is readable at a glance on a small screen.
 */
@Composable
fun QueueScreen(
    state: WearUiState,
    onPlayItem: (String) -> Unit,
    onRemoveItem: (String) -> Unit,
) {
    val listState = rememberTransformingLazyColumnState()
    val spec = rememberTransformationSpec()
    ScreenScaffold(scrollState = listState) { contentPadding ->
        TransformingLazyColumn(
            state = listState,
            contentPadding = contentPadding,
            modifier = Modifier.fillMaxWidth(),
        ) {
            item {
                ListHeader(
                    modifier = Modifier.transformedHeight(this, spec),
                    transformation = SurfaceTransformation(spec),
                ) {
                    Text(stringResource(R.string.queue_title))
                }
            }
            if (state.queue.items.isEmpty()) {
                item {
                    QueuePlaceholder(isLoading = state.queue.isLoading)
                }
            }
            items(state.queue.items, key = { it.id }) { queueItem ->
                SwipeToReveal(
                    modifier = Modifier.transformedHeight(this, spec),
                    primaryAction = {
                        PrimaryActionButton(
                            onClick = { onRemoveItem(queueItem.id) },
                            icon = {
                                Icon(
                                    painter = painterResource(R.drawable.ic_delete),
                                    contentDescription = null,
                                )
                            },
                            text = { Text(stringResource(R.string.cd_remove)) },
                        )
                    },
                    onSwipePrimaryAction = { onRemoveItem(queueItem.id) },
                ) {
                    QueueRow(
                        item = queueItem,
                        isPlaying = queueItem.id == state.activeQueueItemId,
                        transformation = SurfaceTransformation(spec),
                        onClick = { onPlayItem(queueItem.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun QueueRow(
    item: WearQueueItem,
    isPlaying: Boolean,
    transformation: SurfaceTransformation,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        transformation = transformation,
        colors = if (isPlaying) {
            ButtonDefaults.buttonColors()
        } else {
            ButtonDefaults.filledTonalButtonColors()
        },
        icon = if (isPlaying) {
            {
                Icon(
                    painter = painterResource(R.drawable.ic_music_note),
                    contentDescription = null,
                    modifier = Modifier.size(ButtonDefaults.IconSize),
                )
            }
        } else {
            null
        },
        secondaryLabel = item.artist?.let { artist ->
            {
                Text(text = artist, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        },
        label = {
            Text(text = item.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
        },
    )
}

@Composable
private fun QueuePlaceholder(isLoading: Boolean) {
    if (isLoading) {
        CircularProgressIndicator(modifier = Modifier.size(32.dp))
    } else {
        Text(
            text = stringResource(R.string.queue_empty),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 12.dp),
        )
    }
}
