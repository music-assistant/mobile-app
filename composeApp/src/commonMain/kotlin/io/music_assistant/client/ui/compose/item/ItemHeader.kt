@file:OptIn(ExperimentalMaterial3Api::class)
// Compose layout values (sizes, alphas, animation durations) are visual design tokens.
@file:Suppress("MagicNumber")

package io.music_assistant.client.ui.compose.item

import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Lyrics
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.window.core.layout.WindowSizeClass
import coil3.compose.AsyncImage
import io.music_assistant.client.data.model.client.AppMediaItemFixtures
import io.music_assistant.client.data.model.client.ImageType
import io.music_assistant.client.data.model.client.ItemDescription
import io.music_assistant.client.data.model.client.ProviderDetails
import io.music_assistant.client.data.model.client.items.Album
import io.music_assistant.client.data.model.client.items.AppMediaItem
import io.music_assistant.client.data.model.client.items.Artist
import io.music_assistant.client.data.model.client.items.Audiobook
import io.music_assistant.client.data.model.client.items.Genre
import io.music_assistant.client.data.model.client.items.MediaCollection
import io.music_assistant.client.data.model.client.items.PlaylistOwnerLine
import io.music_assistant.client.data.model.client.items.PodcastEpisode
import io.music_assistant.client.data.model.client.items.RefreshItemAction
import io.music_assistant.client.data.model.client.items.Track
import io.music_assistant.client.data.play.PlayMode
import io.music_assistant.client.imageloader.rememberArtworkRequest
import io.music_assistant.client.ui.INACTIVE_ALPHA
import io.music_assistant.client.ui.compose.common.LocalDislikeSupported
import io.music_assistant.client.ui.compose.common.LocalGrantedScopes
import io.music_assistant.client.ui.compose.common.OverflowMenuButton
import io.music_assistant.client.ui.compose.common.OverflowMenuOption
import io.music_assistant.client.ui.compose.common.PlayerColors
import io.music_assistant.client.ui.compose.common.RemoveFromLibraryConfirmationDialog
import io.music_assistant.client.ui.compose.common.dynamicColorsMenuOption
import io.music_assistant.client.ui.compose.common.icons.TrackIcon
import io.music_assistant.client.ui.compose.common.items.AddToPlaylistDialog
import io.music_assistant.client.ui.compose.common.items.Badges
import io.music_assistant.client.ui.compose.common.items.ItemAction
import io.music_assistant.client.ui.compose.common.items.LibraryActions
import io.music_assistant.client.ui.compose.common.items.LocalClickActionConfig
import io.music_assistant.client.ui.compose.common.items.PlaylistActions
import io.music_assistant.client.ui.compose.common.items.ProgressBadge
import io.music_assistant.client.ui.compose.common.items.localizedSubtitle
import io.music_assistant.client.ui.compose.common.items.navigationOptions
import io.music_assistant.client.ui.compose.common.items.rememberArtistNavigation
import io.music_assistant.client.ui.compose.common.items.resolveDetailOverflowActions
import io.music_assistant.client.ui.compose.common.items.toOverflowOption
import io.music_assistant.client.ui.compose.common.painters.rememberPlaceholderPainter
import io.music_assistant.client.ui.compose.common.providers.ProviderIconFetcher
import io.music_assistant.client.ui.compose.search.SearchInput
import io.music_assistant.client.ui.contentColorByLuminance
import io.music_assistant.client.ui.inactive
import io.music_assistant.client.ui.utils.WindowClass
import io.music_assistant.client.ui.utils.fadingEdges
import musicassistantclient.composeapp.generated.resources.Res
import musicassistantclient.composeapp.generated.resources.action_find_on_music_services
import musicassistantclient.composeapp.generated.resources.action_go_to_artist
import musicassistantclient.composeapp.generated.resources.action_go_to_podcast
import musicassistantclient.composeapp.generated.resources.action_lyrics
import musicassistantclient.composeapp.generated.resources.action_refresh_item
import musicassistantclient.composeapp.generated.resources.action_similar_artists
import musicassistantclient.composeapp.generated.resources.action_transcript
import musicassistantclient.composeapp.generated.resources.cd_close
import musicassistantclient.composeapp.generated.resources.cd_find_in_list
import musicassistantclient.composeapp.generated.resources.cd_more
import musicassistantclient.composeapp.generated.resources.cd_refreshing_item
import musicassistantclient.composeapp.generated.resources.common_back
import musicassistantclient.composeapp.generated.resources.find_in_list_label
import musicassistantclient.composeapp.generated.resources.item_narrated_by
import musicassistantclient.composeapp.generated.resources.refresh
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ItemHeader(
    item: AppMediaItem,
    colors: PlayerColors = PlayerColors(
        MaterialTheme.colorScheme.primaryContainer,
        MaterialTheme.colorScheme.primary,
    ),
    providerIconFetcher: ProviderIconFetcher? = null,
    onPlayClick: (PlayMode) -> Unit = {},
    navigateToItem: (AppMediaItem) -> Unit = {},
    genres: List<Genre> = emptyList(),
    playableSummary: PlayableSummary? = null,
    ownerLine: PlaylistOwnerLine? = null,
    providerDetails: (String) -> ProviderDetails? = { null },
    description: ItemDescription? = null,
    onOpenDescription: () -> Unit = {},
) {
    // Art color on top, fading down to the surface the Screen actually paints, so the
    // wash dissolves seamlessly where the tabs begin. Mirrors the player gradient (inverted).
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    listOf(colors.dominant.inactive(), MaterialTheme.colorScheme.surface),
                ),
            ),
    ) {
        val image = @Composable {
            Image(
                item = item,
                providerIconFetcher = providerIconFetcher,
            )
        }

        val textAndControls = @Composable { textAlign: TextAlign ->
            ItemText(item, textAlign, navigateToItem, Modifier.padding(top = 16.dp))
            ItemPlayButton(
                item,
                onPlayClick = onPlayClick,
                tint = colors.controlTint,
                modifier = Modifier.padding(top = 8.dp),
            )
            ItemFacts(
                item = item,
                playableSummary = playableSummary,
                ownerLine = ownerLine,
                providerDetails = providerDetails,
                textAlign = textAlign,
                modifier = Modifier.padding(top = 8.dp),
            )
        }

        // The chips and the text span the full width under the art on every layout.
        val details = @Composable { textAlign: TextAlign ->
            GenreChips(
                genres = genres,
                onGenreClick = navigateToItem,
                textAlign = textAlign,
                modifier = Modifier.padding(top = 4.dp),
            )
            description?.let {
                ItemDescriptionBlock(
                    description = it,
                    onOpen = onOpenDescription,
                    modifier = Modifier.padding(top = 8.dp, bottom = 8.dp),
                )
            }
        }

        if (WindowClass.isWide()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                image()
                Column(modifier = Modifier.padding(start = 32.dp)) {
                    textAndControls(TextAlign.Start)
                }
            }
            details(TextAlign.Start)
        } else {
            // Horizontal
            Column(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                image()
                textAndControls(TextAlign.Center)
                details(TextAlign.Center)
            }
        }
    }
}

