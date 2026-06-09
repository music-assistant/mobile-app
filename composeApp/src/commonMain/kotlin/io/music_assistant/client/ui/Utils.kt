package io.music_assistant.client.ui

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

val MAX_DIALOG_HEIGHT = 400.dp
const val INACTIVE_ALPHA = 0.4f

fun Modifier.alphaOn(enabled: Boolean) = alpha(if (enabled) 1f else INACTIVE_ALPHA)
fun Color.alphaOn(enabled: Boolean) = copy(alpha = if (enabled) 1f else INACTIVE_ALPHA)
fun Color.inactive() = alphaOn(false)

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

const val HUNDRED = 100
const val TEN = 10
const val ONE = 1
