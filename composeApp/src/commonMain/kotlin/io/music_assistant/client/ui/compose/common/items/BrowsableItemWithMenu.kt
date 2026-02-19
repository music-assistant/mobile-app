package io.music_assistant.client.ui.compose.common.items

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.filled.AddToQueue
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
import io.music_assistant.client.data.model.server.QueueOption
import io.music_assistant.client.ui.compose.common.viewmodel.ActionsViewModel
import kotlinx.coroutines.launch
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Replay

@Composable
fun AlbumWithMenu(
    item: AppMediaItem.Album,
    showSubtitle: Boolean,
    rowMode: Boolean = false,
    onNavigateClick: (AppMediaItem.Album) -> Unit,
    onPlayOption: ((AppMediaItem.Album, QueueOption, Boolean) -> Unit),
    playlistActions: ActionsViewModel.PlaylistActions? = null,
    libraryActions: ActionsViewModel.LibraryActions,
    providerIconFetcher: (@Composable (Modifier, String) -> Unit)?,
    serverUrl: String?
) {
    BrowsableItemWithMenu(
        modifier = if (rowMode) Modifier.fillMaxWidth() else Modifier,
        item = item,
        onNavigateClick = onNavigateClick,
        onPlayOption = onPlayOption,
        playlistActions = playlistActions,
        libraryActions = libraryActions,
    ) { mod, onClick, onLongClick ->
        if (rowMode) {
            AlbumRowItem(
                modifier = mod,
                item = item,
                serverUrl = serverUrl,
                onClick = onClick,
                onLongClick = onLongClick,
                providerIconFetcher = providerIconFetcher
            )
        } else {
            AlbumGridItem(
                item = item,
                showSubtitle = showSubtitle,
                serverUrl = serverUrl,
                onClick = onClick,
                onLongClick = onLongClick,
                providerIconFetcher = providerIconFetcher
            )
        }
    }
}

@Composable
fun ArtistWithMenu(
    item: AppMediaItem.Artist,
    showSubtitle: Boolean,
    rowMode: Boolean = false,
    onNavigateClick: (AppMediaItem.Artist) -> Unit,
    onPlayOption: ((AppMediaItem.Artist, QueueOption, Boolean) -> Unit),
    playlistActions: ActionsViewModel.PlaylistActions? = null,
    libraryActions: ActionsViewModel.LibraryActions,
    providerIconFetcher: (@Composable (Modifier, String) -> Unit)?,
    serverUrl: String?
) {
    BrowsableItemWithMenu(
        modifier = if (rowMode) Modifier.fillMaxWidth() else Modifier,
        item = item,
        onNavigateClick = onNavigateClick,
        onPlayOption = onPlayOption,
        playlistActions = playlistActions,
        libraryActions = libraryActions,
    ) { mod, onClick, onLongClick ->
        if (rowMode) {
            ArtistRowItem(
                modifier = mod,
                item = item,
                serverUrl = serverUrl,
                onClick = onClick,
                onLongClick = onLongClick,
                providerIconFetcher = providerIconFetcher
            )
        } else {
            ArtistGridItem(
                item = item,
                showSubtitle = showSubtitle,
                serverUrl = serverUrl,
                onClick = onClick,
                onLongClick = onLongClick,
                providerIconFetcher = providerIconFetcher
            )
        }
    }
}

@Composable
fun PlaylistWithMenu(
    item: AppMediaItem.Playlist,
    showSubtitle: Boolean,
    rowMode: Boolean = false,
    onNavigateClick: (AppMediaItem.Playlist) -> Unit,
    onPlayOption: ((AppMediaItem.Playlist, QueueOption, Boolean) -> Unit),
    playlistActions: ActionsViewModel.PlaylistActions? = null,
    libraryActions: ActionsViewModel.LibraryActions,
    providerIconFetcher: (@Composable (Modifier, String) -> Unit)?,
    serverUrl: String?
) {
    BrowsableItemWithMenu(
        modifier = if (rowMode) Modifier.fillMaxWidth() else Modifier,
        item = item,
        onNavigateClick = onNavigateClick,
        onPlayOption = onPlayOption,
        playlistActions = playlistActions,
        libraryActions = libraryActions,
    ) { mod, onClick, onLongClick ->
        if (rowMode) {
            PlaylistRowItem(
                modifier = mod,
                item = item,
                serverUrl = serverUrl,
                onClick = onClick,
                onLongClick = onLongClick,
                providerIconFetcher = providerIconFetcher
            )
        } else {
            PlaylistGridItem(
                item = item,
                showSubtitle = showSubtitle,
                serverUrl = serverUrl,
                onClick = onClick,
                onLongClick = onLongClick,
                providerIconFetcher = providerIconFetcher
            )
        }
    }
}

