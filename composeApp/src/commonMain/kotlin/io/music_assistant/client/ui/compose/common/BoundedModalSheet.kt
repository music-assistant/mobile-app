package io.music_assistant.client.ui.compose.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp

internal expect val needsBoundedModalSheet: Boolean

internal val WindowUnavailableSheetWidth = 640.dp
internal val WindowUnavailableSheetHeight = 600.dp

@Composable
internal fun Modifier.boundedModalSheet(): Modifier {
    if (!needsBoundedModalSheet) return this
    val windowInfo = LocalWindowInfo.current
    return boundedModalSheetLayout(windowInfo.containerSize, WindowUnavailableSheetWidth, WindowUnavailableSheetHeight)
}

internal fun Modifier.boundedModalSheetLayout(
    window: IntSize,
    fallbackWidth: Dp,
    fallbackHeight: Dp,
): Modifier =
    layout { measurable, constraints ->
        val effective = boundedModalSheetConstraints(
            constraints = constraints,
            window = window,
            fallbackWidth = fallbackWidth.roundToPx(),
            fallbackHeight = fallbackHeight.roundToPx(),
        )
        val placeable = measurable.measure(effective)
        // An unbounded dialog must position the sheet within the viewport, not center its content again.
        val height = if (constraints.hasBoundedHeight) placeable.height else effective.maxHeight
        layout(placeable.width, height) { placeable.placeRelative(0, 0) }
    }

/** Bounds only unbounded axes using the window size or a fallback when the window is unavailable. */
internal fun boundedModalSheetConstraints(
    constraints: Constraints,
    window: IntSize,
    fallbackWidth: Int,
    fallbackHeight: Int,
): Constraints {
    if (constraints.hasBoundedWidth && constraints.hasBoundedHeight) return constraints
    val width = if (constraints.hasBoundedWidth) {
        constraints.maxWidth
    } else {
        (window.width.takeIf { it > 0 } ?: fallbackWidth).coerceAtLeast(constraints.minWidth)
    }
    val height = if (constraints.hasBoundedHeight) {
        constraints.maxHeight
    } else {
        (window.height.takeIf { it > 0 } ?: fallbackHeight).coerceAtLeast(constraints.minHeight)
    }
    return if (constraints.hasBoundedHeight) {
        Constraints.fitPrioritizingHeight(constraints.minWidth, width, constraints.minHeight, height)
    } else {
        Constraints.fitPrioritizingWidth(constraints.minWidth, width, constraints.minHeight, height)
    }
}
