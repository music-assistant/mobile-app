package io.music_assistant.client.ui.compose.common.items

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.music_assistant.client.data.model.client.items.Artist
import io.music_assistant.client.ui.MAX_DIALOG_HEIGHT
import musicassistantclient.composeapp.generated.resources.Res
import musicassistantclient.composeapp.generated.resources.action_choose_artist
import musicassistantclient.composeapp.generated.resources.common_cancel
import org.jetbrains.compose.resources.stringResource

@Composable
fun ChooseArtistDialog(
    artists: List<Artist>,
    onSelect: (Artist) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.action_choose_artist)) },
        text = {
            LazyColumn(modifier = Modifier.heightIn(max = MAX_DIALOG_HEIGHT)) {
                // No stable key: the candidate list is short and never mutates while the
                // dialog is open, so positional identity is correct (and avoids relying on
                // artist itemId uniqueness).
                items(items = artists) { artist ->
                    TextButton(
                        onClick = { onSelect(artist) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = null,
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = artist.displayName,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(Res.string.common_cancel))
            }
        },
    )
}
