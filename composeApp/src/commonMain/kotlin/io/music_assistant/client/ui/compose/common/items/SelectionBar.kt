package io.music_assistant.client.ui.compose.common.items

import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarState
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import io.music_assistant.client.data.model.client.items.AppMediaItem
import io.music_assistant.client.data.play.PlayMode
import io.music_assistant.client.nav.BackHandler
import io.music_assistant.client.ui.compose.common.ConfirmationDialog
import io.music_assistant.client.ui.compose.common.LocalGrantedScopes
import musicassistantclient.composeapp.generated.resources.Res
import musicassistantclient.composeapp.generated.resources.action_remove
import musicassistantclient.composeapp.generated.resources.dialog_remove_from_playlist_title
import musicassistantclient.composeapp.generated.resources.dialog_remove_items_from_playlist_message
import musicassistantclient.composeapp.generated.resources.selection_actions
import musicassistantclient.composeapp.generated.resources.selection_cancel
import musicassistantclient.composeapp.generated.resources.selection_count
import musicassistantclient.composeapp.generated.resources.selection_select_all
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/**
 * The top bar of a screen while [selection] is active: cancel, the count, "Select all" (while a
 * shown item is not selected) and the actions menu.
 * An action ends the mode. Back also ends it. [onRemoveFromPlaylist] adds "Remove from playlist",
 * which asks for a confirmation first.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SelectionBar(
    selection: ItemSelection,
    onPlay: (List<AppMediaItem>, PlayMode) -> Unit,
    playlistActions: PlaylistActions?,
    onRemoveFromPlaylist: ((List<AppMediaItem>) -> Unit)? = null,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    var showPlaylistDialog by remember { mutableStateOf(false) }
    var showRemoveConfirmation by remember { mutableStateOf(false) }
    val actions = resolveSelectionActions(
        canEditLibrary = playlistActions != null && LocalGrantedScopes.current.canEditLibrary,
        canRemoveFromPlaylist = onRemoveFromPlaylist != null,
    )
    val count = selection.items.size

    BackHandler(enabled = true) { selection.clear() }

    TopAppBar(
        navigationIcon = {
            IconButton(onClick = selection::clear) {
                Icon(Icons.Default.Close, stringResource(Res.string.selection_cancel))
            }
        },
        title = { Text(pluralStringResource(Res.plurals.selection_count, count, count)) },
        actions = {
            if (selection.canSelectAll) {
                IconButton(onClick = selection::selectAll) {
                    Icon(Icons.Default.SelectAll, stringResource(Res.string.selection_select_all))
                }
            }
            Box {
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(Icons.Default.MoreVert, stringResource(Res.string.selection_actions))
                }
                DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                    ItemActionMenuItems(clickContext = null, actions = actions) { action ->
                        menuExpanded = false
                        when (action) {
                            ItemAction.AddToPlaylist -> showPlaylistDialog = true
                            ItemAction.RemoveFromPlaylist -> showRemoveConfirmation = true
                            else -> action.toPlayMode()?.let { mode ->
                                onPlay(selection.items, mode)
                                selection.clear()
                            }
                        }
                    }
                }
            }
        },
    )

    playlistActions?.takeIf { showPlaylistDialog }?.let {
        AddToPlaylistDialog(
            items = selection.items,
            playlistActions = it,
            onDismiss = { showPlaylistDialog = false },
            onAdded = selection::clear,
        )
    }

    onRemoveFromPlaylist?.takeIf { showRemoveConfirmation }?.let { remove ->
        ConfirmationDialog(
            title = stringResource(Res.string.dialog_remove_from_playlist_title),
            message = pluralStringResource(Res.plurals.dialog_remove_items_from_playlist_message, count, count),
            confirmLabel = stringResource(Res.string.action_remove),
            onConfirm = {
                remove(selection.items)
                selection.clear()
            },
            onDismiss = { showRemoveConfirmation = false },
        )
    }
}

/**
 * The state of a collapsing top bar that expands when [selection] becomes active, so the
 * selection bar shows also after the list scrolled the top bar away.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun rememberSelectionTopAppBarState(selection: ItemSelection?): TopAppBarState {
    val state = rememberTopAppBarState()
    val active = selection?.isActive == true
    LaunchedEffect(active) {
        if (active) state.heightOffset = 0f
    }
    return state
}