@Composable
internal fun ItemTopBar(
    item: AppMediaItem,
    colors: PlayerColors = PlayerColors(
        MaterialTheme.colorScheme.primaryContainer,
        MaterialTheme.colorScheme.primary,
    ),
    onBack: () -> Unit,
    libraryActions: LibraryActions?,
    playlistActions: PlaylistActions?,
    navigateToItem: (AppMediaItem) -> Unit,
    onSimilarArtistsClick: () -> Unit,
    onLyricsClick: (() -> Unit)? = null,
    onRefresh: (() -> Unit)? = null,
    refreshAction: RefreshItemAction? = null,
    refreshingItem: Boolean = false,
    onRefreshItem: () -> Unit = {},
    query: String?,
    onQueryChanged: ((String?) -> Unit)?,
) {
    val searchEnabled = onQueryChanged != null

    // Flat fill equal to the header gradient's top color, so the bar reads as one
    // continuous wash with the header below it. Back/overflow icons are NOT control-tinted
    // — just black or white per the composited bar luminance, keeping them legible.
    val barBg = colors.dominant.inactive()
    val onBar = lerp(MaterialTheme.colorScheme.surface, colors.dominant, INACTIVE_ALPHA)
        .contentColorByLuminance()
    // Paint barBg directly on the wrapping Box rather than via TopAppBar's containerColor:
    // TopAppBar runs its own animateColorAsState on the container, which would stack a second
    // tween on top of our color animation and visibly lag the header. Transparent container =
    // single-pass color change, in lockstep with the header gradient.
    Box(modifier = Modifier.background(barBg)) {
        TopAppBar(
            title = {
                if (searchEnabled && query != null) {
                    SearchInput(
                        query = query,
                        onQueryChanged = onQueryChanged,
                        placeholder = stringResource(Res.string.find_in_list_label),
                    )
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = Color.Transparent,
                scrolledContainerColor = Color.Transparent,
                navigationIconContentColor = onBar,
                actionIconContentColor = onBar,
                titleContentColor = onBar,
            ),
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        stringResource(Res.string.common_back),
                    )
                }
            },
            actions = {
                if (refreshingItem) {
                    val description = stringResource(Res.string.cd_refreshing_item)
                    Box(
                        modifier = Modifier.size(48.dp).semantics { contentDescription = description },
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), color = onBar, strokeWidth = 2.dp)
                    }
                }

                if (searchEnabled && query != null) {
                    IconButton(onClick = { onQueryChanged(null) }) {
                        Icon(Icons.Default.Close, stringResource(Res.string.cd_close))
                    }
                } else {
                    onRefresh?.let { refresh ->
                        IconButton(onClick = refresh) {
                            Icon(
                                Icons.Default.Refresh,
                                stringResource(Res.string.refresh),
                            )
                        }
                    }

                    if (searchEnabled) {
                        IconButton(onClick = { onQueryChanged("") }) {
                            Icon(Icons.Default.Search, stringResource(Res.string.cd_find_in_list))
                        }
                    }

                    ItemOverflow(
                        item = item,
                        libraryActions = libraryActions,
                        playlistActions = playlistActions,
                        navigateToItem = navigateToItem,
                        onSimilarArtistsClick = onSimilarArtistsClick,
                        onLyricsClick = onLyricsClick,
                        refreshAction = refreshAction,
                        refreshingItem = refreshingItem,
                        onRefreshItem = onRefreshItem,
                        onRemovedFromLibrary = onBack,
                    )
                }
            },
        )
    }
}

