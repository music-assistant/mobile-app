package io.music_assistant.client.ui.compose.common

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout

// Three-slot row that centers `center` against the parent width while reserving
// equal side gutters sized to the wider of `start`/`end`, so a long center
// child shrinks instead of overlapping the side slots.
@Composable
fun CenteredThreeSlotRow(
    start: @Composable () -> Unit,
    center: @Composable () -> Unit,
    end: @Composable () -> Unit,
    modifier: Modifier = Modifier.Companion,
) {
    Layout(
        modifier = modifier,
        content = {
            Box { start() }
            Box { center() }
            Box { end() }
        },
    ) { measurables, constraints ->
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val startPlaceable = measurables[0].measure(loose)
        val endPlaceable = measurables[2].measure(loose)
        val side = maxOf(startPlaceable.width, endPlaceable.width)
        val parentWidth = if (constraints.hasBoundedWidth) {
            constraints.maxWidth
        } else {
            startPlaceable.width + endPlaceable.width
        }
        val centerMaxWidth = (parentWidth - 2 * side).coerceAtLeast(0)
        val centerPlaceable = measurables[1].measure(
            loose.copy(maxWidth = centerMaxWidth),
        )
        val height = maxOf(
            startPlaceable.height,
            centerPlaceable.height,
            endPlaceable.height,
        )
        layout(parentWidth, height) {
            startPlaceable.placeRelative(0, 0)
            centerPlaceable.placeRelative((parentWidth - centerPlaceable.width) / 2, 0)
            endPlaceable.placeRelative(parentWidth - endPlaceable.width, 0)
        }
    }
}
