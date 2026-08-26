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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import io.music_assistant.client.data.model.client.QueueOption
import io.music_assistant.client.data.model.client.items.Album
import io.music_assistant.client.data.model.client.items.AppMediaItem
import io.music_assistant.client.data.model.client.items.Artist
import io.music_assistant.client.data.model.client.items.Audiobook
import io.music_assistant.client.data.model.client.items.Genre
import io.music_assistant.client.data.model.client.items.Playlist
import io.music_assistant.client.data.model.client.items.Podcast
import io.music_assistant.client.settings.ViewMode
import io.music_assistant.client.ui.compose.common.RemoveFromLibraryConfirmationDialog
import io.music_assistant.client.ui.compose.common.tvFocusRing

@Composable
fun AlbumWithMenu(
    item: Album,
    viewMode: ViewMode = ViewMode.GRID,
    onNavigateClick: (Album) -> Unit,
    onPlayOption: PlayHandler<Album>,
    playlistActions: PlaylistActions? = null,
    libraryActions: LibraryActions,
    providerIconFetcher: (@Composable (Modifier, String) -> Unit)?,
    firstItemFocusRequester: FocusRequester? = null,
) {
    BrowsableItemWithMenu(
        modifier = when (viewMode) {
            ViewMode.GRID -> Modifier
            ViewMode.LIST -> Modifier.fillMaxWidth()
        },
        item = item,
        onNavigateClick = onNavigateClick,
        onPlayOption = onPlayOption,
        playlistActions = playlistActions,
        libraryActions = libraryActions,
        firstItemFocusRequester = firstItemFocusRequester,
    ) { mod, onClick, onLongClick ->
        when (viewMode) {
            ViewMode.LIST -> AlbumRowItem(
                modifier = mod,
                item = item,
                onClick = onClick,
                onLongClick = onLongClick,
                providerIconFetcher = providerIconFetcher,
            )
            ViewMode.GRID -> AlbumGridItem(
                modifier = mod,
                item = item,
                onClick = onClick,
                onLongClick = onLongClick,
                providerIconFetcher = providerIconFetcher,
            )
        }
    }
}

@Composable
fun ArtistWithMenu(
    item: Artist,
    viewMode: ViewMode = ViewMode.GRID,
    onNavigateClick: (Artist) -> Unit,
    onPlayOption: PlayHandler<Artist>,
    libraryActions: LibraryActions,
    providerIconFetcher: (@Composable (Modifier, String) -> Unit)?,
    firstItemFocusRequester: FocusRequester? = null,
) {
    BrowsableItemWithMenu(
        modifier = when (viewMode) {
            ViewMode.GRID -> Modifier
            ViewMode.LIST -> Modifier.fillMaxWidth()
        },
        item = item,
        onNavigateClick = onNavigateClick,
        onPlayOption = onPlayOption,
        libraryActions = libraryActions,
        firstItemFocusRequester = firstItemFocusRequester,
    ) { mod, onClick, onLongClick ->
        when (viewMode) {
            ViewMode.LIST -> ArtistRowItem(
                modifier = mod,
                item = item,
                onClick = onClick,
                onLongClick = onLongClick,
                providerIconFetcher = providerIconFetcher,
            )
            ViewMode.GRID -> ArtistGridItem(
                modifier = mod,
                item = item,
                onClick = onClick,
                onLongClick = onLongClick,
                providerIconFetcher = providerIconFetcher,
            )
        }
    }
}

@Composable
fun PlaylistWithMenu(
    item: Playlist,
    viewMode: ViewMode = ViewMode.GRID,
    onNavigateClick: (Playlist) -> Unit,
    onPlayOption: PlayHandler<Playlist>,
    libraryActions: LibraryActions,
    providerIconFetcher: (@Composable (Modifier, String) -> Unit)?,
    firstItemFocusRequester: FocusRequester? = null,
) {
    BrowsableItemWithMenu(
        modifier = when (viewMode) {
            ViewMode.GRID -> Modifier
            ViewMode.LIST -> Modifier.fillMaxWidth()
        },
        item = item,
        onNavigateClick = onNavigateClick,
        onPlayOption = onPlayOption,
        libraryActions = libraryActions,
        firstItemFocusRequester = firstItemFocusRequester,
    ) { mod, onClick, onLongClick ->
        when (viewMode) {
            ViewMode.LIST -> PlaylistRowItem(
                modifier = mod,
                item = item,
                onClick = onClick,
                onLongClick = onLongClick,
                providerIconFetcher = providerIconFetcher,
            )
            ViewMode.GRID -> PlaylistGridItem(
                modifier = mod,
                item = item,
                onClick = onClick,
                onLongClick = onLongClick,
                providerIconFetcher = providerIconFetcher,
            )
        }
    }
}

