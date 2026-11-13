package io.music_assistant.client.ui.utils

import androidx.compose.runtime.Composable
import androidx.compose.ui.layout.SubcomposeLayout

/**
 * Layout that snaps to the max available height once the content pass the [thresholdRatio] of that
 * height.
 */
@Composable
fun SnapToHeightLayout(thresholdRatio: Double, content: @Composable () -> Unit) {
    SubcomposeLayout { constraints ->
        val contentPlaceable = subcompose("content") { content() }[0].measure(constraints)
        val height = if (contentPlaceable.height >= constraints.maxHeight * thresholdRatio) {
            constraints.maxHeight
        } else {
            contentPlaceable.height
        }

        layout(constraints.maxWidth, height) {
            contentPlaceable.place(0, 0)
        }
    }
}