@Composable
private fun ItemOverflow(
    item: AppMediaItem,
    libraryActions: LibraryActions?,
    playlistActions: PlaylistActions?,
    navigateToItem: (AppMediaItem) -> Unit,
    onSimilarArtistsClick: () -> Unit,
    onLyricsClick: (() -> Unit)?,
    refreshAction: RefreshItemAction?,
    refreshingItem: Boolean,
    onRefreshItem: () -> Unit,
    // The page shows the removed item, so it closes with the removal, like the web app.
    onRemovedFromLibrary: () -> Unit,
) {
    var showPlaylistDialog by rememberSaveable { mutableStateOf(false) }
    var showRemoveConfirmation by remember { mutableStateOf(false) }

    // Artist-only, appended last: opens the similar-artists sheet rather than acting on the item,
    // so it stays out of the shared ItemAction/car-action vocabulary.
    val similarArtists = if (item is Artist) {
        listOf(
            OverflowMenuOption(
                title = stringResource(Res.string.action_similar_artists),
                icon = Icons.Default.Groups,
                onClick = onSimilarArtistsClick,
            ),
        )
    } else {
        emptyList()
    }
    val lyrics = listOfNotNull(
        onLyricsClick?.let {
            OverflowMenuOption(
                title = stringResource(
                    if (item is PodcastEpisode) Res.string.action_transcript else Res.string.action_lyrics,
                ),
                icon = Icons.Outlined.Lyrics,
                onClick = it,
            )
        },
    )
    // Detail-only like the similar artists: the server refreshes the item that the page shows.
    val refresh = listOfNotNull(
        refreshAction?.let { action ->
            OverflowMenuOption(
                title = stringResource(
                    when (action) {
                        RefreshItemAction.REFRESH -> Res.string.action_refresh_item
                        RefreshItemAction.FIND_ON_MUSIC_SERVICES -> Res.string.action_find_on_music_services
                    },
                ),
                icon = Icons.Default.Refresh,
                enabled = !refreshingItem,
                onClick = onRefreshItem,
            )
        },
    )

    val canonical = resolveDetailOverflowActions(
        item = item,
        canEditLibrary = LocalGrantedScopes.current.canEditLibrary,
        dislikeSupported = LocalDislikeSupported.current,
        librarySupported = libraryActions != null && item !is Genre && item !is MediaCollection,
        canAddToPlaylist = playlistActions != null && item !is MediaCollection,
    ).map { action ->
        action.toOverflowOption(LocalClickActionConfig.current.context) {
            when (it) {
                ItemAction.AddToLibrary -> libraryActions?.onLibraryClick(item)
                ItemAction.RemoveFromLibrary -> showRemoveConfirmation = true

                is ItemAction.SetFavorite -> libraryActions?.onFavoriteChange(item, it.favorite)

                ItemAction.AddToPlaylist -> showPlaylistDialog = true
                else -> Unit
            }
        }
    }
    OverflowMenuButton(
        options = canonical + item.navigationOptions(navigateToItem, containerItem = item) + similarArtists +
            lyrics + refresh + dynamicColorsMenuOption(),
    ) { onClick ->
        IconButton(onClick = onClick) {
            Icon(
                imageVector = Icons.Default.MoreVert,
                contentDescription = stringResource(Res.string.cd_more),
            )
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
            onConfirm = {
                libraryActions?.onLibraryClick(item)
                onRemovedFromLibrary()
            },
            onDismiss = { showRemoveConfirmation = false },
        )
    }
}

