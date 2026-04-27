@file:OptIn(ExperimentalMaterial3Api::class)
// Compose layout values (sizes, alphas, animation durations) are visual design tokens.
@file:Suppress("MagicNumber")

package io.music_assistant.client.ui.compose.item

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarScrollBehavior
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.window.core.layout.WindowSizeClass
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
import io.music_assistant.client.ui.compose.common.icons.TrackIcon
import io.music_assistant.client.ui.compose.common.items.Badges
import io.music_assistant.client.ui.compose.common.painters.rememberPlaceholderPainter
import io.music_assistant.client.ui.compose.common.viewmodel.ActionsViewModel
import io.music_assistant.client.utils.WindowClass
import kotlinx.coroutines.launch
import musicassistantclient.composeapp.generated.resources.*
import musicassistantclient.composeapp.generated.resources.Res
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ItemHeader(
    item: AppMediaItem,
    serverUrl: String? = null,
    providerIconFetcher: (@Composable (Modifier, String) -> Unit)? = null,
    onPlayClick: (QueueOption, Boolean) -> Unit = { _, _ -> },
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        val image = @Composable {
            Image(
                item = item,
                serverUrl = serverUrl,
                providerIconFetcher = providerIconFetcher,
            )
        }

        val textAndControls = @Composable { textAlign: TextAlign ->
            ItemText(item, textAlign, Modifier.padding(top = 16.dp))
            ItemPlayButton(
                item,
                onPlayClick = onPlayClick,
                modifier = Modifier.padding(top = 16.dp),
            )
        }

        if (WindowClass.isAtLeastExpanded()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                image()
                Column(modifier = Modifier.padding(start = 32.dp)) {
                    textAndControls(TextAlign.Start)
                }
            }
        } else {
            Column(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                image()
                textAndControls(TextAlign.Center)
            }
        }
    }
}

@Composable
internal fun ItemTopBar(
    item: AppMediaItem,
    isRowMode: Boolean,
    onBack: () -> Unit,
    onToggleViewMode: () -> Unit,
    libraryActions: ActionsViewModel.LibraryActions?,
    playlistActions: ActionsViewModel.PlaylistActions?,
    scrollBehavior: TopAppBarScrollBehavior? = null,
    goToArtist: (() -> Unit)?,
) {
    TopAppBar(
        title = {},
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(Res.string.common_back))
            }
        },
        actions = {
            ItemOverflow(
                item = item,
                isRowMode = isRowMode,
                onToggleViewMode = onToggleViewMode,
                libraryActions = libraryActions,
                playlistActions = playlistActions,
                goToArtist = goToArtist,
            )
        },
        scrollBehavior = scrollBehavior,
    )
}