@Composable
fun AudiobookWithMenu(
    item: Audiobook,
    viewMode: ViewMode = ViewMode.GRID,
    onNavigateClick: (Audiobook) -> Unit,
    onPlayOption: PlayHandler<Audiobook>,
    playlistActions: PlaylistActions? = null,
    libraryActions: LibraryActions,
    progressActions: ProgressActions? = null,
    providerIconFetcher: (@Composable (Modifier, String) -> Unit)?,
    firstItemFocusRequester: FocusRequester? = null,
) {
    BrowsableItemWithMenu(
        modifier = when (viewMode) {
            ViewMode.GRID -> Modifier
            ViewMode.LIST -> Modifier.fillMaxWidth()
        },
        item = item,
        onNavigateClick = onNavigateClick,
        onPlayOption = onPlayOption,
        playlistActions = playlistActions,
        libraryActions = libraryActions,
        progressActions = progressActions,
        firstItemFocusRequester = firstItemFocusRequester,
    ) { mod, onClick, onLongClick ->
        when (viewMode) {
            ViewMode.LIST -> AudiobookRowItem(
                modifier = mod,
                item = item,
                onClick = onClick,
                onLongClick = onLongClick,
                providerIconFetcher = providerIconFetcher,
            )
            ViewMode.GRID -> AudiobookGridItem(
                modifier = mod,
                item = item,
                onClick = onClick,
                onLongClick = onLongClick,
                providerIconFetcher = providerIconFetcher,
            )
        }
    }
}

@Composable
fun GenreWithMenu(
    item: Genre,
    viewMode: ViewMode = ViewMode.GRID,
    onNavigateClick: (Genre) -> Unit,
    onPlayOption: PlayHandler<Genre>,
    libraryActions: LibraryActions,
    providerIconFetcher: (@Composable (Modifier, String) -> Unit)?,
    firstItemFocusRequester: FocusRequester? = null,
) {
    BrowsableItemWithMenu(
        modifier = when (viewMode) {
            ViewMode.GRID -> Modifier
            ViewMode.LIST -> Modifier.fillMaxWidth()
        },
        item = item,
        onNavigateClick = onNavigateClick,
        onPlayOption = onPlayOption,
        libraryActions = libraryActions,
        firstItemFocusRequester = firstItemFocusRequester,
    ) { mod, onClick, onLongClick ->
        when (viewMode) {
            ViewMode.LIST -> GenreRowItem(
                modifier = mod,
                item = item,
                onClick = onClick,
                onLongClick = onLongClick,
                providerIconFetcher = providerIconFetcher,
            )
            ViewMode.GRID -> GenreGridItem(
                modifier = mod,
                item = item,
                onClick = onClick,
                onLongClick = onLongClick,
                providerIconFetcher = providerIconFetcher,
            )
        }
    }
}

@Composable
fun PodcastWithMenu(
    item: Podcast,
    viewMode: ViewMode = ViewMode.GRID,
    onNavigateClick: (Podcast) -> Unit,
    onPlayOption: PlayHandler<Podcast>,
    libraryActions: LibraryActions,
    providerIconFetcher: (@Composable (Modifier, String) -> Unit)?,
    firstItemFocusRequester: FocusRequester? = null,
) {
    BrowsableItemWithMenu(
        modifier = when (viewMode) {
            ViewMode.GRID -> Modifier
            ViewMode.LIST -> Modifier.fillMaxWidth()
        },
        item = item,
        onNavigateClick = onNavigateClick,
        onPlayOption = onPlayOption,
        libraryActions = libraryActions,
        firstItemFocusRequester = firstItemFocusRequester,
    ) { mod, onClick, onLongClick ->
        when (viewMode) {
            ViewMode.LIST -> PodcastRowItem(
                modifier = mod,
                item = item,
                onClick = onClick,
                onLongClick = onLongClick,
                providerIconFetcher = providerIconFetcher,
            )
            ViewMode.GRID -> PodcastGridItem(
                modifier = mod,
                item = item,
                onClick = onClick,
                onLongClick = onLongClick,
                providerIconFetcher = providerIconFetcher,
            )
        }
    }
}