@Composable
private fun ItemText(
    item: AppMediaItem,
    textAlign: TextAlign,
    navigateToItem: (AppMediaItem) -> Unit,
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
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            modifier = Modifier
                .fillMaxWidth()
                .fadingEdges()
                .basicMarquee()
                .padding(horizontal = 16.dp),
            text = item.name,
            textAlign = textAlign,
            style = MaterialTheme.typography.titleLarge,
        )

        (item as? Album)?.version?.let {
            if (it.isNotBlank()) {
                Text(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fadingEdges()
                        .basicMarquee()
                        .padding(horizontal = 16.dp),
                    text = it,
                    textAlign = textAlign,
                    style = MaterialTheme.typography.titleSmall,
                )
            }
        }

        // An album's or a track's subtitle is its artist list; tapping it does the overflow's "Go to artist".
        val onSubtitleClick = rememberArtistNavigation(
            artists = when (item) {
                is Album -> item.artists
                is Track -> item.artists
                else -> emptyList()
            },
            navigateToItem = navigateToItem,
        )
        item.localizedSubtitle()?.let {
            Text(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(
                        onSubtitleClick?.let { onClick ->
                            Modifier.clickable(
                                onClickLabel = stringResource(Res.string.action_go_to_artist),
                                role = Role.Button,
                                onClick = onClick,
                            )
                        } ?: Modifier,
                    )
                    .fadingEdges()
                    .basicMarquee()
                    .padding(horizontal = 16.dp),
                text = it,
                textAlign = textAlign,
                style = MaterialTheme.typography.titleMedium,
            )
        }

        // The podcast of an episode, like the web hero; a tap opens it.
        (item as? PodcastEpisode)?.podcast?.let { podcast ->
            Text(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(
                        onClickLabel = stringResource(Res.string.action_go_to_podcast),
                        role = Role.Button,
                        onClick = { navigateToItem(podcast) },
                    )
                    .fadingEdges()
                    .basicMarquee()
                    .padding(horizontal = 16.dp),
                text = podcast.name,
                textAlign = textAlign,
                style = MaterialTheme.typography.titleSmall,
            )
        }

        (item as? Audiobook)?.narrators?.takeIf { it.isNotEmpty() }?.let { narrators ->
            Text(
                modifier = Modifier
                    .fillMaxWidth()
                    .fadingEdges()
                    .basicMarquee()
                    .padding(horizontal = 16.dp),
                text = stringResource(Res.string.item_narrated_by, narrators.joinToString(", ")),
                textAlign = textAlign,
                style = MaterialTheme.typography.titleSmall,
            )
        }
    }
}

@Composable
private fun Image(
    item: AppMediaItem,
    providerIconFetcher: ProviderIconFetcher?,
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

        val shape = if (item is Artist) {
            CircleShape
        } else {
            RoundedCornerShape(16.dp)
        }
        AsyncImage(
            model = rememberArtworkRequest(item.image(ImageType.THUMB)?.url),
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
            badgeSize = 24.dp,
            badgePadding = 8.dp,
        )
        (item as? PodcastEpisode)?.let {
            ProgressBadge(
                fullyPlayed = it.fullyPlayed,
                resumePositionMs = it.resumePositionMs,
                badgeSize = 24.dp,
                badgePadding = 8.dp,
            )
        }
    }
}

@Preview
@Composable
private fun Preview(item: Album = AppMediaItemFixtures.album()) {
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
