@file:OptIn(ExperimentalMaterial3Api::class)
// Compose layout values (sizes, alphas, animation durations) are visual design tokens.
@file:Suppress("MagicNumber")

package io.music_assistant.client.ui.compose.item

import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.window.core.layout.WindowSizeClass
import coil3.compose.AsyncImage
import io.music_assistant.client.data.model.client.AppMediaItemFixtures
import io.music_assistant.client.data.model.client.ImageType
import io.music_assistant.client.data.model.client.QueueOption
import io.music_assistant.client.data.model.client.items.Album
import io.music_assistant.client.data.model.client.items.AppMediaItem
import io.music_assistant.client.data.model.client.items.Artist
import io.music_assistant.client.data.model.client.items.Genre
import io.music_assistant.client.imageloader.rememberArtworkRequest
import io.music_assistant.client.ui.INACTIVE_ALPHA
import io.music_assistant.client.ui.compose.common.OverflowMenuButton
import io.music_assistant.client.ui.compose.common.PlayerColors
import io.music_assistant.client.ui.compose.common.icons.TrackIcon
import io.music_assistant.client.ui.compose.common.items.AddToPlaylistDialog
import io.music_assistant.client.ui.compose.common.items.Badges
import io.music_assistant.client.ui.compose.common.items.ItemAction
import io.music_assistant.client.ui.compose.common.items.LibraryActions
import io.music_assistant.client.ui.compose.common.items.LocalClickActionConfig
import io.music_assistant.client.ui.compose.common.items.PlaylistActions
import io.music_assistant.client.ui.compose.common.items.localizedSubtitle
import io.music_assistant.client.ui.compose.common.items.navigationOptions
import io.music_assistant.client.ui.compose.common.items.resolveDetailOverflowActions
import io.music_assistant.client.ui.compose.common.items.toOverflowOption
import io.music_assistant.client.ui.compose.common.painters.rememberPlaceholderPainter
import io.music_assistant.client.ui.contentColorByLuminance
import io.music_assistant.client.ui.fadingEdges
import io.music_assistant.client.ui.inactive
import io.music_assistant.client.utils.WindowClass
import musicassistantclient.composeapp.generated.resources.Res
import musicassistantclient.composeapp.generated.resources.cd_more
import musicassistantclient.composeapp.generated.resources.common_back
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ItemHeader(
    item: AppMediaItem,
    colors: PlayerColors = PlayerColors(
        MaterialTheme.colorScheme.primaryContainer,
        MaterialTheme.colorScheme.primary,
    ),
    providerIconFetcher: (@Composable (Modifier, String) -> Unit)? = null,
    onPlayClick: (QueueOption, Boolean) -> Unit = { _, _ -> },
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
            ItemText(item, textAlign, Modifier.padding(top = 16.dp))
            ItemPlayButton(
                item,
                onPlayClick = onPlayClick,
                tint = colors.controlTint,
                modifier = Modifier.padding(top = 8.dp),
            )
        }

        if (WindowClass.isAtLeastExpanded()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                image()
                Column(modifier = Modifier.padding(start = 32.dp)) {
                    textAndControls(TextAlign.Start)
                }
            }
        } else {
            Column(
                modifier = Modifier.fillMaxWidth().padding(8.dp),
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
    colors: PlayerColors = PlayerColors(
        MaterialTheme.colorScheme.primaryContainer,
        MaterialTheme.colorScheme.primary,
    ),
    onBack: () -> Unit,
    libraryActions: LibraryActions?,
    playlistActions: PlaylistActions?,
    navigateToItem: (AppMediaItem) -> Unit,
) {
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
            title = {},
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
                ItemOverflow(
                    item = item,
                    libraryActions = libraryActions,
                    playlistActions = playlistActions,
                    navigateToItem = navigateToItem,
                )
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
) {
    var showPlaylistDialog by rememberSaveable { mutableStateOf(false) }

    val canonical = resolveDetailOverflowActions(
        item = item,
        librarySupported = libraryActions != null && item !is Genre,
        canAddToPlaylist = playlistActions != null,
    ).map { action ->
        action.toOverflowOption(LocalClickActionConfig.current.context) {
            when (it) {
                ItemAction.AddToLibrary,
                ItemAction.RemoveFromLibrary,
                -> libraryActions?.onLibraryClick(item)
                ItemAction.Favorite,
                ItemAction.Unfavorite,
                -> libraryActions?.onFavoriteClick(item)
                ItemAction.AddToPlaylist -> showPlaylistDialog = true
                else -> Unit
            }
        }
    }
    OverflowMenuButton(
        options = canonical + item.navigationOptions(navigateToItem),
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
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            modifier = Modifier.fillMaxWidth().fadingEdges().basicMarquee(),
            text = item.name,
            textAlign = textAlign,
            style = MaterialTheme.typography.titleLarge,
        )

        (item as? Album)?.version?.let {
            if (it.isNotBlank()) {
                Text(
                    modifier = Modifier.fillMaxWidth().fadingEdges().basicMarquee(),
                    text = it,
                    textAlign = textAlign,
                    style = MaterialTheme.typography.titleSmall,
                )
            }
        }

        item.localizedSubtitle()?.let {
            Text(
                modifier = Modifier.fillMaxWidth().fadingEdges().basicMarquee(),
                text = it,
                textAlign = textAlign,
                style = MaterialTheme.typography.titleMedium,
            )
        }
    }
}

@Composable
private fun Image(
    item: AppMediaItem,
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
