package io.music_assistant.client.ui.compose.home.players

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.dp
import kotlin.math.max

/** The height of the waveform track. The slider keeps its 48 dp touch height, so the layout does not move. */
internal val WAVEFORM_HEIGHT = 32.dp

// A 2 dp bar and a 1 dp gap, like the web frontend.
private val BAR_WIDTH = 2.dp
private val BAR_PITCH = 3.dp

// A silent part stays visible as a dot.
private val MIN_BAR_HEIGHT = 2.dp

/**
 * The seek bar track of a track with a waveform: one bar for each [BAR_PITCH], centered. A bar
 * before [playedFraction] is [playedColor], a bar before [bufferedFraction] is [bufferedColor],
 * and the other bars are [restColor]. [playedFraction] is read in the draw phase only, so the
 * position tick and a drag redraw the bars without a recomposition.
 */
@Composable
internal fun WaveformTrack(
    bins: List<Float>,
    playedFraction: () -> Float,
    bufferedFraction: Float,
    playedColor: Color,
    bufferedColor: Color,
    restColor: Color,
    modifier: Modifier = Modifier,
) {
    Spacer(
        modifier = modifier
            .fillMaxWidth()
            .height(WAVEFORM_HEIGHT)
            .drawWithCache {
                val barWidth = BAR_WIDTH.toPx()
                val pitch = BAR_PITCH.toPx()
                val minHeight = MIN_BAR_HEIGHT.toPx()
                // Only whole bars, so the last bar does not pass the end of the track.
                val barCount = if (size.width < barWidth) 0 else ((size.width - barWidth) / pitch).toInt() + 1
                val peaks = waveformPeaks(bins, barCount)
                onDrawBehind {
                    val playedX = playedFraction().coerceIn(0f, 1f) * size.width
                    val bufferedX = bufferedFraction.coerceIn(0f, 1f) * size.width
                    // The round caps make the ends of a bar, so the line is shorter by the cap radius.
                    val capRadius = barWidth / 2
                    peaks.forEachIndexed { index, peak ->
                        val x = index * pitch + capRadius
                        val halfHeight = max(minHeight, peak.coerceIn(0f, 1f) * size.height) / 2
                        val center = size.height / 2
                        val halfLine = max(0f, halfHeight - capRadius)
                        drawLine(
                            color = when {
                                x <= playedX -> playedColor
                                x <= bufferedX -> bufferedColor
                                else -> restColor
                            },
                            start = Offset(x, center - halfLine),
                            end = Offset(x, center + halfLine),
                            strokeWidth = barWidth,
                            cap = StrokeCap.Round,
                        )
                    }
                }
            },
    )
}

/**
 * One peak for each of [barCount] bars: the maximum of the [bins] that the bar covers. The
 * maximum keeps the transients that an average would flatten. With more bars than bins, a bin
 * fills several bars.
 */
internal fun waveformPeaks(bins: List<Float>, barCount: Int): FloatArray {
    if (barCount <= 0 || bins.isEmpty()) return FloatArray(0)
    return FloatArray(barCount) { bar ->
        val start = bar * bins.size / barCount
        val end = max(start + 1, (bar + 1) * bins.size / barCount)
        (start until end).maxOf { bins[it] }
    }
}
