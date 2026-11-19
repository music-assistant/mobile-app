// Compose layout values (button sizes, spacing, padding) are visual design tokens.
@file:Suppress("MagicNumber")

package io.music_assistant.client.wear.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.CompactButton
import androidx.wear.compose.material3.CompactButtonDefaults
import androidx.wear.compose.material3.FilledTonalIconButton
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.IconButtonShapes
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.OutlinedIconButton
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import io.music_assistant.client.wear.R
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * The watch's main screen: cover art behind the transport controls, the track position drawn
 * around the play button, and the two ways out — the queue and the volume, which is also
 * where the output is picked.
 *
 * Everything sits in one centred column rather than a scrolling list: a media control screen
 * that moves under the thumb while the user reaches for pause is worse than one that fits.
 *
 * The always-on screen is this screen with everything an always-on screen must not do taken
 * out of it: no cover art, no ring, no ticker, no filled buttons, and the whole column
 * shifted a few dp each minute where the panel asks for it. The sizes do not change, so
 * dimming and waking do not reflow what the user was looking at.
 */
@Composable
fun NowPlayingScreen(
    state: WearUiState,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onOpenVolume: () -> Unit,
    onOpenQueue: () -> Unit,
    ambient: AmbientState = LocalAmbientState.current,
) {
    // The scaffold's own content padding is 5.2% of the screen on every side, which is the
    // safe area for text and is given up here: the artwork is meant to bleed to the glass and
    // the rim buttons are meant to touch it. The column below keeps that margin itself.
    ScreenScaffold(contentPadding = PaddingValues(0.dp)) {
        Box(Modifier.fillMaxSize()) {
            // A full-screen image is the one thing an OLED watch must not hold up for an
            // hour, and the crossfade behind it would animate a screen meant to be still.
            if (!ambient.isAmbient) {
                ArtworkBackground(state.artwork, Modifier.fillMaxSize())
            }
            NowPlayingContent(
                state = state,
                ambient = ambient,
                onPlayPause = onPlayPause,
                onNext = onNext,
                onPrevious = onPrevious,
                onOpenVolume = onOpenVolume,
                onOpenQueue = onOpenQueue,
            )
        }
    }
}

@Composable
private fun NowPlayingContent(
    state: WearUiState,
    ambient: AmbientState,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onOpenVolume: () -> Unit,
    onOpenQueue: () -> Unit,
) {
    val sizes = controlSizes()
    val shift = ambient.burnInOffset
    Column(
        // The top inset keeps the title clear of the clock, and covers the screen margin the
        // scaffold no longer adds: the column is centred in what is left, which on a small
        // watch is the difference between a readable title and a title drawn through the
        // time. The bottom inset is the band the rim buttons sit in, so the transport row
        // centres above them instead of against them. There is no side inset here: the text
        // keeps the margin itself, and the transport row needs the whole width (see below).
        modifier = Modifier
            .fillMaxSize()
            .offset(x = shift.x, y = shift.y)
            .padding(
                top = sizes.topInset,
                bottom = sizes.actionButton + sizes.spacing,
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        TrackLabels(state)
        Spacer(Modifier.height(sizes.spacing))
        TransportControls(
            state = state,
            sizes = sizes,
            ambient = ambient,
            onPlayPause = onPlayPause,
            onNext = onNext,
            onPrevious = onPrevious,
        )
    }
    // Touch does not reach the app while the screen is dimmed, so a pair of filled buttons
    // at the rim would be lit for nothing.
    if (!ambient.isAmbient) {
        ActionRow(
            enabled = state.isConnected,
            buttonSize = sizes.actionButton,
            onOpenVolume = onOpenVolume,
            onOpenQueue = onOpenQueue,
        )
    }
}

@Composable
private fun TrackLabels(state: WearUiState) {
    // A known track keeps the screen; the session status only fills an otherwise empty one,
    // so a brief reconnect does not wipe what the user was looking at.
    val title = state.nowPlaying?.title
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = SAFE_MARGIN),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // The badge marks the line as a track rather than as a status, so it is left off
        // "Connecting…" and "Nothing playing".
        title?.let {
            AppBadge()
            Spacer(Modifier.width(4.dp))
        }
        Text(
            text = title
                ?: state.session.statusLabel()
                ?: stringResource(R.string.nothing_playing),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1f, fill = false),
        )
    }
    val subtitle = state.nowPlaying?.artist ?: state.session.statusDetail()
    subtitle?.let {
        Text(
            text = it,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = SAFE_MARGIN),
        )
    }
}

