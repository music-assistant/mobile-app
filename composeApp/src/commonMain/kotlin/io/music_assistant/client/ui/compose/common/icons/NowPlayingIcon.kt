package io.music_assistant.client.ui.compose.common.icons

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

private const val BAR_COUNT = 4
private const val MIN_SCALE = 0.25f
private const val MAX_SCALE = 1f
private const val ANIMATION_DURATION_MS = 1000
private const val DELAY_STEP_MS = 200
private val BAR_DELAYS = List(BAR_COUNT) { it * DELAY_STEP_MS }

@Composable
fun NowPlayingIcon(
    modifier: Modifier = Modifier,
    size: Dp = 16.dp,
    color: Color = MaterialTheme.colorScheme.primary,
) {
    val barWidth = size * 3 / 16
    val gap = size * 2 / 16
    val transition = rememberInfiniteTransition()

    Row(
        modifier = modifier.height(size),
        horizontalArrangement = Arrangement.spacedBy(gap),
        verticalAlignment = Alignment.Bottom,
    ) {
        BAR_DELAYS.forEach { delay ->
            val scale by transition.animateFloat(
                initialValue = MIN_SCALE,
                targetValue = MAX_SCALE,
                animationSpec = infiniteRepeatable(
                    animation = tween(
                        durationMillis = ANIMATION_DURATION_MS / 2,
                        delayMillis = delay,
                    ),
                    repeatMode = RepeatMode.Reverse,
                ),
            )

            Box(
                modifier = Modifier
                    .width(barWidth)
                    .height(size)
                    .graphicsLayer {
                        scaleY = scale
                        transformOrigin = TransformOrigin(0.5f, 1f)
                    }
                    .background(color),
            )
        }
    }
}
