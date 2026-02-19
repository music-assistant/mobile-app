package io.music_assistant.client.ui.compose.common.items

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.filled.AddToQueue
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlaylistAddCircle
import androidx.compose.material.icons.filled.QueuePlayNext
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
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
import androidx.compose.ui.unit.dp
import compose.icons.TablerIcons
import compose.icons.tablericons.FolderMinus
import compose.icons.tablericons.FolderPlus
import compose.icons.tablericons.Heart
import compose.icons.tablericons.HeartBroken
import io.music_assistant.client.data.model.client.AppMediaItem
import io.music_assistant.client.data.model.client.PlayableItem
import io.music_assistant.client.data.model.server.QueueOption
import io.music_assistant.client.ui.compose.common.viewmodel.ActionsViewModel
import kotlinx.coroutines.launch
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Replay

@Composable
fun TrackWithMenu(
    item: AppMediaItem.Track,
    rowMode: Boolean = false,
    onPlayOption: ((AppMediaItem.Track, QueueOption, Boolean) -> Unit),
    playlistActions: ActionsViewModel.PlaylistActions? = null,
    onRemoveFromPlaylist: (() -> Unit)? = null,
    libraryActions: ActionsViewModel.LibraryActions,
    providerIconFetcher: (@Composable (Modifier, String) -> Unit)?,
    serverUrl: String?,
) {
    PlayableItemWithMenu(
        modifier = if (rowMode) Modifier.fillMaxWidth() else Modifier,
        item = item,
        onPlayOption = onPlayOption,
        playlistActions = playlistActions,
        onRemoveFromPlaylist = onRemoveFromPlaylist,
        libraryActions = libraryActions,
        itemComposable = { mod, onClick, onLongClick ->
            if (rowMode) {
                TrackRowItem(
                    modifier = mod,
                    item = item,
                    serverUrl = serverUrl,
                    onClick = onClick,
                    onLongClick = onLongClick,
                    providerIconFetcher = providerIconFetcher
                )
            } else {
                TrackGridItem(
                    modifier = mod,
                    item = item,
                    serverUrl = serverUrl,
                    onClick = onClick,
                    onLongClick = onLongClick,
                    showSubtitle = true,
                    providerIconFetcher = providerIconFetcher
                )
            }
        }
    )
}

@Composable
fun PodcastEpisodeWithMenu(
    item: AppMediaItem.PodcastEpisode,
    rowMode: Boolean = false,
    onPlayOption: ((AppMediaItem.PodcastEpisode, QueueOption, Boolean) -> Unit),
    playlistActions: ActionsViewModel.PlaylistActions? = null,
    onRemoveFromPlaylist: (() -> Unit)? = null,
    libraryActions: ActionsViewModel.LibraryActions,
    progressActions: ActionsViewModel.ProgressActions? = null,
    providerIconFetcher: (@Composable (Modifier, String) -> Unit)?,
    serverUrl: String?,
) {
    PlayableItemWithMenu(
        modifier = if (rowMode) Modifier.fillMaxWidth() else Modifier,
        item = item,
        onPlayOption = onPlayOption,
        playlistActions = playlistActions,
        onRemoveFromPlaylist = onRemoveFromPlaylist,
        libraryActions = libraryActions,
        progressActions = progressActions,
        itemComposable = { mod, onClick, onLongClick ->
            if (rowMode) {
                PodcastEpisodeRowItem(
                    modifier = mod,
                    item = item,
                    serverUrl = serverUrl,
                    onClick = onClick,
                    onLongClick = onLongClick,
                    providerIconFetcher = providerIconFetcher
                )
            } else {
                PodcastEpisodeGridItem(
                    modifier = mod,
                    item = item,
                    serverUrl = serverUrl,
                    onClick = onClick,
                    onLongClick = onLongClick,
                    showSubtitle = true,
                    providerIconFetcher = providerIconFetcher
                )
            }
        }
    )
}

@Composable
fun RadioWithMenu(
    item: AppMediaItem.RadioStation,
    rowMode: Boolean = false,
    onPlayOption: ((AppMediaItem.RadioStation, QueueOption, Boolean) -> Unit),
    playlistActions: ActionsViewModel.PlaylistActions? = null,
    onRemoveFromPlaylist: (() -> Unit)? = null,
    libraryActions: ActionsViewModel.LibraryActions,
    providerIconFetcher: (@Composable (Modifier, String) -> Unit)?,
    serverUrl: String?,
) {
    PlayableItemWithMenu(
        modifier = if (rowMode) Modifier.fillMaxWidth() else Modifier,
        item = item,
        onPlayOption = onPlayOption,
        playlistActions = playlistActions,
        onRemoveFromPlaylist = onRemoveFromPlaylist,
        libraryActions = libraryActions,
        itemComposable = { mod, onClick, onLongClick ->
            if (rowMode) {
                RadioRowItem(
                    modifier = mod,
                    item = item,
                    serverUrl = serverUrl,
                    onClick = onClick,
                    onLongClick = onLongClick,
                    providerIconFetcher = providerIconFetcher
                )
            } else {
                RadioGridItem(
                    modifier = mod,
                    item = item,
                    serverUrl = serverUrl,
                    onClick = onClick,
                    onLongClick = onLongClick,
                    showSubtitle = true,
                    providerIconFetcher = providerIconFetcher
                )
            }
        }
    )
}

/**
 * A reusable composable that displays a playable item with a dropdown menu for queue actions.
 * The menu includes options to play now, insert next, add to queue, start radio, and manage library/favorites.
 * It also handles adding to playlists if playlist actions are provided.
 * Default click plays it now.
 */
