package io.music_assistant.client.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

val MAX_DIALOG_HEIGHT = 400.dp
const val INACTIVE_ALPHA = 0.4f
private const val LUMINANCE_MIDPOINT = 0.5f

fun Modifier.alphaOn(enabled: Boolean) = alpha(if (enabled) 1f else INACTIVE_ALPHA)
fun Color.alphaOn(enabled: Boolean) = copy(alpha = if (enabled) 1f else INACTIVE_ALPHA)
fun Color.inactive() = alphaOn(false)

/** Black or white, whichever stays legible on top of this (opaque) color. */
fun Color.contentColorByLuminance(): Color =
    if (luminance() > LUMINANCE_MIDPOINT) Color.Black else Color.White

// Soft horizontal fade-out at the left/right edges — for marquee text that should
// dissolve at the boundaries instead of hard-cutting. Offscreen compositing is required
// so the DstIn gradient masks only this content, not whatever is drawn behind it.
fun Modifier.fadingEdges(edgeWidth: Dp = 24.dp): Modifier = this
    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    .drawWithContent {
        drawContent()
        val w = edgeWidth.toPx().coerceAtMost(size.width / 2f)
        drawRect(
            brush = Brush.horizontalGradient(
                0f to Color.Transparent,
                (w / size.width) to Color.Black,
            ),
            blendMode = BlendMode.DstIn,
        )
        drawRect(
            brush = Brush.horizontalGradient(
                ((size.width - w) / size.width) to Color.Black,
                1f to Color.Transparent,
            ),
            blendMode = BlendMode.DstIn,
        )
    }

/**
 * Cancels the given [padding] on left/right/top so a child (e.g. a colored header) drawn inside a
 * padded parent — like a [androidx.compose.foundation.lazy.grid.LazyVerticalGrid] item with
 * `contentPadding` — bleeds to the parent's real edges instead of stopping at the inset. Bottom is
 * intentionally left untouched. Pass the SAME padding the parent applied so the child re-aligns to
 * the true top-left.
 */
fun Modifier.fullBleed(padding: PaddingValues): Modifier = layout { measurable, constraints ->
    val left = padding.calculateLeftPadding(layoutDirection).roundToPx()
    val right = padding.calculateRightPadding(layoutDirection).roundToPx()
    val top = padding.calculateTopPadding().roundToPx()
    val placeable = measurable.measure(
        constraints.copy(
            minWidth = constraints.minWidth + left + right,
            maxWidth = constraints.maxWidth + left + right,
        ),
    )
    // Report the ORIGINAL (un-expanded) width — not the widened placeable's. Reporting the wider
    // size makes a LazyGrid full-span item overflow its slot on the trailing edge, which the grid
    // then clips (leaving an un-bled stripe on the right); the leading edge survives only because
    // the negative placement leaks into the left padding. Keeping the reported width at the slot
    // size lets the wider placeable draw past BOTH horizontal edges symmetrically.
    layout(
        (placeable.width - left - right).coerceAtLeast(0),
        (placeable.height - top).coerceAtLeast(0),
    ) {
        placeable.place(-left, -top)
    }
}

const val HUNDRED = 100
const val TEN = 10
const val ONE = 1
