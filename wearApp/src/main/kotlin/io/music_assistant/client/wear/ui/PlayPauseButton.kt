// Compose layout values (button sizes, stroke widths, wave geometry) are visual design tokens.
@file:Suppress("MagicNumber")

package io.music_assistant.client.wear.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.FilledIconButton
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.IconButtonShapes
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.OutlinedIconButton
import io.music_assistant.client.wear.R
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * The play/pause button with the track position drawn around it.
 *
 * The position ring sits on the button rather than at the bezel: it keeps the one thing that
 * moves next to the one thing the user presses, and leaves the screen edge to the system.
 * The ring is scalloped so that it reads as part of the control instead of as a stray arc,
 * and so the played part is still tellable apart from the rest without relying on colour.
 *
 * [progress] is null for anything without a known length — a radio stream — and then only
 * the track is drawn.
 *
 * In [ambient] there is no ring at all: a position that can only move once a minute is not a
 * position worth drawing, and an arc of lit pixels in a fixed place is what burns a panel in.
 * The space the ring took is kept, so the transport row does not reflow when the screen dims.
 */
@Composable
fun PlayPauseButton(
    isPlaying: Boolean,
    progress: Float?,
    enabled: Boolean,
    buttonSize: Dp,
    ringSize: Dp,
    onClick: () -> Unit,
    ambient: Boolean = false,
) {
    val trackColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.25f)
    val indicatorColor = MaterialTheme.colorScheme.primary
    Box(contentAlignment = Alignment.Center) {
        if (ambient) {
            Spacer(Modifier.size(ringSize))
        } else {
            Canvas(Modifier.size(ringSize)) {
                val stroke = RING_STROKE.toPx()
                val amplitude = WAVE_AMPLITUDE.toPx()
                // Everything here is pixels: the crest of the wave, plus half the stroke, is
                // what has to fit inside the canvas, so the ring keeps clear of the button
                // beneath it.
                val radius =
                    size.minDimension / 2f - stroke / 2f - amplitude + 2f // additional margin
                drawWavyArc(radius, amplitude, FULL_CIRCLE, trackColor, stroke)
                progress?.takeIf { it > 0f }?.let {
                    drawWavyArc(radius, amplitude, FULL_CIRCLE * it, indicatorColor, stroke)
                }
            }
        }
        val glyph = @Composable {
            Icon(
                painter = painterResource(
                    if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play,
                ),
                contentDescription = stringResource(
                    if (isPlaying) R.string.cd_pause else R.string.cd_play,
                ),
                modifier = Modifier.size(buttonSize / 2),
            )
        }
        // Outlined rather than filled while dimmed, for the same reason the ring goes.
        if (ambient) {
            OutlinedIconButton(
                onClick = onClick,
                enabled = enabled,
                modifier = Modifier.size(buttonSize),
            ) { glyph() }
        } else {
            FilledIconButton(
                onClick = onClick,
                enabled = enabled,
                modifier = Modifier.size(buttonSize),
            ) { glyph() }
        }
    }
}

/**
 * Draws [sweepDegrees] of a wave that runs around the centre, starting at twelve o'clock.
 *
 * The wave count is whole, so the crest at the start of the circle is also the crest at its
 * end and the ring closes without a seam.
 */
private fun DrawScope.drawWavyArc(
    radius: Float,
    amplitude: Float,
    sweepDegrees: Float,
    color: Color,
    strokeWidth: Float,
) {
    if (sweepDegrees <= 0f) return
    val steps = (sweepDegrees / DEGREES_PER_STEP).roundToInt().coerceAtLeast(2)
    val path = Path()
    for (step in 0..steps) {
        val angle = ((sweepDegrees * step / steps) - QUARTER_TURN) * PI.toFloat() / 180f
        val waved = radius + amplitude * cos(WAVE_COUNT * angle)
        val point = Offset(
            x = center.x + waved * cos(angle),
            y = center.y + waved * sin(angle),
        )
        if (step == 0) path.moveTo(point.x, point.y) else path.lineTo(point.x, point.y)
    }
    drawPath(
        path = path,
        color = color,
        style = Stroke(
            width = strokeWidth,
            cap = StrokeCap.Round,
            join = StrokeJoin.Round,
        ),
    )
}

private val RING_STROKE = 3.5.dp
private val WAVE_AMPLITUDE = 2.5.dp
private const val WAVE_COUNT = 12f
private const val FULL_CIRCLE = 360f
private const val QUARTER_TURN = 90f
private const val DEGREES_PER_STEP = 1.5f
