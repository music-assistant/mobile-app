// Drawing values mirror the web frontend's SVG/CSS, in its 24x24 viewport units.
@file:Suppress("MagicNumber")

package io.music_assistant.client.ui.compose.common.icons

import androidx.compose.foundation.Canvas
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Path
import kotlin.math.PI
import kotlin.math.cos

private const val RING_RADIUS = 7f

/** Spark length as a share of a ring (`stroke-dasharray: 4 39.98` on a 43.98 circumference). */
private const val CROSSFADE_SPARK_FRACTION = 4f / 43.98f

/** Share of the loop over which a spark fades in at the start and out at the end. */
private const val SPARK_FADE_EDGE = 0.15f

/** Spark alpha at the loop seam, where it is faded furthest out. */
private const val SPARK_MIN_ALPHA = 0.15f

/**
 * Crossfade glyph: two overlapping outlined rings, the web frontend's crossfade shape.
 *
 * When [smart] (the server reports smart fades active), the rings dim, a spark circles each
 * ring half a loop apart, and the whole glyph pulses with a soft glow, matching the web
 * frontend's `CrossfadeIcon.vue`. Otherwise it is the plain two-ring glyph. Like the web
 * client, standard crossfade does not animate; only smart crossfade does.
 *
 * Colored from [LocalContentColor], like `Icon`. The caller sizes it.
 */
@Composable
fun AnimatedCrossfadeIcon(smart: Boolean, modifier: Modifier = Modifier) {
    val color = LocalContentColor.current
    val rings = remember { listOf(ring(9f, 9f), ring(15f, 15f)) }
    val ringLength = remember(rings) { rings.first().measuredLength() }
    // Only a smart glyph runs the animation clock.
    val progress = if (smart) rememberSparkProgress() else null

    Canvas(modifier) {
        inGlyphViewport {
            val p = progress?.value
            p?.let {
                // Whole-glyph pulse, brightest mid-loop, like the web's pulsing drop-shadow.
                val pulse = (1f - cos(2f * PI.toFloat() * it)) / 2f
                rings.forEach { ring ->
                    drawPath(ring, color, alpha = pulse * GLOW_ALPHA, style = GlowStroke)
                }
            }
            rings.forEach { ring ->
                drawPath(
                    path = ring,
                    color = color,
                    alpha = if (p != null) DIMMED_GLYPH_ALPHA else 1f,
                    style = GlyphStroke,
                )
            }
            p?.let {
                rings.forEachIndexed { index, ring ->
                    // The second ring's spark runs half a loop ahead of the first.
                    val ringProgress = (it + index * 0.5f) % 1f
                    drawSpark(
                        path = ring,
                        length = ringLength,
                        dashFraction = CROSSFADE_SPARK_FRACTION,
                        progress = ringProgress,
                        color = color,
                        alpha = sparkAlpha(ringProgress),
                    )
                }
            }
        }
    }
}

private fun ring(cx: Float, cy: Float): Path = Path().apply {
    addOval(Rect(center = Offset(cx, cy), radius = RING_RADIUS))
}

/** Fades the spark in over the first [SPARK_FADE_EDGE] of its loop and out over the last. */
private fun sparkAlpha(progress: Float): Float {
    val edgeDistance = minOf(progress, 1f - progress) / SPARK_FADE_EDGE
    return SPARK_MIN_ALPHA + (1f - SPARK_MIN_ALPHA) * edgeDistance.coerceAtMost(1f)
}
