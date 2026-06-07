package io.music_assistant.client.ui.compose.nav

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.layout
import kotlin.math.roundToInt

/**
 * [TopAppBar] with row below the navigation icon, title and actions. Not an official component
 * within Material Design 3.
 *
 * Both rows collapse/expand together as one unit driven by the shared [scrollBehavior]'s
 * `heightOffset`. Neither inner [TopAppBar] receives the [scrollBehavior] (each would otherwise
 * self-translate and overwrite the shared `heightOffsetLimit`, doubling the motion); instead the
 * wrapping [Column] measures the combined height, publishes `heightOffsetLimit = -combinedHeight`,
 * shrinks its reported height by the current offset so content below moves up, shifts content up by
 * that offset, and clips overflow.
 */
@Composable
fun TwoRowTopAppBar(
    title: @Composable () -> Unit,
    scrollBehavior: TopAppBarScrollBehavior?,
    navigationIcon: @Composable () -> Unit,
    actions: @Composable RowScope.() -> Unit,
    secondRow: @Composable RowScope.() -> Unit,
) {
    Column(
        modifier = Modifier
            .clipToBounds()
            .layout { measurable, constraints ->
                val placeable = measurable.measure(constraints)
                val state = scrollBehavior?.state

                // Full combined two-row budget (inner bars measure at their expanded height
                // because they receive scrollBehavior = null).
                state?.heightOffsetLimit = -placeable.height.toFloat()

                // heightOffset is <= 0 and already coerced into [heightOffsetLimit, 0] by the state.
                val offset = state?.heightOffset ?: 0f
                val height = (placeable.height + offset).coerceAtLeast(0f).roundToInt()

                // Center the block in the shrinking box, exactly like Material3's own TopAppBar
                // (placeTopAppBar centers content at (contentHeight - height) / 2). The box shrinks
                // by the full offset, but centering moves the bar content up by only HALF of it, so
                // the bar parallax-lags at half the scroll speed while the content below rises at
                // full speed and appears to overlap it — matching every other bar in the app.
                layout(placeable.width, height) {
                    placeable.place(0, (offset / 2f).roundToInt())
                }
            },
    ) {
        TopAppBar(
            title = title,
            scrollBehavior = null,
            navigationIcon = navigationIcon,
            actions = actions,
        )

        TopAppBar(
            title = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    secondRow()
                }
            },
            scrollBehavior = null,
            windowInsets = WindowInsets(),
        )
    }
}
