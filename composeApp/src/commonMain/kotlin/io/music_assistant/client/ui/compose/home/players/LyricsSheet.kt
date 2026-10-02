package io.music_assistant.client.ui.compose.home.players

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.music_assistant.client.data.model.client.LrcLine
import io.music_assistant.client.data.model.client.Lyrics
import io.music_assistant.client.ui.compose.common.ToastHost
import io.music_assistant.client.ui.compose.common.rememberToastState
import io.music_assistant.client.ui.inactive
import io.music_assistant.client.utils.KeepScreenOn
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import musicassistantclient.composeapp.generated.resources.Res
import musicassistantclient.composeapp.generated.resources.cd_keep_screen_on_disable
import musicassistantclient.composeapp.generated.resources.cd_keep_screen_on_enable
import musicassistantclient.composeapp.generated.resources.cd_lyrics_close
import musicassistantclient.composeapp.generated.resources.cd_lyrics_offset_decrease
import musicassistantclient.composeapp.generated.resources.cd_lyrics_offset_increase
import musicassistantclient.composeapp.generated.resources.edit_audio
import musicassistantclient.composeapp.generated.resources.lyrics_screen_wake_on
import musicassistantclient.composeapp.generated.resources.night_sight_auto
import musicassistantclient.composeapp.generated.resources.night_sight_auto_off
import org.jetbrains.compose.resources.painterResource
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
    var offsetMs by remember(lyrics) { mutableStateOf(0) }
    var keepScreenOn by remember { mutableStateOf(false) }
    // The offset shifts lines against the live position, so it means nothing without both.
    val canAdjustOffset = lyrics is Lyrics.Synced && livePositionFlow != null
    // The sheet is its own window, so it hosts its own toasts above the page's ToastHost.
    val toastState = rememberToastState()
    val screenWakeOnMessage = stringResource(Res.string.lyrics_screen_wake_on)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        dragHandle = null,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.95f),
        // Content fills to the edge; the nav-bar/safe-area inset would otherwise show as an
        // empty bottom band (pointless on iOS, which has no button bar). Matches BottomSheet.kt.
        contentWindowInsets = { WindowInsets(0, 0, 0, 0) },
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(LYRICS_BOTTOM_SHEET_HEIGHT)
                .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)),
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Scoped to the sheet: leaving the composition releases the screen lock,
                // whichever way the sheet was closed.
                KeepScreenOn(enabled = keepScreenOn)
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onDismiss) {
                        Icon(
                            Icons.Default.ExpandMore,
                            contentDescription = stringResource(Res.string.cd_lyrics_close),
                            tint = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                    Box(
                        modifier = Modifier.weight(1f),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (canAdjustOffset) {
                            LyricsOffsetStepper(
                                offsetMs = offsetMs,
                                onOffsetChange = { offsetMs = it },
                            )
                        }
                    }
                    IconToggleButton(
                        checked = keepScreenOn,
                        onCheckedChange = {
                            keepScreenOn = it
                            if (it) toastState.showToast(screenWakeOnMessage)
                        },
                        colors = IconButtonDefaults.iconToggleButtonColors(
                            contentColor = MaterialTheme.colorScheme.onSurface,
                            checkedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                            checkedContentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        ),
                    ) {
                        Icon(
                            painter = painterResource(
                                if (keepScreenOn) {
                                    Res.drawable.night_sight_auto_off
                                } else {
                                    Res.drawable.night_sight_auto
                                },
                            ),
                            contentDescription = stringResource(
                                if (keepScreenOn) {
                                    Res.string.cd_keep_screen_on_disable
                                } else {
                                    Res.string.cd_keep_screen_on_enable
                                },
                            ),
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
                        offsetMs = offsetMs,
                        modifier = Modifier.fillMaxWidth().weight(1f),
                    )
                }
            }
            ToastHost(toastState = toastState)
        }
    }
}

/** `− value +` stepper for the lyrics offset; each end disables its button at the limit. */
@Composable
private fun LyricsOffsetStepper(
    offsetMs: Int,
    onOffsetChange: (Int) -> Unit,
) {
    Row(
        modifier = Modifier
            .background(
                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                shape = CircleShape,
            )
            .padding(start = 12.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painterResource(Res.drawable.edit_audio),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurface.inactive(),
            modifier = Modifier.padding(end = 16.dp).size(16.dp),
        )
        // 32 dp visually; IconButton still keeps the 48 dp touch target around it.
        IconButton(
            onClick = { onOffsetChange(offsetMs - LYRICS_OFFSET_STEP_MS) },
            enabled = offsetMs > -LYRICS_OFFSET_LIMIT_MS,
            modifier = Modifier.size(32.dp),
        ) {
            Icon(
                Icons.Default.Remove,
                contentDescription = stringResource(Res.string.cd_lyrics_offset_decrease),
                modifier = Modifier.size(18.dp),
            )
        }
        Text(
            text = "${if (offsetMs > 0) "+" else ""}$offsetMs ms",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            // Fixed width so the buttons stay put as the value's length changes.
            modifier = Modifier.width(64.dp),
        )
        IconButton(
            onClick = { onOffsetChange(offsetMs + LYRICS_OFFSET_STEP_MS) },
            enabled = offsetMs < LYRICS_OFFSET_LIMIT_MS,
            modifier = Modifier.size(32.dp),
        ) {
            Icon(
                Icons.Default.Add,
                contentDescription = stringResource(Res.string.cd_lyrics_offset_increase),
                modifier = Modifier.size(18.dp),
            )
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
    offsetMs: Int = 0,
    modifier: Modifier = Modifier,
) {
    val synced = livePositionFlow != null
    val positionSec by (livePositionFlow ?: emptyFlow()).collectAsState(initial = 0.0)
    val currentIndex = remember(lines, positionSec, offsetMs) {
        if (!synced) {
            -1
        } else {
            val ms = (positionSec * 1000).toLong() + offsetMs
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
            // An invisible bold copy sizes every line, so activating a line never
            // re-wraps it and shifts the list; only the visible copy changes weight.
            Box(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = line.text,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Transparent,
                    modifier = Modifier.fillMaxWidth().clearAndSetSemantics {},
                )
                Text(
                    text = line.text,
                    fontSize = 28.sp,
                    fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                    color = if (active) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurface.inactive()
                    },
                    modifier = Modifier.matchParentSize(),
                )
            }
        }
    }
}

private const val LYRICS_BOTTOM_SHEET_HEIGHT = 0.8f
private const val LYRICS_OFFSET_STEP_MS = 200
private const val LYRICS_OFFSET_LIMIT_MS = 2000
