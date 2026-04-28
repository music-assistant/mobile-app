package io.music_assistant.client.ui.compose.common.painters

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.painter.Painter
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.sin

class WaveformPainter(
    private val waveColor: Color,
    private val thickness: Float = 4f,
    private val baseFrequency: Float = 16f,
    private val baseAmplitudeFactor: Float = 0.8f,
    private val verticalOffset: Float = 0.0f,
) : Painter() {
    // Cache for the calculated path points
    private var cachedSize: Size? = null
    private var cachedPoints: List<Pair<Offset, Color>>? = null

    override val intrinsicSize: Size = Size.Unspecified

    override fun DrawScope.onDraw() {
        if (size.width <= 0f || size.height <= 0f) return

        // Check if we need to recalculate (size changed)
        val cache = cachedPoints
        val points = if (cachedSize == size && cache != null) {
            cache
        } else {
            calculateWaveform(size).also {
                cachedSize = size
                cachedPoints = it
            }
        }

        // Draw the cached points
        for (i in 1 until points.size) {
            val (prevPoint, color) = points[i - 1]
            val (currPoint, _) = points[i]
            drawLine(
                color = color,
                start = prevPoint,
                end = currPoint,
                strokeWidth = thickness,
            )
        }
    }

    private fun calculateWaveform(size: Size): List<Pair<Offset, Color>> {
        val points = mutableListOf<Pair<Offset, Color>>()
        val centerLine = size.height * (0.5f + verticalOffset)

        var phase = 0f
        val stepSize = 4 // Increased from 2 to 4 for better performance

        for (x in 0..size.width.toInt() step stepSize) {
            val normalizedX = x.toFloat() / size.width

            // Create an envelope that peaks only at the exact center
            val distanceFromCenter = abs(normalizedX - 0.5f) * 2f
            val envelopeCurve = exp(-distanceFromCenter * distanceFromCenter * 8f)
            val envelope = 0.1f + envelopeCurve * 0.9f

            // Vary amplitude based on position
            val amplitude = size.height * baseAmplitudeFactor * envelope

            // Vary frequency based on position
            val frequency = baseFrequency * (0.5f + envelope * 0.5f)

            // Accumulate phase for smooth transitions
            if (x > 0) {
                phase += frequency * 2 * PI.toFloat() / size.width * stepSize
            }

            // Calculate y using accumulated phase
            val y = centerLine + amplitude * sin(phase)

            // Use the same color for all points to reduce allocations
            // The visual difference is minimal but performance is better
            points.add(Offset(x.toFloat(), y) to waveColor.copy(alpha = envelope))
        }

        return points
    }
}

@Composable
fun rememberWaveformPainter(
    color: Color,
) = remember(color) {
    WaveformPainter(
        waveColor = color,
        thickness = 3f,
    )
}
