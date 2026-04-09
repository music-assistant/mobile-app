package io.music_assistant.client.ui.compose.home

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.music_assistant.client.ui.compose.nav.BackHandler

@Composable
fun BoxScope.FloatingBar(
    modifier: Modifier = Modifier,
    expanded: Boolean = false,
    onExpand: (Boolean) -> Unit = {},
    content: @Composable (expanded: Boolean) -> Unit
) {
    BackHandler(enabled = expanded) {
        onExpand(false)
    }

    val clip by animateDpAsState(if (expanded) 0.dp else 16.dp)
    val padding by animateDpAsState(if (expanded) 0.dp else FloatingBarDefaults.padding)

    Box(
        modifier = modifier
            .align(Alignment.BottomCenter)
            .padding(padding)
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
            if (expanded) {
                IconButton(
                    onClick = { onExpand(false) },
                    modifier = Modifier.statusBarsPadding().fillMaxWidth().height(36.dp)
                ) {
                    Icon(
                        Icons.Default.ExpandMore,
                        "Collapse",
                        modifier = Modifier.size(32.dp)
                    )
                }
            }

            content(expanded)
        }
    }
}

object FloatingBarDefaults {
    val padding = 8.dp
}

@Preview
@Composable
private fun PreviewFloatingBarRow() {
    Box(Modifier.fillMaxSize()) {
        FloatingBar {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Left")
                Text("Right")
            }
        }
    }
}

@Preview
@Composable
private fun PreviewFloatingBarColumn() {
    Box(Modifier.fillMaxSize()) {
        FloatingBar {
            Column {
                Text("Top")
                Text("Bottom")
            }
        }
    }
}

@Preview
@Composable
private fun PreviewFloatingBarExpanded() {
    Box(Modifier.fillMaxSize()) {
        FloatingBar(expanded = true) {
            Column {
                Text("Top")
                Text("Bottom")
            }
        }
    }
}

