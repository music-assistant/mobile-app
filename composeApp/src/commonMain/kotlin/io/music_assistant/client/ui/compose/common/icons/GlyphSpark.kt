// Drawing values mirror the web frontend's SVG/CSS, in its 24x24 viewport units.
@file:Suppress("MagicNumber")

package io.music_assistant.client.ui.compose.common.icons

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale

/*
 * Shared plumbing for the animated badge glyphs ([AnimatedAutoplayIcon],
 * [AnimatedCrossfadeIcon]): a glowing dash, the "spark", that loops along a stroked path.
 * Ported from the web frontend's `AutoplayIcon.vue` / `CrossfadeIcon.vue`, which animate
 * `stroke-dashoffset` over 2.4s.
 */

/** One full loop of a spark, the web frontend's CSS animation duration. */
private const val SPARK_PERIOD_MS = 2400

/** Viewport the glyphs are drawn in: the 24x24 box of their SVG sources. */
private const val GLYPH_VIEWPORT = 24f

/** Alpha of the base glyph while a spark runs over it (`opacity: 0.4` on the web). */
internal const val DIMMED_GLYPH_ALPHA = 0.4f

/**
 * Stand-in for the web's `drop-shadow(0 0 3px currentColor)`: a wide, faint under-stroke.
 * A real blur would need a platform-specific render effect; at badge size this reads the same.
 */
internal const val GLOW_ALPHA = 0.3f

internal val GlyphStroke = Stroke(width = 2f, cap = StrokeCap.Round, join = StrokeJoin.Round)

internal val GlowStroke = Stroke(width = 5f, cap = StrokeCap.Round, join = StrokeJoin.Round)

/** Loop progress in `[0, 1)`. Read it inside a draw block so a frame only redraws. */
@Composable
internal fun rememberSparkProgress(): State<Float> =
    rememberInfiniteTransition(label = "glyphSpark").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(SPARK_PERIOD_MS, easing = LinearEasing)),
        label = "glyphSparkProgress",
    )

internal fun Path.measuredLength(): Float = PathMeasure().apply { setPath(this@measuredLength, false) }.length

/** Scales the 24x24 glyph viewport onto this canvas, so paths use SVG coordinates. */
internal fun DrawScope.inGlyphViewport(block: DrawScope.() -> Unit) =
    scale(scale = size.minDimension / GLYPH_VIEWPORT, pivot = Offset.Zero, block = block)

/**
 * Draws a spark [dashFraction] of [length] long, [progress] of the way along [path], over a
 * glow. The dash phase behaves like SVG's `stroke-dashoffset`: as it shrinks from [length]
 * to zero, the dash travels forward along the path.
 */
internal fun DrawScope.drawSpark(
    path: Path,
    length: Float,
    dashFraction: Float,
    progress: Float,
    color: Color,
    alpha: Float = 1f,
) {
    val dash = length * dashFraction
    val effect = PathEffect.dashPathEffect(
        intervals = floatArrayOf(dash, length - dash),
        phase = length * (1f - progress),
    )
    drawPath(
        path = path,
        color = color,
        alpha = alpha * GLOW_ALPHA,
        style = Stroke(width = GlowStroke.width, cap = StrokeCap.Round, pathEffect = effect),
    )
    drawPath(
        path = path,
        color = color,
        alpha = alpha,
        style = Stroke(width = GlyphStroke.width, cap = StrokeCap.Round, pathEffect = effect),
    )
}
