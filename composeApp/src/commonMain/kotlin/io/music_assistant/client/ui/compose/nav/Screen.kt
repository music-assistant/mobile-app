package io.music_assistant.client.ui.compose.nav

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Surface
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.material3.TopAppBarState
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.layout
import kotlin.math.roundToInt

/**
 * Simplified version of [androidx.compose.material3.Scaffold] that just supports a top bar. Can be
 * nested in a [androidx.compose.material3.Scaffold] with a bottom bar without introducing the
 * extra recomposition of a nesting set [androidx.compose.material3.Scaffold] composables.
 *
 * The top bar collapses as one unit: the whole [topBar] slot translates up at the same speed as the
 * scrolling content (a 1:1 slide-off) and the content below rises to fill. This is centralized here
 * — bars passed to [topBar] must NOT take a [TopAppBarScrollBehavior] themselves, otherwise they'd
 * collapse a second time on top of this.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Screen(
    topBar: @Composable () -> Unit,
    topAppBarState: TopAppBarState = rememberTopAppBarState(),
    content: @Composable () -> Unit,
) {
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior(state = topAppBarState)

    Surface {
        Column(
            modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        ) {
            Box(modifier = Modifier.collapsingTopBar(scrollBehavior)) {
                topBar()
            }
            content()
        }
    }
}

/**
 * Collapses the top bar slot by translating it up by the shared [scrollBehavior]'s `heightOffset`
 * and shrinking its reported height by the same amount, so the content below moves up to fill. The
 * full (non-halved) offset gives a 1:1 slide-off; overflow is clipped via [clipToBounds].
 */
@OptIn(ExperimentalMaterial3Api::class)
private fun Modifier.collapsingTopBar(scrollBehavior: TopAppBarScrollBehavior): Modifier =
    clipToBounds().layout { measurable, constraints ->
        val placeable = measurable.measure(constraints)
        scrollBehavior.state.heightOffsetLimit = -placeable.height.toFloat()
        val offset = scrollBehavior.state.heightOffset
        val height = (placeable.height + offset).coerceAtLeast(0f).roundToInt()
        layout(placeable.width, height) {
            placeable.place(0, offset.roundToInt())
        }
    }
