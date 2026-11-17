package io.music_assistant.client.ui.compose.home.players

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlin.math.abs

/**
 * Wraps a volume [slider] with a tap-to-step gesture: a tap on the left half steps
 * the volume down, a tap on the right half steps it up. Taps on/near the thumb and
 * drags are handed off to the slider so normal dragging still works.
 *
 * A tap also shows the two step areas as a translucent overlay for [STEP_HINT_MILLIS];
 * a tap while the overlay shows restarts that time. A drag never shows it.
 *
 * The single source of truth for the expanded-player and group-settings volume
 * controls — keep the action mapping (down/up vs. group down/up) at the call site.
 */
@Composable
fun VolumeSliderBox(
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    hintColor: Color = MaterialTheme.colorScheme.onSurface,
    hintIconColor: Color = MaterialTheme.colorScheme.surface,
    volume: () -> Float,
    onStepDown: () -> Unit,
    onStepUp: () -> Unit,
    slider: @Composable () -> Unit,
) {
    val touchSlopPx = LocalViewConfiguration.current.touchSlop
    val thumbHitPx = with(LocalDensity.current) { 24.dp.toPx() }
    val volumeForGesture by rememberUpdatedState(volume())
    // Taps since the hint last hid; the hint shows while it is above zero. Each tap restarts the
    // timer, and the reset keeps the counter small.
    var hintTaps by remember { mutableIntStateOf(0) }
    LaunchedEffect(hintTaps) {
        if (hintTaps == 0) return@LaunchedEffect
        delay(STEP_HINT_MILLIS)
        hintTaps = 0
    }
    val gesture = if (enabled) {
        Modifier.pointerInput(Unit) {
            awaitEachGesture {
                val widthPx = size.width
                if (widthPx == 0) return@awaitEachGesture
                val down = awaitFirstDown(
                    requireUnconsumed = false,
                    pass = PointerEventPass.Initial,
                )
                val thumbCenter = (volumeForGesture / 100f) * widthPx
                // Tap on/near thumb: hand off to the Slider so dragging works normally.
                if (abs(down.position.x - thumbCenter) <= thumbHitPx) {
                    return@awaitEachGesture
                }
                down.consume()
                var dragged = false
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    val change = event.changes
                        .firstOrNull { it.id == down.id } ?: break
                    if (!dragged &&
                        (change.position - down.position).getDistance() > touchSlopPx
                    ) {
                        dragged = true
                    }
                    if (change.changedToUp()) {
                        change.consume()
                        if (!dragged) {
                            hintTaps++
                            if (down.position.x < widthPx / 2f) onStepDown() else onStepUp()
                        }
                        break
                    } else {
                        change.consume()
                    }
                }
            }
        }
    } else {
        Modifier
    }
    Box(modifier = modifier.then(gesture)) {
        slider()
        // No pointer modifiers on the overlay, so it is not a hit target: taps and
        // drags keep reaching the gesture above and the slider below.
        AnimatedVisibility(
            visible = hintTaps > 0,
            modifier = Modifier.matchParentSize(),
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            Row(
                modifier = Modifier.fillMaxSize().testTag(VolumeSliderSemantics.STEP_HINT_TAG),
                horizontalArrangement = Arrangement.spacedBy(STEP_HINT_GAP),
            ) {
                StepHint(Icons.Default.Remove, hintColor, hintIconColor, Modifier.weight(1f))
                StepHint(Icons.Default.Add, hintColor, hintIconColor, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun StepHint(icon: ImageVector, color: Color, iconColor: Color, modifier: Modifier) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(8.dp))
            .background(color.copy(alpha = STEP_HINT_FILL_ALPHA)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = iconColor,
            modifier = Modifier.size(20.dp),
        )
    }
}

object VolumeSliderSemantics {
    const val STEP_HINT_TAG = "VolumeStepHint"
}

private const val STEP_HINT_MILLIS = 3_000L
private const val STEP_HINT_FILL_ALPHA = 0.6f
private val STEP_HINT_GAP = 6.dp
