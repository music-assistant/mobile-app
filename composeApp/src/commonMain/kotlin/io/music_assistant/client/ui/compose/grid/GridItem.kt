package io.music_assistant.client.ui.compose.grid

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import io.music_assistant.client.utils.WindowClass

@Composable
fun GridItem(
    modifier: Modifier = Modifier,
    description: String?,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    BoxWithConstraints(
        modifier = Modifier.clearAndSetSemantics {
            if (description != null) {
                contentDescription = description
            }
        },
    ) {
        val cellWidthModifier = if (constraints.hasBoundedWidth) {
            Modifier.fillMaxWidth()
        } else {
            Modifier.width(gridItemMinSize())
        }

        Column(
            modifier = cellWidthModifier
                .then(modifier)
                .clip(RoundedCornerShape(8.dp))
                .combinedClickable(onClick = onClick, onLongClick = onLongClick)
                .padding(4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            content()
        }
    }
}

@Composable
fun gridItemMinSize() = when {
    WindowClass.isAtLeastMedium() -> 180.dp
    else -> 140.dp
}