/**
 * The app icon, small, in front of the track title. It is the launcher icon's monochrome
 * layer in one accent colour: the brand cyan belongs to the launcher, not to a screen that
 * takes its colours from the cover.
 */
@Composable
private fun AppBadge() {
    Icon(
        painter = painterResource(R.drawable.ic_launcher_foreground),
        contentDescription = null,
        tint = MaterialTheme.colorScheme.primary,
        // The drawable keeps its glyph inside the adaptive icon's safe zone, which is 41% of
        // the canvas. Drawing it that much larger puts the glyph itself at the badge's size
        // instead of leaving most of the badge empty.
        modifier = Modifier
            .size(BADGE_SIZE)
            .scale(1f / LAUNCHER_SAFE_ZONE),
    )
}

@Composable
private fun TransportControls(
    state: WearUiState,
    sizes: ControlSizes,
    ambient: AmbientState,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
) {
    val enabled = state.isConnected && state.selected != null
    val canSkip = enabled && state.selected?.canNextPrevious != false
    // The ticker behind the ring runs twice a second. An always-on screen has no ring to
    // move and no business waking the CPU for one, so it is never started.
    val progress = if (ambient.isAmbient) null else rememberLiveProgress(state.nowPlaying)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(sizes.gap),
    ) {
        SkipButton(
            icon = R.drawable.ic_skip_previous,
            contentDescription = R.string.cd_previous,
            enabled = canSkip,
            sizes = sizes,
            ambient = ambient,
            onClick = onPrevious,
        )
        PlayPauseButton(
            isPlaying = state.nowPlaying?.isPlaying == true,
            progress = progress,
            enabled = enabled,
            buttonSize = sizes.playButton,
            ringSize = sizes.ring,
            ambient = ambient.isAmbient,
            onClick = onPlayPause,
        )
        SkipButton(
            icon = R.drawable.ic_skip_next,
            contentDescription = R.string.cd_next,
            enabled = canSkip,
            sizes = sizes,
            ambient = ambient,
            onClick = onNext,
        )
    }
}

@Composable
private fun SkipButton(
    icon: Int,
    contentDescription: Int,
    enabled: Boolean,
    sizes: ControlSizes,
    ambient: AmbientState,
    onClick: () -> Unit,
) {
    val glyph = @Composable {
        Icon(
            painter = painterResource(icon),
            contentDescription = stringResource(contentDescription),
            modifier = Modifier.size(sizes.sideIcon),
        )
    }
    // Outlined rather than filled while dimmed: a filled button is a disc of lit pixels held
    // in one place, which is what burns a panel in. The size does not change, so the row does
    // not move when the screen dims.
    if (ambient.isAmbient) {
        OutlinedIconButton(
            onClick = onClick,
            enabled = enabled,
            modifier = Modifier.size(sizes.sideButton),
        ) { glyph() }
    } else {
        FilledTonalIconButton(
            onClick = onClick,
            enabled = enabled,
            modifier = Modifier.size(sizes.sideButton),
        ) { glyph() }
    }
}

/**
 * The queue and the volume, set against the rim.
 *
 * They are the two ways off this screen, so they give their space to the controls and take
 * the edge the artwork was filling anyway. On a round watch the bottom corners are not glass:
 * each button is placed so that its outer bottom corner rides a circle just inside the bezel,
 * which puts the pair as low as the screen allows and lets a wider button ride higher instead
 * of running off the edge.
 */
