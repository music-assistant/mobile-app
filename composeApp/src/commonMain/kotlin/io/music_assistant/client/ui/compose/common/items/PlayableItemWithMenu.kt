package io.music_assistant.client.ui.compose.common.items

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.filled.AddToQueue
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlaylistAddCircle
import androidx.compose.material.icons.filled.QueuePlayNext
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.Replay
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
import io.music_assistant.client.ui.compose.common.icons.PlayIcon
import io.music_assistant.client.ui.compose.common.viewmodel.ActionsViewModel
import kotlinx.coroutines.launch
import musicassistantclient.composeapp.generated.resources.*
import musicassistantclient.composeapp.generated.resources.Res
import org.jetbrains.compose.resources.stringResource

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
                    providerIconFetcher = providerIconFetcher,
                )
            } else {
                TrackGridItem(
                    modifier = mod,
                    item = item,
                    serverUrl = serverUrl,
                    onClick = onClick,
                    onLongClick = onLongClick,
                    providerIconFetcher = providerIconFetcher,
                )
            }
        },
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
                    providerIconFetcher = providerIconFetcher,
                )
            } else {
                PodcastEpisodeGridItem(
                    modifier = mod,
                    item = item,
                    serverUrl = serverUrl,
                    onClick = onClick,
                    onLongClick = onLongClick,
                    providerIconFetcher = providerIconFetcher,
                )
            }
        },
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
                    providerIconFetcher = providerIconFetcher,
                )
            } else {
                RadioGridItem(
                    modifier = mod,
                    item = item,
                    serverUrl = serverUrl,
                    onClick = onClick,
                    onLongClick = onLongClick,
                    providerIconFetcher = providerIconFetcher,
                )
            }
        },
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
    ) -> Unit,
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
            onDismissRequest = { expandedItemId = null },
        ) {
            DropdownMenuItem(
                text = { Text(stringResource(Res.string.action_play_now)) },
                onClick = {
                    onPlayOption(item, QueueOption.REPLACE, false)
                    expandedItemId = null
                },
                leadingIcon = {
                    Icon(
                        imageVector = PlayIcon,
                        contentDescription = stringResource(Res.string.action_play_now),
                    )
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(Res.string.action_insert_next_and_play)) },
                onClick = {
                    onPlayOption(item, QueueOption.PLAY, false)
                    expandedItemId = null
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.PlaylistAddCircle,
                        contentDescription = stringResource(Res.string.action_insert_next_and_play),
                    )
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(Res.string.action_insert_next)) },
                onClick = {
                    onPlayOption(item, QueueOption.NEXT, false)
                    expandedItemId = null
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.QueuePlayNext,
                        contentDescription = stringResource(Res.string.action_insert_next),
                    )
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(Res.string.action_add_to_bottom)) },
                onClick = {
                    onPlayOption(item, QueueOption.ADD, false)
                    expandedItemId = null
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.AddToQueue,
                        contentDescription = stringResource(Res.string.action_add_to_bottom),
                    )
                },
            )
            if (item.canStartRadio) {
                DropdownMenuItem(
                    text = { Text(stringResource(Res.string.action_start_radio)) },
                    onClick = {
                        onPlayOption(item, QueueOption.REPLACE, true)
                        expandedItemId = null
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Radio,
                            contentDescription = stringResource(Res.string.action_start_radio),
                        )
                    },
                )
            }

            val libText = if (item.isInLibrary) {
                stringResource(
                    Res.string.action_remove_from_library,
                )
            } else {
                stringResource(Res.string.action_add_to_library)
            }
            DropdownMenuItem(
                text = { Text(libText) },
                onClick = {
                    libraryActions.onLibraryClick(item as AppMediaItem)
                    expandedItemId = null
                },
                leadingIcon = {
                    Icon(
                        imageVector =
                            if (item.isInLibrary) {
                                TablerIcons.FolderMinus
                            } else {
                                TablerIcons.FolderPlus
                            },
                        contentDescription = libText,
                    )
                },
            )

            // Favorite management (only for library items)
            if (item.isInLibrary) {
                val favText = if (item.favorite == true) {
                    stringResource(
                        Res.string.action_unfavorite,
                    )
                } else {
                    stringResource(Res.string.action_favorite)
                }
                DropdownMenuItem(
                    text = { Text(favText) },
                    onClick = {
                        libraryActions.onFavoriteClick(item as AppMediaItem)
                        expandedItemId = null
                    },
                    leadingIcon = {
                        Icon(
                            imageVector =
                                if (item.favorite == true) {
                                    TablerIcons.HeartBroken
                                } else {
                                    TablerIcons.Heart
                                },
                            contentDescription = favText,
                        )
                    },
                )
            }

            if (playlistActions != null && item is AppMediaItem.Track) {
                DropdownMenuItem(
                    text = { Text(stringResource(Res.string.action_add_to_playlist)) },
                    onClick = {
                        showPlaylistDialog = true
                        expandedItemId = null
                        // Load playlists when dialogue opens
                        coroutineScope.launch {
                            isLoadingPlaylists = true
                            playlists = playlistActions.onLoadPlaylists()
                            isLoadingPlaylists = false
                        }
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.PlaylistAdd,
                            contentDescription = stringResource(Res.string.action_add_to_playlist),
                        )
                    },
                )
            }
            if (onRemoveFromPlaylist != null) {
                DropdownMenuItem(
                    text = { Text(stringResource(Res.string.action_remove_from_playlist)) },
                    onClick = {
                        onRemoveFromPlaylist()
                        expandedItemId = null
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = stringResource(Res.string.action_remove_from_playlist),
                        )
                    },
                )
            }

            // Mark played/unplayed (podcast episodes)
            if (progressActions != null && item is AppMediaItem.PodcastEpisode) {
                val isPlayed = item.fullyPlayed == true
                DropdownMenuItem(
                    text = {
                        Text(
                        if (isPlayed) {
                            stringResource(
                                Res.string.action_mark_unplayed,
                            )
                        } else {
                            stringResource(Res.string.action_mark_played)
                        },
                    )
                    },
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
                            contentDescription = if (isPlayed) {
                                stringResource(
                                    Res.string.action_mark_unplayed,
                                )
                            } else {
                                stringResource(Res.string.action_mark_played)
                            },
                        )
                    },
                )
            }
        }

        // Add to Playlist Dialogue
        if (showPlaylistDialog && item is AppMediaItem.Track) {
            AlertDialog(
                onDismissRequest = {
                    showPlaylistDialog = false
                    playlists = emptyList()
                    isLoadingPlaylists = false
                },
                title = { Text(stringResource(Res.string.playlist_add_to_title)) },
                text = {
                    if (isLoadingPlaylists) {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator()
                        }
                    } else if (playlists.isEmpty()) {
                        Text(stringResource(Res.string.playlist_no_editable))
                    } else {
                        LazyColumn {
                            items(
                                items = playlists,
                                key = { p -> p.itemId },
                            ) { playlist ->
                                TextButton(
                                    onClick = {
                                        playlistActions?.onAddToPlaylist
                                            ?.invoke(item, playlist)
                                        showPlaylistDialog = false
                                        playlists = emptyList()
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Text(
                                        text = playlist.name,
                                        modifier = Modifier.fillMaxWidth(),
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
                        Text(stringResource(Res.string.common_cancel))
                    }
                },
            )
        }
    }
}
