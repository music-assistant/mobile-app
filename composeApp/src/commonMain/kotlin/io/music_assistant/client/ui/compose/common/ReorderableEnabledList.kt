@file:Suppress("MagicNumber")

package io.music_assistant.client.ui.compose.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import compose.icons.TablerIcons
import compose.icons.tablericons.GripVertical
import io.music_assistant.client.ui.MAX_DIALOG_HEIGHT
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

/**
 * Reorderable + toggleable list. Enabled items float to the top and can be dragged among
 * themselves; disabled items sink below and lose the drag handle. Owns its working state and
 * emits the full ordered list on every change via [onItemsChange].
 *
 * @param canDisableLast when false (default), the last enabled item can't be turned off (min 1).
 */
@Composable
fun <T> ReorderableEnabledList(
    initialItems: List<Pair<T, Boolean>>,
    key: (T) -> Any,
    label: @Composable (T) -> String,
    onItemsChange: (List<Pair<T, Boolean>>) -> Unit,
    modifier: Modifier = Modifier,
    canDisableLast: Boolean = false,
) {
    val plateShape = RoundedCornerShape(12.dp)
    var items by remember { mutableStateOf(initialItems) }
    val enabledCount = items.count { it.second }
    val listState = rememberLazyListState()
    val reorderableLazyListState =
        rememberReorderableLazyListState(listState) { from, to ->
            // Constrain reorder to the enabled section only.
            if (from.index >= enabledCount || to.index >= enabledCount) return@rememberReorderableLazyListState
            items = items.toMutableList().apply { add(to.index, removeAt(from.index)) }
            onItemsChange(items)
        }

    LazyColumn(
        modifier = modifier.heightIn(max = MAX_DIALOG_HEIGHT),
        state = listState,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(
            items = items,
            key = { key(it.first) },
        ) { (item, enabled) ->
            ReorderableItem(state = reorderableLazyListState, key = key(item)) {
                Row(
                    modifier = Modifier
                        .padding(horizontal = 16.dp)
                        .fillMaxWidth()
                        .clip(plateShape)
                        .background(Color.Transparent)
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, plateShape)
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = label(item),
                        modifier = Modifier
                            .weight(1f)
                            .alpha(if (enabled) 1f else 0.5f),
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    // Enforce min=1 enabled unless caller opts out.
                    val canToggle = canDisableLast || enabled.not() || enabledCount > 1
                    Switch(
                        checked = enabled,
                        enabled = canToggle,
                        onCheckedChange = { newEnabled ->
                            items = moveToEnabledBoundary(items, item, newEnabled)
                            onItemsChange(items)
                        },
                    )
                    Icon(
                        modifier = Modifier
                            .padding(start = 12.dp)
                            .then(if (enabled) Modifier.draggableHandle() else Modifier)
                            .alpha(if (enabled) 1f else 0.3f)
                            .size(20.dp),
                        imageVector = TablerIcons.GripVertical,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.secondary,
                    )
                }
            }
        }
    }
}
