// Compose layout values (button sizes, stroke widths, wave geometry) are visual design tokens.
@file:Suppress("MagicNumber")

package io.music_assistant.client.wear.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.FilledIconButton
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.IconButtonShapes
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.OutlinedIconButton
import androidx.wear.compose.material3.onehandedgesture.OneHandedGestureAction
import androidx.wear.compose.material3.onehandedgesture.OneHandedGestureClickIndicator
import androidx.wear.compose.material3.onehandedgesture.OneHandedGestureClickIndicatorState
import androidx.wear.compose.material3.onehandedgesture.oneHandedGesture
import androidx.wear.compose.material3.onehandedgesture.rememberOneHandedGestureConfiguration
import io.music_assistant.client.ui.compose.common.icons.PauseIcon
import io.music_assistant.client.ui.compose.common.icons.PlayIcon
import io.music_assistant.client.wear.R
import kotlinx.coroutines.launch
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
 * The button carries the same wave (see [WavyButtonShape]), so the two are one control with a
 * gap in it rather than a round button with a scalloped ring loose around it.
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
                    size.minDimension / 2f - stroke / 2f - amplitude // additional margin
                drawWavyArc(radius, amplitude, FULL_CIRCLE, trackColor, stroke)
                progress?.takeIf { it > 0f }?.let {
                    drawWavyArc(radius, amplitude, FULL_CIRCLE * it, indicatorColor, stroke)
                }
            }
        }
        val label = stringResource(if (isPlaying) R.string.cd_pause else R.string.cd_play)
        // The double pinch (the one-handed primary action, Wear OS 7 and newer) plays and
        // pauses too. On an older watch the library does nothing.
        val gesture = rememberOneHandedGestureConfiguration(action = OneHandedGestureAction.Primary)
        val indicatorState = remember { OneHandedGestureClickIndicatorState() }
        val interactionSource = remember { MutableInteractionSource() }
        val scope = rememberCoroutineScope()
        val buttonModifier = Modifier
            .size(buttonSize)
            .oneHandedGesture(
                gestureConfiguration = gesture,
                interactionSource = interactionSource,
                onGestureLabel = label,
                onGestureAvailable = { scope.launch { indicatorState.showIndicator() } },
                onGesture = { if (enabled) onClick() },
            )
        val glyph = @Composable {
            OneHandedGestureClickIndicator(gestureConfiguration = gesture, state = indicatorState) {
                Icon(
                    imageVector = if (isPlaying) PauseIcon else PlayIcon,
                    contentDescription = label,
                    modifier = Modifier.size(buttonSize / 2),
                )
            }
        }
        // Outlined rather than filled while dimmed, for the same reason the ring goes. The
        // outline is drawn at [RING_STROKE], the width of the ring it stands in for, so the
        // dimmed button reads as the same weight of line the lit screen had around it.
        if (ambient) {
            OutlinedIconButton(
                onClick = onClick,
                enabled = enabled,
                modifier = buttonModifier,
                interactionSource = interactionSource,
                shapes = IconButtonShapes(WavyButtonShape),
                border = ButtonDefaults.outlinedButtonBorder(
                    enabled = enabled,
                    borderWidth = RING_STROKE,
                ),
            ) { glyph() }
        } else {
            FilledIconButton(
                onClick = onClick,
                enabled = enabled,
                modifier = buttonModifier,
                interactionSource = interactionSource,
                shapes = IconButtonShapes(WavyButtonShape),
            ) { glyph() }
        }
    }
}

/**
 * The play button's own outline: the ring's wave, inscribed in the button's bounds.
 *
 * It is built from [wavyPath] like the ring is, with the ring's amplitude and the ring's
 * phase, so the distance from the button's edge to the ring is the same the whole way round
 * and the button reads as the inside edge of the ring. The crests sit on the bounds, so the
 * size of the gap is whatever `ControlSizes` leaves between `playButton` and `ring`.
 *
 * The outline is a path rather than a corner shape, so the button cannot cast a shadow. It
 * does not: a Wear icon button is a tonal surface.
 */
private object WavyButtonShape : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Outline {
        val amplitude = with(density) { WAVE_AMPLITUDE.toPx() }
        val path = wavyPath(
            center = Offset(size.width / 2f, size.height / 2f),
            radius = size.minDimension / 2f - amplitude,
            amplitude = amplitude,
            sweepDegrees = FULL_CIRCLE,
        ).apply { close() }
        return Outline.Generic(path)
    }
}

/** Strokes [sweepDegrees] of the wave that [wavyPath] traces, starting at twelve o'clock. */
private fun DrawScope.drawWavyArc(
    radius: Float,
    amplitude: Float,
    sweepDegrees: Float,
    color: Color,
    strokeWidth: Float,
) {
    if (sweepDegrees <= 0f) return
    drawPath(
        path = wavyPath(center, radius, amplitude, sweepDegrees),
        color = color,
        style = Stroke(
            width = strokeWidth,
            cap = StrokeCap.Round,
            join = StrokeJoin.Round,
        ),
    )
}

/**
 * Traces [sweepDegrees] of a wave that runs around [center], starting at twelve o'clock.
 *
 * The path swings [amplitude] either side of [radius], so a crest reaches [radius] +
 * [amplitude]. The wave count is whole, so the crest at the start of the circle is also the
 * crest at its end and the ring closes without a seam.
 *
 * Both the ring and the button under it are traced here, which is what keeps their twelve
 * crests in the same twelve places.
 */
private fun wavyPath(
    center: Offset,
    radius: Float,
    amplitude: Float,
    sweepDegrees: Float,
): Path {
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
    return path
}

private val RING_STROKE = 3.5.dp
private val WAVE_AMPLITUDE = 1.5.dp
private const val WAVE_COUNT = 12f
private const val FULL_CIRCLE = 360f
private const val QUARTER_TURN = 90f
private const val DEGREES_PER_STEP = 1.5f
