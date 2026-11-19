// Compose layout values (button sizes, insets, spacing) are visual design tokens.
@file:Suppress("MagicNumber")

package io.music_assistant.client.wear.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The sizes the now-playing screen draws its controls at, which a small watch cannot wear at
 * full size.
 */
internal data class ControlSizes(
    val playButton: Dp,
    val ring: Dp,
    val sideButton: Dp,
    val sideIcon: Dp,
    /** The gap between the three transport buttons. */
    val gap: Dp,
    val actionButton: Dp,
    val topInset: Dp,
    /** The vertical gap between the labels and the transport row. */
    val spacing: Dp,
) {
    /**
     * What the transport row asks for across: both skip buttons, the gaps, and the play
     * button's position ring — which is what the middle of the row actually measures,
     * because the ring is wider than the button it is drawn around.
     */
    val transportWidth: Dp = ring + (gap + sideButton) * 2

    /**
     * The same sizes, shrunk until the transport row fits in [available].
     *
     * A Row hands its children space in source order, so a row that does not fit takes the
     * whole shortfall out of its last child: the next button comes back narrower than it is
     * tall, and an icon button is a 50% rounded corner, so it draws as a pill beside a round
     * previous button. One scale factor across every transport size keeps the buttons
     * circular and the row symmetric about the play button — a row that is a little small is
     * read as a size, while a row with one pill in it is read as a bug.
     *
     * The rim button and the insets do not scale. They are measured against the screen's
     * edges and against the clock, neither of which the width of this row has anything to
     * say about.
     */
    fun fitTo(available: Dp): ControlSizes {
        val scale = available / transportWidth
        return if (scale >= 1f) {
            this
        } else {
            copy(
                playButton = playButton * scale,
                ring = ring * scale,
                sideButton = sideButton * scale,
                sideIcon = sideIcon * scale,
                gap = gap * scale,
            )
        }
    }
}

/**
 * The sizes for this watch: one of two sets, then shrunk to fit if even the smaller set is
 * too wide for the screen.
 *
 * The row is measured against the whole width because the column around it no longer insets
 * it — the text keeps that margin itself (see `SAFE_MARGIN`) — less the rim the round screen
 * takes out of the corners.
 */
@Composable
internal fun controlSizes(): ControlSizes {
    val screenWidth = LocalConfiguration.current.screenWidthDp
    return remember(screenWidth) {
        val base = if (screenWidth < LARGE_SCREEN_WIDTH_DP) SMALL_SCREEN_SIZES else LARGE_SCREEN_SIZES
        base.fitTo(screenWidth.dp - RIM_INSET * 2)
    }
}

internal val SMALL_SCREEN_SIZES = ControlSizes(
    playButton = 48.dp,
    ring = 68.dp,
    sideButton = 40.dp,
    sideIcon = 20.dp,
    gap = 8.dp,
    actionButton = 44.dp,
    topInset = 16.dp,
    spacing = 8.dp,
)

internal val LARGE_SCREEN_SIZES = ControlSizes(
    playButton = 64.dp,
    ring = 82.dp,
    sideButton = 52.dp,
    sideIcon = 26.dp,
    gap = 8.dp,
    actionButton = 44.dp,
    topInset = 16.dp,
    spacing = 8.dp,
)

/** The width above which Wear's own components stop using their small-screen sizes. */
internal const val LARGE_SCREEN_WIDTH_DP = 225

/** How far inside the bezel the controls keep, so a round screen does not clip a corner. */
internal val RIM_INSET = 6.dp
