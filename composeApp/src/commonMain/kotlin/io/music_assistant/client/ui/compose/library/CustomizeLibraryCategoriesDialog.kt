@file:Suppress("MagicNumber")

package io.music_assistant.client.ui.compose.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import io.music_assistant.client.ui.compose.common.ReorderableEnabledList
import musicassistantclient.composeapp.generated.resources.Res
import musicassistantclient.composeapp.generated.resources.common_done
import musicassistantclient.composeapp.generated.resources.library_customize
import org.jetbrains.compose.resources.stringResource

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
    var items by remember { mutableStateOf(initialConfig) }
    Column {
        ReorderableEnabledList(
            initialItems = initialConfig,
            key = { it.name },
            label = { stringResource(it.stringResource()) },
            onItemsChange = { items = it },
        )
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
