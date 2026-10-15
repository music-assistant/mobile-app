package io.music_assistant.client.ui.compose.home.players

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.pager.PagerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import io.music_assistant.client.ui.compose.common.ToastDuration
import io.music_assistant.client.ui.compose.common.ToastState
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import musicassistantclient.composeapp.generated.resources.Res
import musicassistantclient.composeapp.generated.resources.players_swipe_hint
import org.jetbrains.compose.resources.stringResource

// Lets the expand animation settle before the hint competes with it.
private const val HINT_START_DELAY_MS = 400L
private const val GLOW_PULSE_MS = 800
private const val GLOW_FADE_MS = 300
private val GLOW_WIDTH = 48.dp

/**
 * One-time hint that the pager swipes between players: a pulsing primary glow on each edge
 * that can scroll, plus a long toast. It ends when the toast ends or on the first swipe, and
 * only then calls [onShown]. Leaving composition earlier (player collapsed) does not count as
 * shown, so the hint comes back next time. The glow only draws, so touches reach the pager.
 */
@Composable
fun Modifier.playerSwipeHint(
    enabled: Boolean,
    pagerState: PagerState,
    toastState: ToastState,
    onShown: () -> Unit,
): Modifier {
    if (!enabled) return this
    val message = stringResource(Res.string.players_swipe_hint)
    val currentOnShown by rememberUpdatedState(onShown)
    var visible by remember { mutableStateOf(false) }

    LaunchedEffect(pagerState, toastState) {
        delay(HINT_START_DELAY_MS)
        toastState.showToast(message, ToastDuration.LONG)
        visible = true
        val swiped = withTimeoutOrNull(ToastDuration.LONG.millis) {
            snapshotFlow { pagerState.isScrollInProgress }.first { it }
        } != null
        visible = false
        // Clear only our own toast, never one that replaced it.
        if (swiped && toastState.currentToast.value?.message == message) toastState.hideToast()
        delay(GLOW_FADE_MS.toLong())
        currentOnShown()
    }

    val fade = animateFloatAsState(if (visible) 1f else 0f, tween(GLOW_FADE_MS))
    val pulse = rememberInfiniteTransition().animateFloat(
        initialValue = 0.25f,
        targetValue = 0.7f,
        animationSpec = infiniteRepeatable(tween(GLOW_PULSE_MS), RepeatMode.Reverse),
    )
    val primary = MaterialTheme.colorScheme.primary
    // States are read in the draw phase only, so the pulse never recomposes the pager.
    return drawWithContent {
        drawContent()
        val alpha = fade.value * pulse.value
        if (alpha == 0f) return@drawWithContent
        val ltr = layoutDirection == LayoutDirection.Ltr
        if (pagerState.canScrollBackward) drawEdgeGlow(primary.copy(alpha = alpha), left = ltr)
        if (pagerState.canScrollForward) drawEdgeGlow(primary.copy(alpha = alpha), left = !ltr)
    }
}

private fun DrawScope.drawEdgeGlow(color: Color, left: Boolean) {
    val width = GLOW_WIDTH.toPx().coerceAtMost(size.width / 2)
    val startX = if (left) 0f else size.width - width
    val stops = listOf(color, Color.Transparent).let { if (left) it else it.reversed() }
    drawRect(
        brush = Brush.horizontalGradient(stops, startX = startX, endX = startX + width),
        topLeft = Offset(startX, 0f),
        size = Size(width, size.height),
    )
}
