package io.music_assistant.client.ui.utils

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.layout.SubcomposeLayout

/**
 * Layout that snaps to the max available height once the content pass the [thresholdRatio] of that
 * height.
 *
 * The layout height will only ever grow and never shrink. This prevents shorter content from
 * repositioning the layout.
 */
@Composable
fun SnapToHeightLayout(thresholdRatio: Double, content: @Composable () -> Unit) {
    var maxHeight by remember { mutableStateOf(0) }

    SubcomposeLayout { constraints ->
        val contentPlaceable = subcompose("content") { content() }[0].measure(constraints)
        val newHeight = if (contentPlaceable.height >= constraints.maxHeight * thresholdRatio) {
            constraints.maxHeight
        } else {
            contentPlaceable.height
        }

        if (newHeight > maxHeight) {
            maxHeight = newHeight
        }

        layout(constraints.maxWidth, maxHeight) {
            contentPlaceable.place(0, 0)
        }
    }
}