@Composable
private fun ItemOverflow(
    item: AppMediaItem,
    isRowMode: Boolean,
    onToggleViewMode: () -> Unit,
    libraryActions: ActionsViewModel.LibraryActions?,
    playlistActions: ActionsViewModel.PlaylistActions?,
    goToArtist: (() -> Unit)?,
) {
    val coroutineScope = rememberCoroutineScope()
    var showPlaylistDialog by rememberSaveable { mutableStateOf(false) }
    var playlists by remember { mutableStateOf<List<AppMediaItem.Playlist>>(emptyList()) }
    var isLoadingPlaylists by remember { mutableStateOf(false) }

    OverflowMenu(
        options = buildList {
            libraryActions?.let { actions ->
                if (item !is AppMediaItem.Genre) {
                    add(
                        OverflowMenuOption(
                            title =
                                if (item.isInLibrary) {
                                    stringResource(Res.string.action_remove_from_library)
                                } else {
                                    stringResource(Res.string.action_add_to_library)
                                },
                            icon =
                                if (item.isInLibrary) {
                                    TablerIcons.FolderMinus
                                } else {
                                    TablerIcons.FolderPlus
                                },
                        ) { actions.onLibraryClick(item) },
                    )
                }
                if (item.isInLibrary) {
                    add(
                        OverflowMenuOption(
                            title =
                                if (item.favorite == true) {
                                    stringResource(Res.string.action_unfavorite)
                                } else {
                                    stringResource(Res.string.action_favorite)
                                },
                            icon =
                                if (item.favorite == true) {
                                    TablerIcons.HeartBroken
                                } else {
                                    TablerIcons.Heart
                                },
                        ) { actions.onFavoriteClick(item) },
                    )
                }
            }

            playlistActions?.let {
                add(
                    OverflowMenuOption(
                        title = stringResource(Res.string.playlist_add_to_title),
                        icon = Icons.AutoMirrored.Filled.PlaylistAdd,
                    ) {
                        showPlaylistDialog = true
                        // Load playlists when dialog opens
                        coroutineScope.launch {
                            isLoadingPlaylists = true
                            playlists = it.onLoadPlaylists()
                            isLoadingPlaylists = false
                        }
                    },
                )
            }

            add(
                OverflowMenuOption(
                    title = stringResource(Res.string.action_toggle_view_mode),
                    icon = if (isRowMode) Icons.Default.GridView else Icons.AutoMirrored.Filled.ViewList,
                    onClick = onToggleViewMode,
                ),
            )

            if (goToArtist != null) {
                add(
                    OverflowMenuOption(
                        title = stringResource(Res.string.action_go_to_artist),
                        icon = Icons.Default.Person,
                        onClick = goToArtist,
                    ),
                )
            }
        },
    ) { onClick ->
        IconButton(onClick = onClick) {
            Icon(imageVector = Icons.Default.MoreVert, contentDescription = stringResource(Res.string.cd_more))
        }
    }

    if (showPlaylistDialog) {
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
                    Column {
                        playlists.forEach { playlist ->
                            TextButton(
                                onClick = {
                                    playlistActions?.onAddToPlaylist(item, playlist)
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

@Composable
private fun ItemText(
    item: AppMediaItem,
    textAlign: TextAlign,
    modifier: Modifier,
) {
    val horizontalAlignment = if (textAlign == TextAlign.Center) {
        Alignment.CenterHorizontally
    } else {
        Alignment.Start
    }

    Column(
        modifier = modifier,
        horizontalAlignment = horizontalAlignment,
    ) {
        Text(
            item.name,
            textAlign = textAlign,
            style = MaterialTheme.typography.titleLarge,
        )

        (item as? AppMediaItem.Album)?.version?.let {
            if (it.isNotBlank()) {
                Text(
                    it,
                    textAlign = textAlign,
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }

        item.subtitle?.let {
            Text(
                it,
                textAlign = textAlign,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 12.dp),
            )
        }
    }
}

@Composable
private fun Image(
    item: AppMediaItem,
    serverUrl: String?,
    providerIconFetcher: @Composable ((Modifier, String) -> Unit)?,
) {
    Box(
        modifier = Modifier
            .widthIn(max = 300.dp)
            .fillMaxWidth(0.7f)
            .aspectRatio(1f),
    ) {
        val placeholder = rememberPlaceholderPainter(
            backgroundColor = MaterialTheme.colorScheme.background,
            iconColor = MaterialTheme.colorScheme.secondary,
            icon = TrackIcon,
        )

        val shape = if (item is AppMediaItem.Artist) {
            CircleShape
        } else {
            RoundedCornerShape(16.dp)
        }

        val model = item.imageInfo?.url(serverUrl)
        AsyncImage(
            model = model,
            placeholder = placeholder,
            fallback = placeholder,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .clip(shape),
        )

        Badges(
            item,
            providerIconFetcher,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 8.dp, bottom = 8.dp)
                .size(24.dp),
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
    Preview(
        AppMediaItemFixtures.album(
            name = "A very long title that is very long oh no it's so long",
        ),
    )
}

@Preview
@Composable
private fun PreviewAlbumVersion() {
    Preview(AppMediaItemFixtures.album(version = "A Version"))
}

@Preview(
    widthDp = WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND,
    heightDp = WindowSizeClass.HEIGHT_DP_MEDIUM_LOWER_BOUND,
)
@Composable
private fun PreviewAlbumMediumWindow() {
    Preview(AppMediaItemFixtures.album())
}

@Preview(
    widthDp = WindowSizeClass.WIDTH_DP_EXPANDED_LOWER_BOUND,
    heightDp = WindowSizeClass.HEIGHT_DP_EXPANDED_LOWER_BOUND,
)
@Composable
private fun PreviewAlbumExpandedWindow() {
    Preview(AppMediaItemFixtures.album())
}
