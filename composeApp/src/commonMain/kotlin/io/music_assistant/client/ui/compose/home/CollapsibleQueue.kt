package io.music_assistant.client.ui.compose.home

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Speaker
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import compose.icons.TablerIcons
import compose.icons.tablericons.GripVertical
import io.music_assistant.client.data.model.client.PlayerData
import io.music_assistant.client.data.model.client.Queue
import io.music_assistant.client.ui.compose.common.DataState
import io.music_assistant.client.ui.compose.common.OverflowMenu
import io.music_assistant.client.ui.compose.common.OverflowMenuOption
import io.music_assistant.client.ui.compose.common.action.QueueAction
import io.music_assistant.client.ui.compose.common.icons.PlayIcon
import io.music_assistant.client.ui.compose.common.icons.TrackIcon
import io.music_assistant.client.ui.compose.common.painters.rememberPlaceholderPainter
import io.music_assistant.client.utils.conditional
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CollapsibleQueue(
    modifier: Modifier = Modifier,
    queue: DataState<Queue>,
    isQueueExpanded: Boolean,
    onQueueExpandedSwitch: () -> Unit,
    onGoToLibrary: () -> Unit,
    serverUrl: String?,
    queueAction: (QueueAction) -> Unit,
    players: List<PlayerData> = emptyList(),
    onPlayerSelected: ((String) -> Unit)? = null,
    isCurrentPage: Boolean = true,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, bottom = if (isQueueExpanded) 0.dp else 16.dp)
            .animateContentSize(),
    ) {
        // Action buttons (visible when expanded and has items)
        val queueData = queue as? DataState.Data
        val items = (queueData?.data?.items as? DataState.Data)?.data

        val queueLabel = items?.let { list ->
            val currentId = queueData?.data?.info?.currentItem?.id
            val currentPos = currentId?.let { id -> list.indexOfFirst { it.id == id } }
                ?.takeIf { it >= 0 }
                ?.let { it + 1 }
            currentPos?.let { "Queue ($it/${list.size})" }
        } ?: "Queue"

        Button(
            modifier = Modifier.fillMaxWidth(),
            onClick = { onQueueExpandedSwitch() }
        ) {
            Text(
                text = queueLabel,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Icon(
                imageVector = if (isQueueExpanded) Icons.Default.ExpandMore else Icons.Default.ExpandLess,
                contentDescription = "Toggle Queue"
            )
        }
        val hasItems = items?.isNotEmpty() == true
        val queueId = queueData?.data?.info?.id

        if (isQueueExpanded && hasItems && queueId != null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Transfer button
                OverflowMenu(
                    options = players.filter { p -> p.player.id != queueId }.map { playerData ->
                        OverflowMenuOption(
                            title = playerData.player.nameAndSuffix,
                            icon = Icons.Default.Speaker,
                            onClick = {
                                queueAction(
                                    QueueAction.Transfer(
                                        queueId,
                                        playerData.player.id,
                                        playerData.player.isPlaying
                                    )
                                )
                                onPlayerSelected?.invoke(playerData.player.id)
                            }
                        )
                    }.ifEmpty {
                        listOf(
                            OverflowMenuOption(
                                title = "No other players available",
                                onClick = { /* No-op */ }
                            )
                        )
                    },
                    buttonContent = {
                        OutlinedButton(onClick = it) {
                            Text("Transfer")
                        }
                    }
                )

                // Clear button
                OutlinedButton(
                    onClick = { queueAction(QueueAction.ClearQueue(queueId)) }
                ) {
                    Text("Clear")
                }
            }
        }

        if (isQueueExpanded) {
            Box(
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentAlignment = Alignment.Center
            ) {
                val message: String? = when (queue) {
                    is DataState.Error -> "Error loading queue"
                    is DataState.Loading -> "Loading queue..."
                    is DataState.NoData -> "No items"
                    is DataState.Stale -> when (queue.data.items) {
                        is DataState.Error -> "Error loading queue"
                        is DataState.Loading -> "Loading queue..."
                        is DataState.NoData -> "Not loaded"
                        is DataState.Data -> null
                        is DataState.Stale -> null
                    }

                    is DataState.Data -> when (queue.data.items) {
                        is DataState.Error -> "Error loading queue"
                        is DataState.Loading -> "Loading queue..."
                        is DataState.NoData -> "Not loaded"
                        is DataState.Data -> null
                        is DataState.Stale -> null
                    }
                }

                message?.let {
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } ?: run {
                    // Handle both Data and Stale states - both contain valid queue data
                    val queueData = when (queue) {
                        is DataState.Data -> queue.data
                        is DataState.Stale -> queue.data
                        else -> return@run
                    }
                    val items = when (val itemsState = queueData.items) {
                        is DataState.Data -> itemsState.data
                        is DataState.Stale -> itemsState.data
                        else -> return@run
                    }

                    if (items.isEmpty()) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Text(
                                text = "Queue is empty",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            OutlinedButton(
                                onClick = onGoToLibrary
                            ) {
                                Text("BROWSE LIBRARY")
                            }
                        }
                    } else {
                        val currentItemId = queueData.info.currentItem?.id
                        val currentItemIndex = currentItemId?.let { id ->
                            items.indexOfFirst { it.id == id }
                        } ?: -1

                        var internalItems by remember(items) { mutableStateOf(items) }
                        var dragEndIndex by remember { mutableStateOf<Int?>(null) }
                        var menuItemId by remember { mutableStateOf<String?>(null) }
                        val listState = rememberLazyListState()
                        val reorderableLazyListState =
                            rememberReorderableLazyListState(listState) { from, to ->
                                if (to.index <= currentItemIndex) {
                                    return@rememberReorderableLazyListState
                                }
                                internalItems = internalItems.toMutableList().apply {
                                    add(to.index, removeAt(from.index))
                                }
                                dragEndIndex = to.index
                            }

                        // Auto-scroll to current item when queue is shown or page becomes current
                        LaunchedEffect(isQueueExpanded, isCurrentPage, currentItemIndex) {
                            if (isQueueExpanded && isCurrentPage && currentItemIndex >= 0) {
                                // Scroll to show the current item with some context
                                // Center the current item in the viewport
                                listState.animateScrollToItem(
                                    index = currentItemIndex,
                                    scrollOffset = -100 // Offset to show some items above
                                )
                            }
                        }

                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            state = listState,
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            itemsIndexed(
                                items = internalItems,
                                key = { _, item -> item.id }) { index, item ->
                                val isCurrent = item.id == currentItemId
                                val isPlayed = index < currentItemIndex
                                val isPlayable = item.isPlayable

                                ReorderableItem(
                                    state = reorderableLazyListState,
                                    key = item.id,
                                    enabled = isPlayable,  // Disable reordering for unplayable items
                                ) {
                                    Box {
                                        Row(
                                            modifier = Modifier
                                                .padding(vertical = 1.dp)
                                                .alpha(
                                                    when {
                                                        !isPlayable -> 0.3f  // Gray out unplayable items
                                                        isPlayed -> 0.5f
                                                        else -> 1f
                                                    }
                                                )
                                                .fillMaxWidth()
                                                .clip(shape = RoundedCornerShape(8.dp))
                                                .conditional(
                                                    condition = isPlayed,  // Treat unplayable like played items
                                                    ifTrue = {
                                                        clickable(isPlayable) {  // Only clickable if playable
                                                            queueAction(
                                                                QueueAction.PlayQueueItem(
                                                                    queueData.info.id, item.id
                                                                )
                                                            )
                                                        }
                                                    },
                                                    ifFalse = {
                                                        combinedClickable(
                                                            onClick = {
                                                                if (!isCurrent && isPlayable) {
                                                                    queueAction(
                                                                        QueueAction.PlayQueueItem(
                                                                            queueData.info.id,
                                                                            item.id
                                                                        )
                                                                    )
                                                                }
                                                            },
                                                            onLongClick = {
                                                                menuItemId = item.id
                                                            },
                                                        )
                                                    }
                                                )
                                                .padding(horizontal = 12.dp, vertical = 2.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.Start
                                        ) {
                                            val placeholder = rememberPlaceholderPainter(
                                                backgroundColor = MaterialTheme.colorScheme.background,
                                                iconColor = MaterialTheme.colorScheme.secondary,
                                                icon = TrackIcon
                                            )
                                            AsyncImage(
                                                modifier = Modifier
                                                    .padding(end = 8.dp)
                                                    .size(40.dp)
                                                    .clip(RoundedCornerShape(size = 4.dp)),
                                                placeholder = placeholder,
                                                fallback = placeholder,
                                                model = item.track.imageInfo?.url(serverUrl),
                                                contentDescription = null,
                                                contentScale = ContentScale.Crop,
                                            )
                                            if (isCurrent) {
                                                Icon(
                                                    modifier = Modifier.padding(end = 8.dp)
                                                        .size(12.dp),
                                                    imageVector = PlayIcon,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.secondary,
                                                )
                                            }
                                            Column(
                                                modifier = Modifier.weight(1f).wrapContentHeight()
                                            ) {
                                                Text(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    text = item.track.name,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                    color = MaterialTheme.colorScheme.secondary,
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    fontWeight = when {
                                                        isCurrent -> FontWeight.Bold
                                                        else -> FontWeight.Normal
                                                    }
                                                )
                                                Text(
                                                    modifier = Modifier.fillMaxWidth().alpha(0.7f),
                                                    text = if (isPlayable) {
                                                        item.track.subtitle ?: "Unknown"
                                                    } else {
                                                        "Cannot play this item"
                                                    },
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                    color = MaterialTheme.colorScheme.secondary,
                                                    style = MaterialTheme.typography.bodySmall,
                                                )
                                            }
                                            if (!isCurrent && !isPlayed && isPlayable) {
                                                Icon(
                                                    modifier = Modifier
                                                        .draggableHandle(
                                                            onDragStopped = {
                                                                dragEndIndex?.let { to ->
                                                                    queueAction(
                                                                        QueueAction.MoveItem(
                                                                            queueData.info.id,
                                                                            item.id,
                                                                            from = index,
                                                                            to = to
                                                                        )
                                                                    )
                                                                }
                                                            }
                                                        )
                                                        .size(16.dp),
                                                    imageVector = TablerIcons.GripVertical,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.secondary,
                                                )
                                            }
                                        }

                                        // Long-click menu
                                        DropdownMenu(
                                            expanded = menuItemId == item.id,
                                            onDismissRequest = { menuItemId = null }
                                        ) {
                                            DropdownMenuItem(
                                                text = { Text("Delete") },
                                                onClick = {
                                                    queueAction(
                                                        QueueAction.RemoveItems(
                                                            queueData.info.id,
                                                            listOf(item.id)
                                                        )
                                                    )
                                                    menuItemId = null
                                                }
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
    }
}
