@file:Suppress("MagicNumber")

package io.music_assistant.client.ui.compose.library

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import compose.icons.TablerIcons
import compose.icons.tablericons.GripVertical
import io.music_assistant.client.ui.MAX_DIALOG_HEIGHT
import musicassistantclient.composeapp.generated.resources.Res
import musicassistantclient.composeapp.generated.resources.common_done
import musicassistantclient.composeapp.generated.resources.library_customize
import org.jetbrains.compose.resources.stringResource
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

@Composable
fun CustomizeLibraryCategoriesDialog(
    initialConfig: List<Pair<LibraryCategory, Boolean>>,
    onDismissRequest: () -> Unit,
    onConfirm: (List<Pair<LibraryCategory, Boolean>>) -> Unit,
) {
    Dialog(onDismissRequest = onDismissRequest) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
        ) {
            Box(modifier = Modifier.padding(vertical = 16.dp)) {
                Column {
                    Text(
                        stringResource(Res.string.library_customize),
                        style = MaterialTheme.typography.headlineSmall,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                    Box(modifier = Modifier.padding(top = 16.dp)) {
                        TabsCustomizeList(initialConfig) { result ->
                            onConfirm(result)
                            onDismissRequest()
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TabsCustomizeList(
    initialConfig: List<Pair<LibraryCategory, Boolean>>,
    onDone: (List<Pair<LibraryCategory, Boolean>>) -> Unit,
) {
    val plateShape = RoundedCornerShape(12.dp)
    var items by remember { mutableStateOf(initialConfig) }
    val enabledCount = items.count { it.second }
    val listState = rememberLazyListState()
    val reorderableLazyListState =
        rememberReorderableLazyListState(listState) { from, to ->
            // Constrain reorder to the enabled section only.
            if (from.index >= enabledCount || to.index >= enabledCount) return@rememberReorderableLazyListState
            items = items.toMutableList().apply {
                add(to.index, removeAt(from.index))
            }
        }

    Column {
        LazyColumn(
            modifier = Modifier.heightIn(max = MAX_DIALOG_HEIGHT),
            state = listState,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(
                items = items,
                key = { it.first.name },
            ) { (tab, enabled) ->
                ReorderableItem(state = reorderableLazyListState, key = tab.name) {
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
                            text = stringResource(tab.stringResource()),
                            modifier = Modifier
                                .weight(1f)
                                .alpha(if (enabled) 1f else 0.5f),
                            style = MaterialTheme.typography.bodyLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        // Disable switch on the last enabled tab to enforce min=1.
                        val canToggle = enabled.not() || enabledCount > 1
                        Switch(
                            checked = enabled,
                            enabled = canToggle,
                            onCheckedChange = { newEnabled ->
                                items = moveToEnabledBoundary(items, tab, newEnabled)
                            },
                        )
                        Icon(
                            modifier = Modifier
                                .padding(start = 12.dp)
                                .then(
                                    if (enabled) Modifier.draggableHandle() else Modifier,
                                )
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
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp, end = 8.dp),
            horizontalArrangement = Arrangement.End,
        ) {
            TextButton(onClick = { onDone(items) }) {
                Text(stringResource(Res.string.common_done))
            }
        }
    }
}

@Preview
@Composable
private fun PreviewCustomizeLibraryCategoriesDialog() {
    CustomizeLibraryCategoriesDialog(
        initialConfig = LibraryCategory.entries.mapIndexed { i, t -> t to (i < 5) },
        onDismissRequest = {},
        onConfirm = {},
    )
}
