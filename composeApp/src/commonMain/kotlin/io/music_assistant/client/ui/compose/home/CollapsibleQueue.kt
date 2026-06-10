// Compose layout values (sizes, alphas, animation durations) are visual design tokens.
@file:Suppress("MagicNumber")

package io.music_assistant.client.ui.compose.home

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalOverscrollFactory
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import compose.icons.TablerIcons
import compose.icons.tablericons.GripVertical
import io.music_assistant.client.data.model.client.Chapter
import io.music_assistant.client.data.model.client.ImageType
import io.music_assistant.client.data.model.client.Queue
import io.music_assistant.client.data.model.client.items.AppMediaItem
import io.music_assistant.client.data.model.client.items.Track
import io.music_assistant.client.data.model.client.items.image
import io.music_assistant.client.ui.compose.common.DataState
import io.music_assistant.client.ui.compose.common.action.QueueAction
import io.music_assistant.client.ui.compose.common.icons.PlayIcon
import io.music_assistant.client.ui.compose.common.icons.TrackIcon
import io.music_assistant.client.ui.compose.common.items.AddToPlaylistDialog
import io.music_assistant.client.ui.compose.common.items.DISABLED_ITEM_ALPHA
import io.music_assistant.client.ui.compose.common.items.PlaylistActions
import io.music_assistant.client.ui.compose.common.items.localizedSubtitle
import io.music_assistant.client.ui.compose.common.painters.rememberPlaceholderPainter
import io.music_assistant.client.ui.contentColorByLuminance
import io.music_assistant.client.utils.conditional
import io.music_assistant.client.utils.formatDuration
import kotlinx.coroutines.flow.Flow
import musicassistantclient.composeapp.generated.resources.Res
import musicassistantclient.composeapp.generated.resources.action_add_to_playlist
import musicassistantclient.composeapp.generated.resources.cd_toggle_queue
import musicassistantclient.composeapp.generated.resources.common_delete
import musicassistantclient.composeapp.generated.resources.item_subtitle_unknown
import musicassistantclient.composeapp.generated.resources.queue_browse_library
import musicassistantclient.composeapp.generated.resources.queue_cannot_play
import musicassistantclient.composeapp.generated.resources.queue_empty
import musicassistantclient.composeapp.generated.resources.queue_error
import musicassistantclient.composeapp.generated.resources.queue_label
import musicassistantclient.composeapp.generated.resources.queue_label_with_position
import musicassistantclient.composeapp.generated.resources.queue_loading
import musicassistantclient.composeapp.generated.resources.queue_no_items
import musicassistantclient.composeapp.generated.resources.queue_not_loaded
import org.jetbrains.compose.resources.stringResource
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import kotlin.time.DurationUnit

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CollapsibleQueue(
    modifier: Modifier = Modifier,
    queue: DataState<Queue>,
    isQueueExpanded: Boolean,
    onQueueExpandedSwitch: () -> Unit,
    onGoToLibrary: () -> Unit,
    queueAction: (QueueAction) -> Unit,
    tint: Color,
    isCurrentPage: Boolean = true,
    contentPadding: PaddingValues,
    playlistActions: PlaylistActions? = null,
    onChapterClick: (Chapter) -> Unit = {},
    livePositionFlow: Flow<Double>? = null,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .animateContentSize(),
    ) {
        // Action buttons (visible when expanded and has items)
        val queueData = queue as? DataState.Data
        val items = (queueData?.data?.items as? DataState.Data)?.data

        val defaultLabel = stringResource(Res.string.queue_label)
        val queueLabel = items?.let { list ->
            val currentId = queueData.data.info.currentItem?.id
            val currentPos = currentId?.let { id -> list.indexOfFirst { it.id == id } }
                ?.takeIf { it >= 0 }
                ?.let { it + 1 }
            currentPos?.let { stringResource(Res.string.queue_label_with_position, it, list.size) }
        } ?: defaultLabel

        val queueButtonContentColor = tint.contentColorByLuminance()
        Button(
            modifier = Modifier
                .let {
                    if (!isQueueExpanded) {
                        it.padding(contentPadding)
                    } else {
                        it
                    }
                }
                .fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = tint,
                contentColor = queueButtonContentColor,
            ),
            onClick = { onQueueExpandedSwitch() },
        ) {
            Text(
                text = queueLabel,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Icon(
                imageVector = if (isQueueExpanded) Icons.Default.ExpandMore else Icons.Default.ExpandLess,
                contentDescription = stringResource(Res.string.cd_toggle_queue),
            )
        }

        if (isQueueExpanded) {
            Queue(
                modifier = Modifier.fillMaxWidth().weight(1f),
                queue = queue,
                onGoToLibrary = onGoToLibrary,
                isQueueExpanded = isQueueExpanded,
                isCurrentPage = isCurrentPage,
                contentPadding = contentPadding,
                queueAction = queueAction,
                playlistActions = playlistActions,
                onChapterClick = onChapterClick,
                livePositionFlow = livePositionFlow,
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Queue(
    modifier: Modifier = Modifier,
    queue: DataState<Queue>,
    onGoToLibrary: () -> Unit,
    isQueueExpanded: Boolean,
    isCurrentPage: Boolean,
    contentPadding: PaddingValues,
    queueAction: (QueueAction) -> Unit,
    playlistActions: PlaylistActions? = null,
    onChapterClick: (Chapter) -> Unit = {},
    livePositionFlow: Flow<Double>? = null,
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        // Drop the list's inner overscroll so a downward pull at the top reaches the
        // ancestor collapse NestedScrollConnection instead of being eaten by the iOS
        // Cupertino rubber-band. Covers both the populated LazyColumn and the empty
        // verticalScroll Column.
        CompositionLocalProvider(LocalOverscrollFactory provides null) {
            val message: String? = when (queue) {
                is DataState.Error -> stringResource(Res.string.queue_error)
                is DataState.Loading -> stringResource(Res.string.queue_loading)
                is DataState.NoData -> stringResource(Res.string.queue_no_items)
                is DataState.Stale -> when (queue.data.items) {
                    is DataState.Error -> stringResource(Res.string.queue_error)
                    is DataState.Loading -> stringResource(Res.string.queue_loading)
                    is DataState.NoData -> stringResource(Res.string.queue_not_loaded)
                    is DataState.Data -> null
                    is DataState.Stale -> null
                }

                is DataState.Data -> when (queue.data.items) {
                    is DataState.Error -> stringResource(Res.string.queue_error)
                    is DataState.Loading -> stringResource(Res.string.queue_loading)
                    is DataState.NoData -> stringResource(Res.string.queue_not_loaded)
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
                val queueData = queue.dataOrNull ?: return@run
                val items = queueData.items.dataOrNull ?: return@run

                if (items.isEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState()),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(
                            16.dp,
                            Alignment.CenterVertically,
                        ),
                    ) {
                        Text(
                            text = stringResource(Res.string.queue_empty),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        OutlinedButton(
                            onClick = onGoToLibrary,
                        ) {
                            Text(stringResource(Res.string.queue_browse_library))
                        }
                    }
                } else {
                    val currentItemId = queueData.info.currentItem?.id

                    var internalItems by remember(items) { mutableStateOf(items) }
                    // Index of the current item in the OPTIMISTIC list, so played-state
                    // shading stays consistent with the displayed (possibly mid-drag) order.
                    val currentItemIndex = remember(internalItems, currentItemId) {
                        currentItemId?.let { id -> internalItems.indexOfFirst { it.id == id } } ?: -1
                    }
                    // Drag origin/destination in real queue-index space, tracked from
                    // the reorder lambda's live reads. Never captured from a row's
                    // composition (which goes stale after the server echo resets the
                    // list), so the relative positionShift sent to the server stays
                    // correct across consecutive drags.
                    var dragStartIndex by remember { mutableStateOf<Int?>(null) }
                    var dragEndIndex by remember { mutableStateOf<Int?>(null) }
                    var menuItemId by remember { mutableStateOf<String?>(null) }
                    var addToPlaylistTrack by remember { mutableStateOf<Track?>(null) }
                    val listState = rememberLazyListState()

                    // Flattened rows: real queue items, plus chapter rows under the
                    // current audiobook. Structurally stable across playback ticks.
                    val displayRows = remember(internalItems, currentItemId) {
                        internalItems.buildDisplayRows(currentItemId)
                    }
                    val currentChapters = remember(displayRows) {
                        displayRows.filterIsInstance<QueueDisplayRow.ChapterItem>()
                            .map { it.chapter }
                    }

                    // Live position drives only the active-chapter highlight + scroll
                    // target. derivedStateOf scans per tick but notifies readers only
                    // at chapter boundaries, so the Queue body does not recompose every
                    // ~500ms. Read positionState.value only inside a derivedStateOf (see
                    // active, scrollReady); a direct body read recomposes every tick.
                    // Seeded null (not elapsedTime) so the open-scroll can distinguish a
                    // resolved live position from the stale server seed — see scrollReady.
                    val positionState: State<Double?>? = livePositionFlow
                        ?.collectAsStateWithLifecycle(initialValue = null)
                    // Stand-in until the live flow resolves, and for previews where it
                    // never does. Constant, so deliberately NOT a remember key below:
                    // keying on it would re-create the derivedStateOf every server tick.
                    val fallbackPosition = queueData.info.elapsedTime ?: 0.0
                    val active by remember(currentChapters, positionState) {
                        derivedStateOf {
                            currentChapters.activeChapter(positionState?.value ?: fallbackPosition)
                        }
                    }

                    val reorderableLazyListState =
                        rememberReorderableLazyListState(listState) { from, to ->
                            // Map visual indices to real queue indices from live State,
                            // never a captured value, so the mapping can't go stale
                            // against an optimistic mid-drag reorder.
                            val rows = internalItems.buildDisplayRows(currentItemId)
                            val currentIdx = internalItems.indexOfFirst { it.id == currentItemId }
                            val fromQueueIndex = rows.queueIndexAt(from.index)
                                ?: return@rememberReorderableLazyListState
                            val toQueueIndex = rows.queueIndexAt(to.index)
                                ?: return@rememberReorderableLazyListState
                            // currentIdx == -1 (no current item) must not block reorders.
                            if (currentIdx >= 0 && toQueueIndex <= currentIdx) {
                                return@rememberReorderableLazyListState
                            }
                            // First move of a drag records the true origin index.
                            if (dragStartIndex == null) dragStartIndex = fromQueueIndex
                            internalItems = internalItems.toMutableList().apply {
                                add(toQueueIndex, removeAt(fromQueueIndex))
                            }
                            dragEndIndex = toQueueIndex
                        }

                    // Recomputed when the active chapter changes so a (re)open lands on the
                    // chapter playing now. The scroll itself (LaunchedEffect below) is
                    // intentionally NOT keyed on `active`, so crossing a chapter boundary
                    // mid-browse never yanks the user's scroll position.
                    val autoScrollIndex = remember(displayRows, active) {
                        val activeIdx = displayRows.indexOfFirst {
                            it is QueueDisplayRow.ChapterItem && it.chapter == active
                        }
                        val currentIdx = displayRows.indexOfFirst {
                            it is QueueDisplayRow.QueueItem && it.item.id == currentItemId
                        }
                        when {
                            active != null && activeIdx >= 0 -> activeIdx
                            currentIdx >= 0 -> currentIdx
                            else -> -1
                        }
                    }

                    // Gate the one-shot open-scroll on a resolved live position when the
                    // current item has chapters: the elapsedTime seed can name the previous
                    // chapter just after a boundary, and the scroll never re-runs on later
                    // position changes (by design — see autoScrollIndex). No chapters → the
                    // target is position-independent, so scroll immediately. derivedStateOf
                    // so this flips once on resolution, not on every ~500ms tick.
                    val scrollReady by remember(currentChapters, positionState) {
                        derivedStateOf {
                            currentChapters.isEmpty() ||
                                positionState == null || positionState.value != null
                        }
                    }
                    LaunchedEffect(isQueueExpanded, isCurrentPage, currentItemId, scrollReady) {
                        val shouldScroll =
                            isQueueExpanded && isCurrentPage && scrollReady && autoScrollIndex >= 0
                        if (shouldScroll && !listState.isScrollInProgress) {
                            listState.animateScrollToItem(
                                index = autoScrollIndex,
                                scrollOffset = -100, // Offset to show some items above
                            )
                        }
                    }

                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag(CollapsibleQueueSemantics.QUEUE_TAG),
                        state = listState,
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        contentPadding = contentPadding,
                    ) {
                        itemsIndexed(
                            items = displayRows,
                            key = { _, row -> row.key },
                        ) { _, row ->
                            if (row is QueueDisplayRow.ChapterItem) {
                                QueueChapterRow(
                                    chapter = row.chapter,
                                    isActive = row.chapter == active,
                                    onClick = { onChapterClick(row.chapter) },
                                )
                                return@itemsIndexed
                            }
                            val queueRow = row as QueueDisplayRow.QueueItem
                            val item = queueRow.item
                            val isCurrent = item.id == currentItemId
                            val isPlayed = queueRow.queueIndex < currentItemIndex
                            val isPlayable = item.isPlayable

                            ReorderableItem(
                                state = reorderableLazyListState,
                                key = queueRow.key,  // MUST equal the LazyColumn item key
                                enabled = isPlayable,  // Disable reordering for unplayable items
                            ) {
                                Box {
                                    Row(
                                        modifier = Modifier
                                            .padding(vertical = 1.dp)
                                            .alpha(
                                                when {
                                                    !isPlayable -> DISABLED_ITEM_ALPHA  // Gray out unplayable items
                                                    isPlayed -> 0.5f
                                                    else -> 1f
                                                },
                                            )
                                            .fillMaxWidth()
                                            .clip(shape = RoundedCornerShape(8.dp))
                                            .conditional(
                                                condition = isPlayed,  // Treat unplayable like played items
                                                ifTrue = {
                                                    clickable(isPlayable) {  // Only clickable if playable
                                                        queueAction(
                                                            QueueAction.PlayQueueItem(
                                                                queueData.info.id, item.id,
                                                            ),
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
                                                                        item.id,
                                                                    ),
                                                                )
                                                            }
                                                        },
                                                        onLongClick = {
                                                            menuItemId = item.id
                                                        },
                                                    )
                                                },
                                            )
                                            .padding(horizontal = 12.dp, vertical = 2.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.Start,
                                    ) {
                                        val placeholder = rememberPlaceholderPainter(
                                            backgroundColor = MaterialTheme.colorScheme.background,
                                            iconColor = MaterialTheme.colorScheme.secondary,
                                            icon = TrackIcon,
                                        )
                                        AsyncImage(
                                            modifier = Modifier
                                                .padding(end = 8.dp)
                                                .size(40.dp)
                                                .clip(RoundedCornerShape(size = 4.dp)),
                                            placeholder = placeholder,
                                            fallback = placeholder,
                                            model = item.track.image(ImageType.THUMB)?.url,
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
                                            modifier = Modifier.weight(1f).wrapContentHeight(),
                                        ) {
                                            Text(
                                                modifier = Modifier.fillMaxWidth(),
                                                text = item.track.displayName,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                color = MaterialTheme.colorScheme.secondary,
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = when {
                                                    isCurrent -> FontWeight.Bold
                                                    else -> FontWeight.Normal
                                                },
                                            )
                                            Text(
                                                modifier = Modifier.fillMaxWidth().alpha(0.7f),
                                                text = if (isPlayable) {
                                                    (item.track as? AppMediaItem)?.localizedSubtitle()
                                                        ?: stringResource(Res.string.item_subtitle_unknown)
                                                } else {
                                                    stringResource(Res.string.queue_cannot_play)
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
                                                            val from = dragStartIndex
                                                            val to = dragEndIndex
                                                            if (from != null && to != null) {
                                                                queueAction(
                                                                    QueueAction.MoveItem(
                                                                        queueData.info.id,
                                                                        item.id,
                                                                        from = from,
                                                                        to = to,
                                                                    ),
                                                                )
                                                            }
                                                            dragStartIndex = null
                                                            dragEndIndex = null
                                                        },
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
                                        onDismissRequest = { menuItemId = null },
                                    ) {
                                        val track = item.track as? Track
                                        if (track != null && playlistActions != null) {
                                            DropdownMenuItem(
                                                text = {
                                                    Text(stringResource(Res.string.action_add_to_playlist))
                                                },
                                                leadingIcon = {
                                                    Icon(
                                                        imageVector = Icons.AutoMirrored.Filled.PlaylistAdd,
                                                        contentDescription = null,
                                                    )
                                                },
                                                onClick = {
                                                    addToPlaylistTrack = track
                                                    menuItemId = null
                                                },
                                            )
                                        }
                                        DropdownMenuItem(
                                            text = { Text(stringResource(Res.string.common_delete)) },
                                            leadingIcon = {
                                                Icon(
                                                    imageVector = Icons.Default.Delete,
                                                    contentDescription = null,
                                                )
                                            },
                                            onClick = {
                                                queueAction(
                                                    QueueAction.RemoveItems(
                                                        queueData.info.id,
                                                        listOf(item.id),
                                                    ),
                                                )
                                                menuItemId = null
                                            },
                                        )
                                    }
                                }
                            }
                        }
                    }

                    addToPlaylistTrack?.let { track ->
                        if (playlistActions != null) {
                            AddToPlaylistDialog(
                                item = track,
                                playlistActions = playlistActions,
                                onDismiss = { addToPlaylistTrack = null },
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Display-only chapter row nested under the current audiobook. Tap-to-seek only:
 * no image, drag handle, menu, or delete. Kept private to this file so it cannot
 * be mistaken for a real queue item elsewhere.
 */
@Composable
private fun QueueChapterRow(
    chapter: Chapter,
    isActive: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(start = 72.dp, end = 12.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = chapter.name,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
            color = if (isActive) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.secondary
            },
        )

        if (chapter.end != null) {
            Text(
                text = chapter.duration.formatDuration(DurationUnit.SECONDS),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.7f),
            )
        }
    }
}

object CollapsibleQueueSemantics {
    const val QUEUE_TAG: String = "CollapsibleQueue"
}