@Composable
private fun ActionRow(
    enabled: Boolean,
    buttonSize: Dp,
    onOpenVolume: () -> Unit,
    onOpenQueue: () -> Unit,
) {
    RimRow(
        spacing = 78.dp,
        bottomSlack = CompactButtonDefaults.TapTargetPadding.calculateBottomPadding(),
        modifier = Modifier.fillMaxSize(),
    ) {
        CompactButton(
            onClick = onOpenQueue,
            enabled = enabled,
            modifier = Modifier.size(buttonSize),
            colors = ButtonDefaults.filledTonalButtonColors(),
            icon = {
                Icon(
                    painter = painterResource(R.drawable.ic_queue),
                    contentDescription = stringResource(R.string.cd_queue),
                    modifier = Modifier.size(buttonSize / 2),
                )
            },
        )
        CompactButton(
            onClick = onOpenVolume,
            enabled = enabled,
            modifier = Modifier.size(buttonSize),
            colors = ButtonDefaults.filledTonalButtonColors(),
            icon = {
                Icon(
                    painter = painterResource(R.drawable.ic_speaker),
                    contentDescription = stringResource(R.string.cd_volume),
                    modifier = Modifier.size(CompactButtonDefaults.ExtraSmallIconSize),
                )
            },
        )
    }
}

/**
 * Lays [content] out as a row along the bottom edge, as low as the screen's shape allows.
 *
 * A round screen gets each child's outer bottom corner on a circle one [RIM_INSET] inside the
 * bezel; a square one gets that inset from the bottom. The row is centred horizontally and
 * measured against the whole screen, so it must fill it.
 *
 * [bottomSlack] is the transparent padding a child carries under its ink — a Wear button pads
 * itself out to a 48 dp tap target — which is placed past the rim rather than against it, or
 * the buttons would sit a tap target's worth of nothing above the edge.
 */
@Composable
private fun RimRow(
    spacing: Dp,
    bottomSlack: Dp,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val isRound = LocalConfiguration.current.isScreenRound
    Layout(content = content, modifier = modifier) { measurables, constraints ->
        val gap = spacing.roundToPx()
        val inset = RIM_INSET.roundToPx()
        val slack = bottomSlack.roundToPx()
        val children = measurables.map {
            it.measure(Constraints(maxWidth = constraints.maxWidth, maxHeight = constraints.maxHeight))
        }
        val width = constraints.maxWidth
        val height = constraints.maxHeight
        val rowWidth = children.sumOf { it.width } + gap * (children.size - 1).coerceAtLeast(0)
        val centerX = width / 2f
        val radius = minOf(width, height) / 2f - inset
        layout(width, height) {
            var x = ((width - rowWidth) / 2f).roundToInt()
            children.forEach { child ->
                val corner = maxOf(abs(x - centerX), abs(x + child.width - centerX))
                val bottom = if (isRound) {
                    height / 2f + sqrt((radius * radius - corner * corner).coerceAtLeast(0f))
                } else {
                    (height - inset).toFloat()
                }
                // [bottom] is where the child's ink ends; the slack it carries below that goes
                // past the rim, where there is nothing to draw on anyway.
                child.place(x, (bottom + slack - child.height).roundToInt())
                x += child.width + gap
            }
        }
    }
}

/**
 * The margin the labels keep from the glass: the 5.2% of the screen that Wear's own
 * components leave, at the two screen widths this app sizes itself for, plus the 12 dp the
 * column always had.
 *
 * It is the text's margin and not the column's. The transport row is as wide as the play
 * button's position ring, not as its button — 164 dp at the small sizes, 202 dp at the large
 * ones — and a 24 dp inset on each side left a 192 dp screen 20 dp short of that, which
 * [ControlSizes.fitTo] explains the look of. Giving the row the whole width buys back more
 * than the shortfall at every size this app runs at, so the scaling there stays a fallback.
 */
private val SAFE_MARGIN = 24.dp
private val BADGE_SIZE = 16.dp
private const val LAUNCHER_SAFE_ZONE = 0.41f
