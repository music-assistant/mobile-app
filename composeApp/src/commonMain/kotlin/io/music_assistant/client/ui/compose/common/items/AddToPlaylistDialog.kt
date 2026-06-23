package io.music_assistant.client.ui.compose.common.items

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import compose.icons.TablerIcons
import compose.icons.tablericons.Plus
import io.music_assistant.client.data.model.client.items.AppMediaItem
import io.music_assistant.client.data.model.client.items.Playlist
import kotlinx.coroutines.launch
import musicassistantclient.composeapp.generated.resources.Res
import musicassistantclient.composeapp.generated.resources.common_cancel
import musicassistantclient.composeapp.generated.resources.playlist_add_button
import musicassistantclient.composeapp.generated.resources.playlist_add_to_title
import musicassistantclient.composeapp.generated.resources.playlist_no_editable
import org.jetbrains.compose.resources.stringResource

@Composable
fun AddToPlaylistDialog(
    item: AppMediaItem,
    playlistActions: PlaylistActions,
    onDismiss: () -> Unit,
) {
    var playlists by remember { mutableStateOf<List<Playlist>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var showCreateDialog by remember { mutableStateOf(false) }
    // The just-created playlist, so the refreshed list can highlight/scroll to it.
    var highlighted by remember { mutableStateOf<Playlist?>(null) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    LaunchedEffect(item.itemId) {
        isLoading = true
        playlists = playlistActions.getEditablePlaylists()
        isLoading = false
    }

    // Scroll the freshly created playlist into view once it lands in the list.
    LaunchedEffect(highlighted, playlists) {
        val key = highlighted?.lazyListKey() ?: return@LaunchedEffect
        val index = playlists.indexOfFirst { it.lazyListKey() == key }
        // +1 for the pinned "New playlist" row at the top of the list.
        if (index >= 0) listState.animateScrollToItem(index + 1)
    }

    if (showCreateDialog) {
        CreatePlaylistDialog(
            onDismiss = { showCreateDialog = false },
            onCreate = { name ->
                showCreateDialog = false
                scope.launch {
                    isLoading = true
                    val created = playlistActions.createPlaylist(name)
                    if (created != null) {
                        playlists = playlistActions.getEditablePlaylists()
                        highlighted = created
                    }
                    isLoading = false
                }
            },
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.playlist_add_to_title)) },
        text = {
            when {
                isLoading -> Box(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }

                else -> LazyColumn(state = listState) {
                    item {
                        NewPlaylistRow(onClick = { showCreateDialog = true })
                    }

                    if (playlists.isEmpty()) {
                        item {
                            Text(
                                text = stringResource(Res.string.playlist_no_editable),
                                modifier = Modifier.fillMaxWidth().padding(16.dp),
                            )
                        }
                    } else {
                        items(
                            items = playlists,
                            key = { p -> p.lazyListKey() },
                        ) { playlist ->
                            val isHighlighted =
                                playlist.lazyListKey() == highlighted?.lazyListKey()
                            TextButton(
                                onClick = {
                                    playlistActions.addToPlaylist(item.uri, playlist)
                                    onDismiss()
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(
                                        if (isHighlighted) {
                                            MaterialTheme.colorScheme.surfaceVariant
                                        } else {
                                            Color.Transparent
                                        },
                                    ),
                            ) {
                                Text(
                                    text = playlist.displayName,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        }
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

@Composable
private fun NewPlaylistRow(onClick: () -> Unit) {
    TextButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Icon(
            imageVector = TablerIcons.Plus,
            contentDescription = null,
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = stringResource(Res.string.playlist_add_button),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