@Composable
private fun <T : AppMediaItem> BrowsableItemWithMenu(
    modifier: Modifier = Modifier,
    item: T,
    onNavigateClick: (T) -> Unit,
    onPlayOption: PlayHandler<T>,
    playlistActions: PlaylistActions? = null,
    libraryActions: LibraryActions,
    progressActions: ProgressActions? = null,
    // Android TV: attached to the actual focusable leaf built by itemComposable, not the outer
    // wrapper Box above -- that Box isn't focusable itself, so a requester there can't be used to
    // requestFocus() into this item (verified live: it silently does nothing). See CategoryRow.kt.
    firstItemFocusRequester: FocusRequester? = null,
    itemComposable: @Composable (
        modifier: Modifier,
        onClick: (T) -> Unit,
        onLongClick: (T) -> Unit,
    ) -> Unit,
) {
    val clickContext = LocalClickActionConfig.current.context
    var expandedItemId by remember { mutableStateOf<String?>(null) }
    var showPlaylistDialog by rememberSaveable { mutableStateOf(false) }
    var showRemoveConfirmation by remember { mutableStateOf(false) }

    val actions = resolveLongClickActions(
        item = item,
        clickContext = clickContext,
        librarySupported = item !is Genre,
        canAddToPlaylist = playlistActions != null && item.supportsAddToPlaylist,
        canRemoveFromPlaylist = false,
        progressSupported = progressActions != null && item is Audiobook,
        customizationAllowed = false,
    )

    // Android TV: this wrapper Box isn't itself the focusable node -- the real clickable target
    // is built several layers down by itemComposable (a different composable per media type/view
    // mode) -- so tvFocusRing needs trackDescendants to see focus land on any of them.
    Box(modifier = modifier.tvFocusRing(trackDescendants = true)) {
        // Browsable items stay navigable even when non-playable; dim + drop playback actions.
        val contentModifier = Modifier.align(Alignment.Center)
            .then(if (item.isPlayable) Modifier else Modifier.alpha(DISABLED_ITEM_ALPHA))
            .then(
                if (firstItemFocusRequester != null) {
                    Modifier.focusRequester(firstItemFocusRequester)
                } else {
                    Modifier
                },
            )
        itemComposable(
            contentModifier,
            onNavigateClick,
        ) { expandedItemId = item.itemId }

        DropdownMenu(
            expanded = expandedItemId == item.itemId,
            onDismissRequest = { expandedItemId = null },
        ) {
            ItemActionMenuItems(clickContext, actions) { action ->
                expandedItemId = null
                when (action) {
                    is ItemAction.Play -> onPlayOption(item, action.queueOption, false, false)
                    ItemAction.StartRadio -> onPlayOption(item, QueueOption.REPLACE, true, false)
                    ItemAction.AddToLibrary -> libraryActions.onLibraryClick(item)
                    ItemAction.RemoveFromLibrary -> showRemoveConfirmation = true
                    ItemAction.Favorite,
                    ItemAction.Unfavorite,
                    -> libraryActions.onFavoriteClick(item)
                    ItemAction.AddToPlaylist -> showPlaylistDialog = true
                    ItemAction.MarkPlayed -> progressActions?.onMarkPlayed(item)
                    ItemAction.MarkUnplayed -> progressActions?.onMarkUnplayed(item)
                    ItemAction.RemoveFromPlaylist -> Unit
                    // Browsable items never surface Customize (playable-only menu entry).
                    ItemAction.Customize -> Unit
                    else -> Unit
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

        if (showRemoveConfirmation) {
            RemoveFromLibraryConfirmationDialog(
                item = item,
                onConfirm = { libraryActions.onLibraryClick(item) },
                onDismiss = { showRemoveConfirmation = false },
            )
        }
    }
}
