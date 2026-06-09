package io.music_assistant.client.ui.compose.common.items

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.DropdownMenu
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import io.music_assistant.client.data.model.client.QueueOption
import io.music_assistant.client.data.model.client.itemKind
import io.music_assistant.client.data.model.client.items.Album
import io.music_assistant.client.data.model.client.items.AppMediaItem
import io.music_assistant.client.data.model.client.items.PlayableItem
import io.music_assistant.client.data.model.client.items.PodcastEpisode
import io.music_assistant.client.data.model.client.items.RadioStation
import io.music_assistant.client.data.model.client.items.Track
import io.music_assistant.client.settings.ViewMode

typealias PlayHandler<T> = (item: T, queueOption: QueueOption, radio: Boolean, parent: AppMediaItem?) -> Unit

@Composable
fun TrackWithMenu(
    item: Track,
    viewMode: ViewMode = ViewMode.GRID,
    parent: AppMediaItem? = null,
    onPlayOption: PlayHandler<Track>,
    playlistActions: PlaylistActions? = null,
    onRemoveFromPlaylist: (() -> Unit)? = null,
    libraryActions: LibraryActions,
    providerIconFetcher: (@Composable (Modifier, String) -> Unit)?,
) {
    PlayableItemWithMenu(
        modifier = when (viewMode) {
            ViewMode.GRID -> Modifier
            ViewMode.LIST -> Modifier.fillMaxWidth()
        },
        item = item,
        parent = parent,
        onPlayOption = onPlayOption,
        playlistActions = playlistActions,
        onRemoveFromPlaylist = onRemoveFromPlaylist,
        libraryActions = libraryActions,
        itemComposable = { mod, onClick, onLongClick ->
            when (viewMode) {
                ViewMode.LIST -> TrackRowItem(
                    modifier = mod,
                    item = item,
                    isAlbumRow = parent is Album,
                    onClick = onClick,
                    onLongClick = onLongClick,
                    providerIconFetcher = providerIconFetcher,
                )

                ViewMode.GRID -> TrackGridItem(
                    modifier = mod,
                    item = item,
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
    item: PodcastEpisode,
    viewMode: ViewMode = ViewMode.GRID,
    onPlayOption: PlayHandler<PodcastEpisode>,
    playlistActions: PlaylistActions? = null,
    onRemoveFromPlaylist: (() -> Unit)? = null,
    libraryActions: LibraryActions,
    progressActions: ProgressActions? = null,
    providerIconFetcher: (@Composable (Modifier, String) -> Unit)?,
) {
    PlayableItemWithMenu(
        modifier = when (viewMode) {
            ViewMode.GRID -> Modifier
            ViewMode.LIST -> Modifier.fillMaxWidth()
        },
        item = item,
        onPlayOption = onPlayOption,
        playlistActions = playlistActions,
        onRemoveFromPlaylist = onRemoveFromPlaylist,
        libraryActions = libraryActions,
        progressActions = progressActions,
        itemComposable = { mod, onClick, onLongClick ->
            when (viewMode) {
                ViewMode.LIST -> PodcastEpisodeRowItem(
                    modifier = mod,
                    item = item,
                    onClick = onClick,
                    onLongClick = onLongClick,
                    providerIconFetcher = providerIconFetcher,
                )

                ViewMode.GRID -> PodcastEpisodeGridItem(
                    modifier = mod,
                    item = item,
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
    item: RadioStation,
    viewMode: ViewMode = ViewMode.GRID,
    onPlayOption: PlayHandler<RadioStation>,
    playlistActions: PlaylistActions? = null,
    onRemoveFromPlaylist: (() -> Unit)? = null,
    libraryActions: LibraryActions,
    providerIconFetcher: (@Composable (Modifier, String) -> Unit)?,
) {
    PlayableItemWithMenu(
        modifier = when (viewMode) {
            ViewMode.GRID -> Modifier
            ViewMode.LIST -> Modifier.fillMaxWidth()
        },
        item = item,
        onPlayOption = onPlayOption,
        playlistActions = playlistActions,
        onRemoveFromPlaylist = onRemoveFromPlaylist,
        libraryActions = libraryActions,
        itemComposable = { mod, onClick, onLongClick ->
            when (viewMode) {
                ViewMode.LIST -> RadioRowItem(
                    modifier = mod,
                    item = item,
                    onClick = onClick,
                    onLongClick = onLongClick,
                    providerIconFetcher = providerIconFetcher,
                )

                ViewMode.GRID -> RadioGridItem(
                    modifier = mod,
                    item = item,
                    onClick = onClick,
                    onLongClick = onLongClick,
                    providerIconFetcher = providerIconFetcher,
                )
            }
        },
    )
}

/**
 * Playable item with a long-press dropdown menu. Default click plays the item now.
 */
@Composable
private fun <T> PlayableItemWithMenu(
    modifier: Modifier = Modifier,
    item: T,
    parent: AppMediaItem? = null,
    onPlayOption: PlayHandler<T>,
    playlistActions: PlaylistActions? = null,
    onRemoveFromPlaylist: (() -> Unit)? = null,
    libraryActions: LibraryActions,
    progressActions: ProgressActions? = null,
    itemComposable: @Composable (
        modifier: Modifier,
        onClick: (T) -> Unit,
        onLongClick: (T) -> Unit,
    ) -> Unit,
) where T : PlayableItem, T : AppMediaItem {
    var expandedItemId by remember { mutableStateOf<String?>(null) }
    var showPlaylistDialog by rememberSaveable { mutableStateOf(false) }
    var showCustomizeDialog by rememberSaveable { mutableStateOf(false) }

    // The tap action for this item's (kind, context) pair (PLAY_NOW outside customizable
    // screens). Null when the item isn't playable — then a tap opens the menu instead.
    val effectiveDefault = LocalClickActionConfig.current.effectiveActionFor(item)

    val actions = resolveLongClickActions(
        item = item,
        librarySupported = true,
        canAddToPlaylist = playlistActions != null && item.supportsAddToPlaylist,
        canRemoveFromPlaylist = onRemoveFromPlaylist != null,
        progressSupported = progressActions != null && item is PodcastEpisode,
        defaultAction = effectiveDefault,
        parent = parent,
        customizationAllowed = true,
    )

    val runPlayAction: (ItemAction) -> Unit = { action ->
        when (action) {
            is ItemAction.Play -> onPlayOption(item, action.queueOption, false, null)
            ItemAction.StartRadio -> onPlayOption(item, QueueOption.REPLACE, true, null)
            is ItemAction.PlayFromHere -> onPlayOption(item, QueueOption.REPLACE, false, parent)
            else -> Unit
        }
    }

    // Non-playable items keep the long-press menu (favorite, library, …) but can't be played:
    // dim them and route a tap to the menu instead of starting playback.
    val playable = item.isPlayable
    Box(modifier = modifier) {
        itemComposable(
            Modifier.align(Alignment.Center)
                .then(if (playable) Modifier else Modifier.alpha(DISABLED_ITEM_ALPHA)),
            { effectiveDefault?.let(runPlayAction) ?: run { expandedItemId = item.itemId } },
            { expandedItemId = item.itemId },
        )
        DropdownMenu(
            modifier = Modifier.semantics {
                role = Role.DropdownList
            },
            expanded = expandedItemId == item.itemId,
            onDismissRequest = { expandedItemId = null },
        ) {
            ItemActionMenuItems(actions, defaultAction = effectiveDefault) { action ->
                expandedItemId = null
                when (action) {
                    is ItemAction.Play,
                    ItemAction.StartRadio,
                    is ItemAction.PlayFromHere,
                    -> runPlayAction(action)
                    ItemAction.AddToLibrary,
                    ItemAction.RemoveFromLibrary,
                        -> libraryActions.onLibraryClick(item)

                    ItemAction.Favorite,
                    ItemAction.Unfavorite,
                        -> libraryActions.onFavoriteClick(item)

                    ItemAction.AddToPlaylist -> showPlaylistDialog = true
                    ItemAction.RemoveFromPlaylist -> onRemoveFromPlaylist?.invoke()
                    ItemAction.MarkPlayed -> progressActions?.onMarkPlayed(item)
                    ItemAction.MarkUnplayed -> progressActions?.onMarkUnplayed(item)
                    ItemAction.Customize -> showCustomizeDialog = true
                }
            }
        }

        if (showPlaylistDialog && playlistActions != null) {
            AddToPlaylistDialog(
                item = item,
                playlistActions = playlistActions,
                onDismiss = { showPlaylistDialog = false },
            )
        }

        if (showCustomizeDialog) {
            item.itemKind()?.let { kind ->
                DefaultClickActionsDialog(itemKind = kind, onDismiss = { showCustomizeDialog = false })
            }
        }
    }
}