@Composable
fun AudiobookWithMenu(
    item: AppMediaItem.Audiobook,
    showSubtitle: Boolean,
    rowMode: Boolean = false,
    onNavigateClick: (AppMediaItem.Audiobook) -> Unit,
    onPlayOption: ((AppMediaItem.Audiobook, QueueOption, Boolean) -> Unit),
    playlistActions: ActionsViewModel.PlaylistActions? = null,
    libraryActions: ActionsViewModel.LibraryActions,
    progressActions: ActionsViewModel.ProgressActions? = null,
    providerIconFetcher: (@Composable (Modifier, String) -> Unit)?,
    serverUrl: String?
) {
    BrowsableItemWithMenu(
        modifier = if (rowMode) Modifier.fillMaxWidth() else Modifier,
        item = item,
        onNavigateClick = onNavigateClick,
        onPlayOption = onPlayOption,
        playlistActions = playlistActions,
        libraryActions = libraryActions,
        progressActions = progressActions,
    ) { mod, onClick, onLongClick ->
        if (rowMode) {
            AudiobookRowItem(
                modifier = mod,
                item = item,
                serverUrl = serverUrl,
                onClick = onClick,
                onLongClick = onLongClick,
                providerIconFetcher = providerIconFetcher
            )
        } else {
            AudiobookGridItem(
                item = item,
                showSubtitle = showSubtitle,
                serverUrl = serverUrl,
                onClick = onClick,
                onLongClick = onLongClick,
                providerIconFetcher = providerIconFetcher
            )
        }
    }
}

@Composable
fun PodcastWithMenu(
    item: AppMediaItem.Podcast,
    showSubtitle: Boolean,
    rowMode: Boolean = false,
    onNavigateClick: (AppMediaItem.Podcast) -> Unit,
    onPlayOption: ((AppMediaItem.Podcast, QueueOption, Boolean) -> Unit),
    playlistActions: ActionsViewModel.PlaylistActions? = null,
    libraryActions: ActionsViewModel.LibraryActions,
    providerIconFetcher: (@Composable (Modifier, String) -> Unit)?,
    serverUrl: String?
) {
    BrowsableItemWithMenu(
        modifier = if (rowMode) Modifier.fillMaxWidth() else Modifier,
        item = item,
        onNavigateClick = onNavigateClick,
        onPlayOption = onPlayOption,
        playlistActions = playlistActions,
        libraryActions = libraryActions,
    ) { mod, onClick, onLongClick ->
        if (rowMode) {
            PodcastRowItem(
                modifier = mod,
                item = item,
                serverUrl = serverUrl,
                onClick = onClick,
                onLongClick = onLongClick,
                providerIconFetcher = providerIconFetcher
            )
        } else {
            PodcastGridItem(
                item = item,
                showSubtitle = showSubtitle,
                serverUrl = serverUrl,
                onClick = onClick,
                onLongClick = onLongClick,
                providerIconFetcher = providerIconFetcher
            )
        }
    }
}


@Composable
private fun <T : AppMediaItem> BrowsableItemWithMenu(
    modifier: Modifier = Modifier,
    item: T,
    onNavigateClick: (T) -> Unit,
    onPlayOption: ((T, QueueOption, Boolean) -> Unit),
    playlistActions: ActionsViewModel.PlaylistActions? = null,
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
            onNavigateClick,
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
                        (item as? AppMediaItem)?.let {
                            libraryActions.onFavoriteClick(it)
                            expandedItemId = null
                        }
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

            if (playlistActions != null && (item is AppMediaItem.Album || item is AppMediaItem.Artist)) {
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

            // Mark played/unplayed (audiobooks)
            if (progressActions != null && item is AppMediaItem.Audiobook) {
                val isPlayed = item.fullyPlayed == true
                DropdownMenuItem(
                    text = { Text(if (isPlayed) "Mark as unplayed" else "Mark as played") },
                    onClick = {
                        if (isPlayed) {
                            progressActions.onMarkUnplayed(item)
                        } else {
                            progressActions.onMarkPlayed(item)
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


