package io.music_assistant.client.ui.compose.item

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.filled.AddToQueue
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlaylistAddCircle
import androidx.compose.material.icons.filled.QueuePlayNext
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import compose.icons.TablerIcons
import compose.icons.tablericons.FolderMinus
import compose.icons.tablericons.FolderPlus
import compose.icons.tablericons.Heart
import compose.icons.tablericons.HeartBroken
import io.music_assistant.client.data.model.client.AppMediaItem
import io.music_assistant.client.data.model.client.AppMediaItemFixtures
import io.music_assistant.client.data.model.server.QueueOption
import io.music_assistant.client.ui.compose.common.OverflowMenu
import io.music_assistant.client.ui.compose.common.OverflowMenuOption
import io.music_assistant.client.ui.compose.common.viewmodel.ActionsViewModel
import kotlinx.coroutines.launch
import org.jetbrains.compose.ui.tooling.preview.Preview

@Composable
fun ItemHeader(
    item: AppMediaItem,
    serverUrl: String? = null,
    onBack: () -> Unit = {},
    onPlayClick: (QueueOption, Boolean) -> Unit = { _, _ -> },
    libraryAction: ActionsViewModel.LibraryActions? = null,
    playlistActions: ActionsViewModel.PlaylistActions? = null
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
        }

        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            item.imageInfo?.url(serverUrl)?.let {
                val shape = if (item is AppMediaItem.Artist) {
                    CircleShape
                } else {
                    RoundedCornerShape(16.dp)
                }

                AsyncImage(
                    model = it,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize(0.70f)
                        .clip(shape)
                )
            }

            Text(
                item.name,
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(top = 16.dp)
            )

            item.subtitle?.let {
                Text(
                    it,
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = 16.dp)
            ) {
                Button(
                    modifier = Modifier.semantics { contentDescription = "Play now" },
                    onClick = { onPlayClick(QueueOption.REPLACE, false) }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null)
                        Text(modifier = Modifier.padding(start = 8.dp), text = "Play")
                    }
                }

                ItemOverflowMenu(
                    item = item,
                    modifier = Modifier.padding(start = 4.dp),
                    onPlayClick = onPlayClick,
                    libraryActions = libraryAction,
                    playlistActions = playlistActions
                )
            }
        }
    }
}

@Composable
private fun ItemOverflowMenu(
    item: AppMediaItem,
    modifier: Modifier,
    onPlayClick: (QueueOption, Boolean) -> Unit,
    libraryActions: ActionsViewModel.LibraryActions?,
    playlistActions: ActionsViewModel.PlaylistActions?
) {
    val coroutineScope = rememberCoroutineScope()
    var showPlaylistDialog by rememberSaveable { mutableStateOf(false) }
    var playlists by remember { mutableStateOf<List<AppMediaItem.Playlist>>(emptyList()) }
    var isLoadingPlaylists by remember { mutableStateOf(false) }

    OverflowMenu(
        modifier = modifier,
        buttonContent = { onClick ->
            Icon(
                modifier = Modifier.clickable { onClick() },
                imageVector = Icons.Default.ExpandMore,
                contentDescription = null
            )
        },
        options = buildList {
            add(
                OverflowMenuOption(
                    title = "Insert next and play",
                    icon = Icons.Default.PlaylistAddCircle
                ) { onPlayClick(QueueOption.PLAY, false) })
            add(
                OverflowMenuOption(
                    title = "Insert next",
                    icon = Icons.Default.QueuePlayNext
                ) {
                    onPlayClick(QueueOption.NEXT, false)
                })
            add(
                OverflowMenuOption(
                    title = "Add to bottom",
                    icon = Icons.Default.AddToQueue
                ) {
                    onPlayClick(
                        QueueOption.ADD, false
                    )
                })
            if (item.canStartRadio) {
                add(
                    OverflowMenuOption(
                        title = "Start radio",
                        icon = Icons.Default.Radio
                    ) {
                        onPlayClick(QueueOption.REPLACE, true)
                    })
            }
            add(
                OverflowMenuOption(
                    title =
                        if (item.isInLibrary) "Remove from library"
                        else "Add to library",
                    icon =
                        if (item.isInLibrary) TablerIcons.FolderMinus
                        else TablerIcons.FolderPlus
                ) { libraryActions?.onLibraryClick(item) })
            if (item.isInLibrary) {
                add(
                    OverflowMenuOption(
                        title =
                            if (item.favorite == true) "Unfavorite"
                            else "Favorite",
                        icon =
                            if (item.favorite == true) TablerIcons.HeartBroken
                            else TablerIcons.Heart
                    ) { libraryActions?.onFavoriteClick(item) })
            }

            playlistActions?.let {
                add(
                    OverflowMenuOption(
                        title = "Add to Playlist",
                        icon = Icons.AutoMirrored.Filled.PlaylistAdd
                    ) {
                        showPlaylistDialog = true
                        // Load playlists when dialog opens
                        coroutineScope.launch {
                            isLoadingPlaylists = true
                            playlists = it.onLoadPlaylists()
                            isLoadingPlaylists = false
                        }
                    })
            }
        }
    )

    if (showPlaylistDialog) {
        AlertDialog(
            onDismissRequest = {
                showPlaylistDialog = false
                playlists = emptyList()
                isLoadingPlaylists = false
            },
            title = { Text("Add to Playlist") },
            text = {
                if (isLoadingPlaylists) {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                } else if (playlists.isEmpty()) {
                    Text("No editable playlists available")
                } else {
                    Column {
                        playlists.forEach { playlist ->
                            TextButton(
                                onClick = {
                                    playlistActions?.onAddToPlaylist(item, playlist)
                                    showPlaylistDialog = false
                                    playlists = emptyList()
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = playlist.name,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = {
                    showPlaylistDialog = false
                    playlists = emptyList()
                    isLoadingPlaylists = false
                }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Preview
@Composable
private fun Preview(item: AppMediaItem.Album = AppMediaItemFixtures.album()) {
    ItemHeader(item)
}

@Preview
@Composable
private fun PreviewLongTitle() {
    Preview(AppMediaItemFixtures.album(name = "A very long title that is very long oh no it's so long"))
}
