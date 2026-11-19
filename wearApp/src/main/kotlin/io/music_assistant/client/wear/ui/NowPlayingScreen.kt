// Compose layout values (button sizes, spacing, padding) are visual design tokens.
@file:Suppress("MagicNumber")

package io.music_assistant.client.wear.ui

import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Queue
import androidx.compose.material.icons.rounded.Speaker
import androidx.compose.material.icons.rounded.SpeakerGroup
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.findRootCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
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
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.IconButton
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import io.music_assistant.client.ui.compose.common.icons.SkipBackIcon
import io.music_assistant.client.ui.compose.common.icons.SkipForwardIcon
import io.music_assistant.client.wear.R
import io.music_assistant.client.wear.data.WearPlayer
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
 * out of it: no cover art, no ring, no ticker and no filled buttons. The sizes do not change, so
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
    Column(
        // The top inset keeps the title clear of the clock, and covers the screen margin the
        // scaffold no longer adds: the column is centred in what is left, which on a small
        // watch is the difference between a readable title and a title drawn through the
        // time. The bottom inset is the band the rim buttons sit in, so the transport row
        // centres above them instead of against them. There is no side inset here: the text
        // keeps the margin itself, and the transport row needs the whole width (see below).
        modifier = Modifier
            .fillMaxSize()
            .padding(
                top = sizes.topInset,
                bottom = sizes.actionButton + sizes.spacing,
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        TrackLabels(state, ambient)
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
    ActionRow(
        enabled = state.isConnected,
        buttonSize = sizes.actionButton,
        selectedPlayer = state.selected,
        onOpenVolume = onOpenVolume,
        onOpenQueue = onOpenQueue,
    )
}

@Composable
private fun TrackLabels(state: WearUiState, ambient: AmbientState) {
    // A known track keeps the screen; the session status only fills an otherwise empty one,
    // so a brief reconnect does not wipe what the user was looking at.
    val title = state.nowPlaying?.title
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .roundScreenPadding(),
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
            // The weight caps the line at what is left of the row after the badge, inside the
            // row's own screen margin, so a long title scrolls across the space it is allowed
            // and never under the bezel. `fill = false` keeps a short title at its natural
            // width, so the badge and the title stay centred together as a pair.
            modifier = Modifier
                .weight(1f, fill = false)
                .scrollingTitle(enabled = !ambient.isAmbient),
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
                .roundScreenPadding(),
        )
    }
}

/**
 * Side padding that keeps a line inside a round screen at the height the line actually sits,
 * instead of inside the rectangle that contains the screen.
 *
 * [SAFE_MARGIN] is a rectangular margin, and near the top of a circle the glass is narrower
 * than the rectangle by a long way: on a 240 dp round screen the title line sits about 49 dp
 * down, where the glass spans only 23 dp to 217 dp, so a 24 dp margin leaves the text running
 * into the bezel. This measures where the line landed and insets it to the chord at its own
 * furthest edge, never giving back less than [SAFE_MARGIN], and keeps [RIM_INSET] of air
 * between the text and the glass — the same inset the rim buttons keep.
 *
 * Reading the position back is safe here: side padding does not move a one-line row up or
 * down, so the measurement cannot chase itself. A square screen keeps the flat margin.
 */
@Composable
private fun Modifier.roundScreenPadding(): Modifier {
    val isRound = LocalConfiguration.current.isScreenRound
    val density = LocalDensity.current
    var inset by remember { mutableStateOf(SAFE_MARGIN) }
    return this
        .onGloballyPositioned { coordinates ->
            if (!isRound) return@onGloballyPositioned
            val root = coordinates.findRootCoordinates()
            val width = root.size.width.toFloat()
            val height = root.size.height.toFloat()
            val radius = minOf(width, height) / 2f
            val centerY = height / 2f
            val top = coordinates.positionInRoot().y
            // The corner that sticks out furthest from the middle is the one that decides how
            // much room the line has.
            val reach = maxOf(abs(top - centerY), abs(top + coordinates.size.height - centerY))
            val halfChord = sqrt((radius * radius - reach * reach).coerceAtLeast(0f))
            val chordInset = with(density) { (width / 2f - halfChord).toDp() } + RIM_INSET
            inset = maxOf(chordInset, SAFE_MARGIN)
        }
        .padding(horizontal = inset)
}

/**
 * Scrolls a line of text past its window when it does not fit, and leaves it alone when it
 * does.
 *
 * The marquee measures the text unbounded and then clips it to the width the layout gave it,
 * so only the title moves: the badge beside it is a sibling in the row and stays where it is,
 * and the scroll happens inside the margin the row keeps from the glass. It repeats for as
 * long as the screen is up, because a title that scrolls three times and then rests truncated
 * reads as broken rather than as finished.
 *
 * Never on the always-on screen, which is meant to be still: there [enabled] is false and the
 * line falls back to the ellipsis.
 */
private fun Modifier.scrollingTitle(enabled: Boolean): Modifier =
    if (enabled) basicMarquee(iterations = Int.MAX_VALUE) else this

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
            icon = SkipBackIcon,
            contentDescription = R.string.cd_previous,
            enabled = canSkip,
            sizes = sizes,
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
            icon = SkipForwardIcon,
            contentDescription = R.string.cd_next,
            enabled = canSkip,
            sizes = sizes,
            onClick = onNext,
        )
    }
}

@Composable
private fun SkipButton(
    icon: ImageVector,
    contentDescription: Int,
    enabled: Boolean,
    sizes: ControlSizes,
    onClick: () -> Unit,
) {
    // Outlined rather than filled while dimmed: a filled button is a disc of lit pixels held
    // in one place, which is what burns a panel in. The size does not change, so the row does
    // not move when the screen dims.
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.size(sizes.sideButton),
    ) {
        Icon(
        imageVector = icon,
        contentDescription = stringResource(contentDescription),
        modifier = Modifier.size(sizes.sideIcon),
    )
    }
}

/**
 * The queue and the volume, set against the rim.
 *
 * They are the two ways off this screen, so they give their space to the controls and take
 * the edge the artwork was filling anyway. They are outlined rather than filled, on the lit
 * screen as well as the dimmed one: they sit beside the outlined transport buttons, and a
 * pair of discs at the rim would pull the eye away from the controls they frame. On the
 * always-on screen they also stay rather than leave a gap where a control was, and an
 * outline is a ring of lit pixels rather than a disc of them.
 *
 * On a round watch the bottom corners are not glass:
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
    selectedPlayer: WearPlayer?,
) {
    val colors = ButtonDefaults.outlinedButtonColors()
    RimRow(
        spacing = 56.dp,
        bottomSlack = CompactButtonDefaults.TapTargetPadding.calculateBottomPadding(),
        modifier = Modifier.fillMaxSize(),
    ) {
        CompactButton(
            onClick = onOpenQueue,
            enabled = enabled,
            modifier = Modifier.size(buttonSize),
            colors = colors,
            icon = {
                Icon(
                    imageVector = Icons.Rounded.Queue,
                    contentDescription = stringResource(R.string.cd_queue),
                    modifier = Modifier.size(buttonSize / 2),
                )
            },
        )
        CompactButton(
            onClick = onOpenVolume,
            enabled = enabled,
            modifier = Modifier.size(buttonSize),
            colors = colors,
            icon = {
                Icon(
                    imageVector = if (selectedPlayer?.isGroup == true) Icons.Rounded.SpeakerGroup else Icons.Rounded.Speaker,
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