@Composable
private fun <T : PlayableItem> PlayableItemWithMenu(
    modifier: Modifier = Modifier,
    item: T,
    onPlayOption: ((T, QueueOption, Boolean) -> Unit),
    playlistActions: ActionsViewModel.PlaylistActions? = null,
    onRemoveFromPlaylist: (() -> Unit)? = null,
    libraryActions: ActionsViewModel.LibraryActions,
    progressActions: ActionsViewModel.ProgressActions? = null,
    itemComposable: @Composable (
        modifier: Modifier,
        onClick: (T) -> Unit,
        onLongClick: (T) -> Unit,
    ) -> Unit
) {
    var expandedItemId by remember { mutableStateOf<String?>(null) }
    var showPlaylistDialog by rememberSaveable { mutableStateOf(false) }
    var playlists by remember { mutableStateOf<List<AppMediaItem.Playlist>>(emptyList()) }
    var isLoadingPlaylists by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    Box(modifier = modifier) {
        itemComposable(
            Modifier.align(Alignment.Center),
            { onPlayOption(item, QueueOption.REPLACE, false) },
            { expandedItemId = item.itemId },
        )
        DropdownMenu(
            expanded = expandedItemId == item.itemId,
            onDismissRequest = { expandedItemId = null }
        ) {
            DropdownMenuItem(
                text = { Text("Play now") },
                onClick = {
                    onPlayOption(item, QueueOption.REPLACE, false)
                    expandedItemId = null
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Play now"
                    )
                }
            )
            DropdownMenuItem(
                text = { Text("Insert next and play") },
                onClick = {
                    onPlayOption(item, QueueOption.PLAY, false)
                    expandedItemId = null
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.PlaylistAddCircle,
                        contentDescription = "Insert next and play"
                    )
                }
            )
            DropdownMenuItem(
                text = { Text("Insert next") },
                onClick = {
                    onPlayOption(item, QueueOption.NEXT, false)
                    expandedItemId = null
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.QueuePlayNext,
                        contentDescription = "Insert next"
                    )
                }
            )
            DropdownMenuItem(
                text = { Text("Add to bottom") },
                onClick = {
                    onPlayOption(item, QueueOption.ADD, false)
                    expandedItemId = null
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.AddToQueue,
                        contentDescription = "Add to bottom"
                    )
                }
            )
            if (item.canStartRadio) {
                DropdownMenuItem(
                    text = { Text("Start radio") },
                    onClick = {
                        onPlayOption(item, QueueOption.REPLACE, true)
                        expandedItemId = null
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Radio,
                            contentDescription = "Start radio"
                        )
                    }
                )
            }

            val libText = if (item.isInLibrary) "Remove from library" else "Add to library"
            DropdownMenuItem(
                text = { Text(libText) },
                onClick = {
                    libraryActions.onLibraryClick(item as AppMediaItem)
                    expandedItemId = null
                },
                leadingIcon = {
                    Icon(
                        imageVector =
                            if (item.isInLibrary) TablerIcons.FolderMinus
                            else TablerIcons.FolderPlus,
                        contentDescription = libText
                    )
                }
            )


            // Favorite management (only for library items)
            if (item.isInLibrary) {
                val favText = if (item.favorite == true) "Unfavorite" else "Favorite"
                DropdownMenuItem(
                    text = { Text(favText) },
                    onClick = {
                        libraryActions.onFavoriteClick(item as AppMediaItem)
                        expandedItemId = null
                    },
                    leadingIcon = {
                        Icon(
                            imageVector =
                                if (item.favorite == true) TablerIcons.HeartBroken
                                else TablerIcons.Heart,
                            contentDescription = favText
                        )
                    }
                )
            }

            if (playlistActions != null && item is AppMediaItem.Track) {
                DropdownMenuItem(
                    text = { Text("Add to playlist") },
                    onClick = {
                        showPlaylistDialog = true
                        expandedItemId = null
                        // Load playlists when dialog opens
                        coroutineScope.launch {
                            isLoadingPlaylists = true
                            playlists = playlistActions.onLoadPlaylists()
                            isLoadingPlaylists = false
                        }
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.PlaylistAdd,
                            contentDescription = "Add to playlist"
                        )
                    }
                )
            }
            if (onRemoveFromPlaylist != null) {
                DropdownMenuItem(
                    text = { Text("Remove from playlist") },
                    onClick = {
                        onRemoveFromPlaylist()
                        expandedItemId = null
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Remove from playlist"
                        )
                    }
                )
            }

            // Mark played/unplayed (podcast episodes)
            if (progressActions != null && item is AppMediaItem.PodcastEpisode) {
                val isPlayed = item.fullyPlayed == true
                DropdownMenuItem(
                    text = { Text(if (isPlayed) "Mark as unplayed" else "Mark as played") },
                    onClick = {
                        if (isPlayed) {
                            progressActions.onMarkUnplayed(item as AppMediaItem)
                        } else {
                            progressActions.onMarkPlayed(item as AppMediaItem)
                        }
                        expandedItemId = null
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = if (isPlayed) Icons.Default.Replay else Icons.Default.Check,
                            contentDescription = if (isPlayed) "Mark as unplayed" else "Mark as played"
                        )
                    }
                )
            }
        }

        // Add to Playlist Dialog
        if (showPlaylistDialog && item is AppMediaItem.Track) {
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
                                        playlistActions?.onAddToPlaylist
                                            ?.invoke(item, playlist)
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
}


