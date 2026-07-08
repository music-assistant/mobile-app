package io.music_assistant.client.ui.compose.home.players

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.music_assistant.client.data.model.client.LrcLine
import io.music_assistant.client.data.model.client.Lyrics
import io.music_assistant.client.ui.inactive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import musicassistantclient.composeapp.generated.resources.Res
import musicassistantclient.composeapp.generated.resources.cd_lyrics_close
import org.jetbrains.compose.resources.stringResource

/**
 * Modal sheet showing [lyrics], peeking to ~80% of the available height and
 * painted with the same art-derived gradient as the player view. [Lyrics.Synced]
 * enlarges, highlights, and auto-scrolls the active line against
 * [livePositionFlow] (seconds); when the flow is absent (or the lyrics are
 * [Lyrics.Plain]) it renders as static scrollable text.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LyricsSheet(
    lyrics: Lyrics,
    livePositionFlow: Flow<Double>?,
    onDismiss: () -> Unit,
) {
    var offsetSec by remember(lyrics) { mutableStateOf(0f) }
    val isSynced = lyrics is Lyrics.Synced

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        dragHandle = null,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.95f),
        // Content fills to the edge; the nav-bar/safe-area inset would otherwise show as an
        // empty bottom band (pointless on iOS, which has no button bar). Matches BottomSheet.kt.
        contentWindowInsets = { WindowInsets(0, 0, 0, 0) },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(LYRICS_BOTTOM_SHEET_HEIGHT)
                .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Slider(
                    value = offsetSec,
                    onValueChange = { offsetSec = it },
                    valueRange = -2f..2f,
                    steps = 39,
                    enabled = isSynced,
                    thumb = {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            if (isSynced) {
                                Text(
                                    text = "",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                            }
                            SliderDefaults.Thumb(
                                interactionSource = remember { MutableInteractionSource() },
                                thumbSize = DpSize(16.dp, 16.dp),
                                enabled = isSynced,
                            )

                            if (isSynced) {
                                Text(
                                    text = formatDecimal(offsetSec.toDouble(), 1),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                            }
                        }
                    },
                    track = { sliderState ->
                        SliderDefaults.Track(
                            sliderState = sliderState,
                            thumbTrackGapSize = 0.dp,
                            trackInsideCornerSize = 0.dp,
                            drawStopIndicator = null,
                            enabled = isSynced,
                            modifier = Modifier.height(8.dp),
                        )
                    },
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onDismiss) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = stringResource(Res.string.cd_lyrics_close),
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
            when (lyrics) {
                is Lyrics.Plain -> PlainLyrics(
                    text = lyrics.text,
                    modifier = Modifier.fillMaxWidth().weight(1f),
                )

                is Lyrics.Synced -> SyncedLyrics(
                    lines = lyrics.lines,
                    livePositionFlow = livePositionFlow,
                    offsetSec = offsetSec,
                    modifier = Modifier.fillMaxWidth().weight(1f),
                )
            }
        }
    }
}

@Composable
private fun PlainLyrics(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        fontSize = 30.sp,
        lineHeight = 40.sp,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = modifier.verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 12.dp),
    )
}

@Composable
private fun SyncedLyrics(
    lines: List<LrcLine>,
    livePositionFlow: Flow<Double>?,
    offsetSec: Float = 0f,
    modifier: Modifier = Modifier,
) {
    val synced = livePositionFlow != null
    val positionSec by (livePositionFlow ?: emptyFlow()).collectAsState(initial = 0.0)
    val currentIndex = remember(lines, positionSec, offsetSec) {
        if (!synced) {
            -1
        } else {
            val ms = ((positionSec + offsetSec) * 1000).toLong()
            lines.indexOfLast { it.timeMs <= ms }
        }
    }

    val listState = rememberLazyListState()
    if (synced) {
        LaunchedEffect(currentIndex) {
            if (currentIndex in lines.indices) {
                // Center the active line: offset it up by half the viewport minus half its height.
                val info = listState.layoutInfo
                val viewport = info.viewportEndOffset - info.viewportStartOffset
                val itemSize =
                    info.visibleItemsInfo.firstOrNull { it.index == currentIndex }?.size ?: 0
                listState.animateScrollToItem(currentIndex, -(viewport / 2 - itemSize / 2))
            }
        }
    }

    LazyColumn(
        state = listState,
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 36.dp),
        verticalArrangement = Arrangement.spacedBy(36.dp),
    ) {
        itemsIndexed(lines) { index, line ->
            val active = index == currentIndex
            Text(
                text = line.text,
                fontSize = 28.sp,
                fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                color = if (active) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurface.inactive()
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

private const val LYRICS_BOTTOM_SHEET_HEIGHT = 0.8f
