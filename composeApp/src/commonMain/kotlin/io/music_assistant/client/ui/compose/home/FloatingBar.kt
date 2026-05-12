package io.music_assistant.client.ui.compose.home

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.music_assistant.client.ui.compose.nav.BackHandler

@Composable
fun FloatingBar(
    expanded: Boolean = false,
    onExpand: (Boolean) -> Unit = {},
    content: @Composable (expanded: Boolean, contentPadding: PaddingValues) -> Unit,
) {
    BackHandler(enabled = expanded) {
        onExpand(false)
    }

    val clip by animateDpAsState(if (expanded) 0.dp else 16.dp)
    val padding by animateDpAsState(if (expanded) 0.dp else 8.dp)
    val paddingValues = PaddingValues(padding)

    Box(
        modifier = Modifier
            .testTag(FloatingBarSemantics.TAG)
            .padding(paddingValues)
            .clip(RoundedCornerShape(clip))
            .fillMaxWidth()
            .let {
                if (expanded) {
                    it.fillMaxHeight()
                } else {
                    it.wrapContentHeight().clickable { onExpand(true) }
                }
            }
            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Column {
            val contentPadding = if (expanded) {
                val windowInsets = WindowInsets.systemBars.only(WindowInsetsSides.Bottom)
                windowInsets.asPaddingValues()
            } else {
                PaddingValues()
            }

            content(expanded, contentPadding)
        }
    }
}

@Composable
fun FloatingBarLayout(
    modifier: Modifier = Modifier,
    floatingBar: @Composable () -> Unit,
    content: @Composable (PaddingValues) -> Unit = {},
) {
    SubcomposeLayout(modifier = modifier.fillMaxSize()) { constraints ->
        val looseConstraints = constraints.copy(minWidth = 0, minHeight = 0)

        val floatingBarPlaceable =
            subcompose("floatingBar") { Box(modifier = Modifier.wrapContentHeight()) { floatingBar() } }
                .first()
                .measure(looseConstraints)

        val contentPadding = PaddingValues(bottom = floatingBarPlaceable.height.toDp())
        val contentPlaceable = subcompose("content") { Box { content(contentPadding) } }
            .first()
            .measure(looseConstraints)

        val layoutWidth = constraints.maxWidth
        val layoutHeight = constraints.maxHeight
        layout(layoutWidth, layoutHeight) {
            contentPlaceable.place(0, 0)
            floatingBarPlaceable.place(0, layoutHeight - floatingBarPlaceable.height)
        }
    }
}

object FloatingBarSemantics {
    const val TAG = "FloatingBar"
}

@Preview
@Composable
private fun PreviewFloatingBarRow() {
    FloatingBarLayout(
        floatingBar = {
            FloatingBar { _, _ ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("Left")
                    Text("Right")
                }
            }
        },
    )
}

@Preview
@Composable
private fun PreviewFloatingBarColumn() {
    FloatingBarLayout(
        floatingBar = {
            FloatingBar { _, _ ->
                Column {
                    Text("Top")
                    Text("Bottom")
                }
            }
        },
    )
}

@Preview
@Composable
private fun PreviewFloatingBarExpanded() {
    FloatingBarLayout(floatingBar = {
        FloatingBar(expanded = true) { _, _ ->
            Column {
                Text("Top")
                Text("Bottom")
            }
        }
    })
}
