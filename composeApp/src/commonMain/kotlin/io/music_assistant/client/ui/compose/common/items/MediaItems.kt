// Compose layout values (sizes, alphas) are visual design tokens.
@file:Suppress("MagicNumber")

package io.music_assistant.client.ui.compose.common.items

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Explicit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import io.music_assistant.client.data.model.client.ImageType
import io.music_assistant.client.data.model.client.items.Album
import io.music_assistant.client.data.model.client.items.AppMediaItem
import io.music_assistant.client.data.model.client.items.Artist
import io.music_assistant.client.data.model.client.items.Audiobook
import io.music_assistant.client.data.model.client.items.Genre
import io.music_assistant.client.data.model.client.items.MediaCollection
import io.music_assistant.client.data.model.client.items.PlayableItem
import io.music_assistant.client.data.model.client.items.Playlist
import io.music_assistant.client.data.model.client.items.Podcast
import io.music_assistant.client.data.model.client.items.PodcastEpisode
import io.music_assistant.client.data.model.client.items.RecommendationFolder
import io.music_assistant.client.data.model.client.items.Track
import io.music_assistant.client.data.model.client.items.image
import io.music_assistant.client.settings.ViewMode
import io.music_assistant.client.ui.compose.common.icons.ArtistIcon
import io.music_assistant.client.ui.compose.common.painters.rememberPlaceholderPainter
import io.music_assistant.client.ui.compose.common.providers.ProviderIconFetcher
import io.music_assistant.client.ui.compose.grid.GridItem
import io.music_assistant.client.ui.theme.favoriteTint
import io.music_assistant.client.utils.rowImageSize
import musicassistantclient.composeapp.generated.resources.Res
import musicassistantclient.composeapp.generated.resources.cd_album_item
import musicassistantclient.composeapp.generated.resources.cd_explicit
import musicassistantclient.composeapp.generated.resources.cd_favorite
import musicassistantclient.composeapp.generated.resources.cd_fully_played
import musicassistantclient.composeapp.generated.resources.cd_in_progress
import org.jetbrains.compose.resources.stringResource

/** Opacity applied to media items that can't be played (server `is_playable == false`). */
internal const val DISABLED_ITEM_ALPHA = 0.3f

/**
 * Artist media item with circular image.
 *
 * @param item The artist item to display
 * @param onClick Click handler
 */
@Composable
fun ArtistGridItem(
    modifier: Modifier = Modifier,
    item: Artist,
    onClick: (Artist) -> Unit,
    onLongClick: (Artist) -> Unit,
    providerIconFetcher: ProviderIconFetcher?,
) {
    GridItem(
        modifier = modifier,
        description = contentDescription(item),
        onClick = { onClick(item) },
        onLongClick = { onLongClick(item) },
    ) {
        Box {
            ArtistImage(item)
            Badges(item, providerIconFetcher)
        }
        Spacer(Modifier.height(4.dp))
        MediaItemLabels(
            title = item.displayName,
            subtitle = item.localizedSubtitle().orEmpty(),
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun ArtistImage(
    item: Artist,
) {
    val primaryContainer = MaterialTheme.colorScheme.primaryContainer
    val onPrimaryContainer = MaterialTheme.colorScheme.onPrimaryContainer
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(CircleShape)
            .background(primaryContainer),
    ) {
        val placeholder = rememberPlaceholderPainter(
            backgroundColor = primaryContainer,
            iconColor = onPrimaryContainer,
            icon = ArtistIcon,
        )
        AsyncImage(
            placeholder = placeholder,
            fallback = placeholder,
            model = item.image(ImageType.THUMB)?.url,
            contentDescription = item.displayName,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

/** The artwork of a media item, see [MediaArtwork]. */
@Composable
internal fun MediaArtwork(item: AppMediaItem) = MediaArtwork(
    imageUrl = item.image(ImageType.THUMB)?.url,
    contentDescription = item.displayName,
    icon = item.defaultIcon,
)

/**
 * The square artwork of every media item but an artist, which [ArtistImage] draws as a circle:
 * the image, or a placeholder with the type [icon]. A grid tile puts [GridBadges] over it.
 */
@Composable
internal fun MediaArtwork(
    imageUrl: String?,
    contentDescription: String,
    icon: ImageVector,
) {
    val primaryContainer = MaterialTheme.colorScheme.primaryContainer
    val onPrimaryContainer = MaterialTheme.colorScheme.onPrimaryContainer
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(RoundedCornerShape(8.dp))
            .background(primaryContainer),
    ) {
        val placeholder = rememberPlaceholderPainter(
            backgroundColor = primaryContainer,
            iconColor = onPrimaryContainer,
            icon = icon,
        )
        AsyncImage(
            placeholder = placeholder,
            fallback = placeholder,
            model = imageUrl,
            contentDescription = contentDescription,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

/**
 * Album media item.
 *
 * @param item The album item to display
 * @param onClick Click handler
 */
@Composable
fun AlbumGridItem(
    modifier: Modifier = Modifier,
    item: Album,
    onClick: (Album) -> Unit,
    onLongClick: (Album) -> Unit,
    providerIconFetcher: ProviderIconFetcher?,
    subtitle: AlbumSubtitle = AlbumSubtitle.ARTISTS,
) {
    GridItem(
        modifier = modifier,
        description = contentDescription(item),
        onClick = { onClick(item) },
        onLongClick = { onLongClick(item) },
    ) {
        Box {
            MediaArtwork(item)
            GridBadges(item, providerIconFetcher)
        }
        Spacer(Modifier.height(4.dp))
        MediaItemLabels(
            title = item.displayName,
            subtitle = item.subtitleText(subtitle).orEmpty(),
        )
    }
}

@Composable
private fun Album.subtitleText(subtitle: AlbumSubtitle): String? = when (subtitle) {
    AlbumSubtitle.ARTISTS -> localizedSubtitle()
    AlbumSubtitle.YEAR -> year?.toString()
}

/**
 * Playlist media item.
 *
 * @param item The playlist item to display
 * @param onClick Click handler

 */
@Composable
fun PlaylistGridItem(
    modifier: Modifier = Modifier,
    item: Playlist,
    onClick: (Playlist) -> Unit,
    onLongClick: (Playlist) -> Unit,
    providerIconFetcher: ProviderIconFetcher? = null,
) {
    GridItem(
        modifier = modifier,
        description = contentDescription(item),
        onClick = { onClick(item) },
        onLongClick = { onLongClick(item) },
    ) {
        Box {
            MediaArtwork(item)
            GridBadges(item, providerIconFetcher)
        }
        Spacer(Modifier.height(4.dp))
        MediaItemLabels(
            title = item.displayName,
            subtitle = item.localizedSubtitle().orEmpty(),
        )
    }
}

@Composable
fun PodcastGridItem(
    modifier: Modifier = Modifier,
    item: Podcast,
    onClick: (Podcast) -> Unit,
    onLongClick: (Podcast) -> Unit,
    providerIconFetcher: ProviderIconFetcher? = null,
) {
    GridItem(
        modifier = modifier,
        description = contentDescription(item),
        onClick = { onClick(item) },
        onLongClick = { onLongClick(item) },
    ) {
        Box {
            MediaArtwork(item)
            GridBadges(item, providerIconFetcher)
        }
        Spacer(Modifier.height(4.dp))
        MediaItemLabels(
            title = item.displayName,
            subtitle = item.localizedSubtitle().orEmpty(),
        )
    }
}

/**
 * Track media item.
 *
 * @param item The track item to display
 * @param onClick Click handler

 */
@Composable
internal fun TrackGridItem(
    modifier: Modifier = Modifier,
    item: Track,
    onClick: (Track) -> Unit,
    onLongClick: (Track) -> Unit,
    providerIconFetcher: ProviderIconFetcher?,
) {
    GridItem(
        modifier = modifier,
        description = contentDescription(item),
        onClick = { onClick(item) },
        onLongClick = { onLongClick(item) },
    ) {
        Box {
            MediaArtwork(item)
            GridBadges(item, providerIconFetcher)
        }
        GridPlayableItemLabels(item)
    }
}

@Composable
internal fun PodcastEpisodeGridItem(
    modifier: Modifier = Modifier,
    item: PodcastEpisode,
    onClick: (PodcastEpisode) -> Unit,
    onLongClick: (PodcastEpisode) -> Unit,
    providerIconFetcher: ProviderIconFetcher?,
) {
    GridItem(
        modifier = modifier,
        description = contentDescription(item),
        onClick = { onClick(item) },
        onLongClick = { onLongClick(item) },
    ) {
        Box {
            MediaArtwork(item)
            GridBadges(item, providerIconFetcher)
            ProgressBadge(
                fullyPlayed = item.fullyPlayed,
                resumePositionMs = item.resumePositionMs,
            )
        }
        GridPlayableItemLabels(item)
    }
}

/**
 * Live stream media item (radio station or input source).
 *
 * @param item The live item to display
 * @param onClick Click handler

 */
@Composable
internal fun <T> LiveStreamGridItem(
    modifier: Modifier = Modifier,
    item: T,
    onClick: (T) -> Unit,
    onLongClick: (T) -> Unit,
    providerIconFetcher: ProviderIconFetcher?,
) where T : AppMediaItem, T : PlayableItem {
    GridItem(
        modifier = modifier,
        description = contentDescription(item),
        onClick = { onClick(item) },
        onLongClick = { onLongClick(item) },
    ) {
        Box {
            MediaArtwork(item)
            GridBadges(item, providerIconFetcher)
        }
        GridPlayableItemLabels(item)
    }
}

/**
 * Audiobook media item.
 *
 * @param item The audiobook item to display
 * @param onClick Click handler

 */
@Composable
internal fun AudiobookGridItem(
    modifier: Modifier = Modifier,
    item: Audiobook,
    onClick: (Audiobook) -> Unit,
    onLongClick: (Audiobook) -> Unit,
    providerIconFetcher: ProviderIconFetcher?,
) {
    GridItem(
        modifier = modifier,
        description = contentDescription(item),
        onClick = { onClick(item) },
        onLongClick = { onLongClick(item) },
    ) {
        Box {
            MediaArtwork(item)
            GridBadges(item, providerIconFetcher)
            ProgressBadge(
                fullyPlayed = item.fullyPlayed,
                resumePositionMs = item.resumePositionMs,
            )
        }
        Spacer(Modifier.height(4.dp))
        MediaItemLabels(
            title = item.displayName,
            subtitle = item.localizedSubtitle().orEmpty(),
        )
    }
}

@Composable
private fun GridPlayableItemLabels(item: PlayableItem) {
    val subtitleText = (item as? AppMediaItem)?.localizedSubtitle()
    Spacer(Modifier.height(4.dp))
    MediaItemLabels(
        title = "${item.displayName}${
            item.version
                ?.trim()?.takeIf { it.isNotBlank() }?.let { " ($it)" }.orEmpty()
        }",
        subtitle = subtitleText,
    )
}

/** The size of a badge on a grid tile. */
private val GRID_BADGE_SIZE = 16.dp

/** The distance of a grid badge from the artwork edge. */
private val GRID_BADGE_PADDING = 2.dp

/** The size of a badge in the badge line of a row. */
internal val ROW_BADGE_SIZE = 14.dp

/**
 * The icon of a media type on a primary chip. The chip of an item that plays on tap is filled,
 * and the chip of an item that opens its page is translucent.
 */
@Composable
internal fun TypeBadge(
    icon: ImageVector,
    size: Dp,
    playsOnTap: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val (container, content) = if (playsOnTap) {
        scheme.primary to scheme.onPrimary
    } else {
        scheme.primaryContainer.copy(alpha = 0.85f) to scheme.primary
    }
    Icon(
        modifier = modifier
            .size(size)
            .background(container, CircleShape)
            .padding(2.dp),
        imageVector = icon,
        contentDescription = null,
        tint = content,
    )
}

/** The favorite heart, or else the provider icon. */
@Composable
private fun FavoriteOrProviderBadge(
    item: AppMediaItem,
    providerIconFetcher: ProviderIconFetcher?,
    modifier: Modifier,
) {
    if (item.favorite == true) {
        Icon(
            modifier = modifier,
            imageVector = Icons.Filled.Favorite,
            contentDescription = stringResource(Res.string.cd_favorite),
            tint = favoriteTint,
        )
    } else {
        providerIconFetcher?.invoke(modifier.background(Color.Gray, CircleShape), item.provider, "dark")
    }
}

@Composable
private fun ExplicitBadge(modifier: Modifier) {
    Icon(
        modifier = modifier.background(Color.White, RoundedCornerShape(2.dp)),
        imageVector = Icons.Filled.Explicit,
        contentDescription = stringResource(Res.string.cd_explicit),
        tint = Color.Black,
    )
}

/**
 * The corner badges of an artwork: the favorite heart or the provider icon at the bottom end, and
 * the explicit mark at the top end. A grid tile adds [GridTypeBadge] and [ProgressBadge], so the
 * four corners never share a badge.
 */
@Composable
fun BoxScope.Badges(
    item: AppMediaItem,
    providerIconFetcher: ProviderIconFetcher?,
    badgeSize: Dp = GRID_BADGE_SIZE,
    badgePadding: Dp = GRID_BADGE_PADDING,
) {
    val modifier = Modifier.padding(badgePadding).size(badgeSize)
    FavoriteOrProviderBadge(item, providerIconFetcher, modifier.align(Alignment.BottomEnd))
    if (item.isExplicit) {
        ExplicitBadge(modifier.align(Alignment.TopEnd))
    }
}

/** The type badge of a grid tile, at the top start. */
@Composable
private fun BoxScope.GridTypeBadge(item: AppMediaItem) {
    TypeBadge(
        icon = item.defaultIcon,
        size = GRID_BADGE_SIZE,
        playsOnTap = item.playsOnTap,
        modifier = Modifier.align(Alignment.TopStart).padding(GRID_BADGE_PADDING),
    )
}

/** The type badge and the [Badges] of a grid tile. */
@Composable
private fun BoxScope.GridBadges(item: AppMediaItem, providerIconFetcher: ProviderIconFetcher?) {
    GridTypeBadge(item)
    Badges(item, providerIconFetcher)
}

/**
 * The badge line of a row, under the labels: the type, the provider or the favorite heart, the
 * explicit mark, and the played state. A row image carries no badge, because it is too small.
 *
 * @param typeIcon the icon of the type badge, or null for none: the round artist image already
 *   tells the type.
 */
@Composable
private fun RowBadges(
    item: AppMediaItem,
    providerIconFetcher: ProviderIconFetcher?,
    fullyPlayed: Boolean? = null,
    resumePositionMs: Long? = null,
    typeIcon: ImageVector? = item.defaultIcon,
) {
    val badge = Modifier.size(ROW_BADGE_SIZE)
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        typeIcon?.let { TypeBadge(it, ROW_BADGE_SIZE, playsOnTap = item.playsOnTap) }
        FavoriteOrProviderBadge(item, providerIconFetcher, badge)
        if (item.isExplicit) {
            ExplicitBadge(badge)
        }
        ProgressBadge(fullyPlayed, resumePositionMs, badge)
    }
}

/** The played state of an audiobook or a podcast episode, at the bottom start of an artwork. */
@Composable
fun BoxScope.ProgressBadge(
    fullyPlayed: Boolean?,
    resumePositionMs: Long?,
    badgeSize: Dp = 18.dp,
    badgePadding: Dp = GRID_BADGE_PADDING,
) {
    ProgressBadge(
        fullyPlayed = fullyPlayed,
        resumePositionMs = resumePositionMs,
        modifier = Modifier.align(Alignment.BottomStart).padding(badgePadding).size(badgeSize),
    )
}

/** A check for a fully played item, a clock for an item with a resume point, nothing otherwise. */
@Composable
private fun ProgressBadge(fullyPlayed: Boolean?, resumePositionMs: Long?, modifier: Modifier) {
    when {
        fullyPlayed == true -> {
            Icon(
                modifier = modifier
                    .background(
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.85f),
                        CircleShape,
                    )
                    .padding(2.dp),
                imageVector = Icons.Default.Check,
                contentDescription = stringResource(Res.string.cd_fully_played),
                tint = MaterialTheme.colorScheme.primary,
            )
        }

        resumePositionMs != null && resumePositionMs > 0 -> {
            Icon(
                modifier = modifier
                    .background(
                        MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.85f),
                        CircleShape,
                    )
                    .padding(2.dp),
                imageVector = Icons.Default.Schedule,
                contentDescription = stringResource(Res.string.cd_in_progress),
                tint = MaterialTheme.colorScheme.tertiary,
            )
        }
    }
}

// ── Row layout ────────────────────────────────────────────────────────────────

@Composable
internal fun TrackRowItem(
    modifier: Modifier = Modifier,
    item: Track,
    showTrackNumber: Boolean,
    onClick: (Track) -> Unit,
    onLongClick: (Track) -> Unit,
    providerIconFetcher: ProviderIconFetcher?,
    titleMaxLines: Int = 2,
) {
    RowItem(
        modifier = modifier,
        titleMaxLines = titleMaxLines,
        name = item.displayName,
        subtitle = item.localizedSubtitle(),
        description = contentDescription(item),
        prefixContent = if (showTrackNumber) {
            item.trackNumber?.toString()?.let { trackNumber ->
                {
                    Text(
                        modifier = Modifier.align(Alignment.Center),
                        text = trackNumber,
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        } else {
            { MediaArtwork(item) }
        },
        badges = { RowBadges(item, providerIconFetcher) },
        onClick = { onClick(item) },
        onLongClick = { onLongClick(item) },
    )
}

@Composable
internal fun AlbumRowItem(
    modifier: Modifier = Modifier,
    item: Album,
    onClick: (Album) -> Unit,
    onLongClick: (Album) -> Unit,
    providerIconFetcher: ProviderIconFetcher?,
    subtitle: AlbumSubtitle = AlbumSubtitle.ARTISTS,
) {
    RowItem(
        modifier = modifier,
        name = item.displayName,
        subtitle = item.subtitleText(subtitle),
        description = contentDescription(item),
        prefixContent = { MediaArtwork(item) },
        badges = { RowBadges(item, providerIconFetcher) },
        onClick = { onClick(item) },
        onLongClick = { onLongClick(item) },
    )
}

@Composable
internal fun ArtistRowItem(
    modifier: Modifier = Modifier,
    item: Artist,
    onClick: (Artist) -> Unit,
    onLongClick: (Artist) -> Unit,
    providerIconFetcher: ProviderIconFetcher?,
) {
    RowItem(
        modifier = modifier,
        name = item.displayName,
        subtitle = item.localizedSubtitle(),
        description = contentDescription(item),
        prefixContent = { ArtistImage(item) },
        badges = { RowBadges(item, providerIconFetcher, typeIcon = null) },
        onClick = { onClick(item) },
        onLongClick = { onLongClick(item) },
    )
}

@Composable
internal fun PlaylistRowItem(
    modifier: Modifier = Modifier,
    item: Playlist,
    onClick: (Playlist) -> Unit,
    onLongClick: (Playlist) -> Unit,
    providerIconFetcher: ProviderIconFetcher?,
) {
    RowItem(
        modifier = modifier,
        name = item.displayName,
        subtitle = item.localizedSubtitle(),
        description = contentDescription(item),
        prefixContent = { MediaArtwork(item) },
        badges = { RowBadges(item, providerIconFetcher) },
        onClick = { onClick(item) },
        onLongClick = { onLongClick(item) },
    )
}

@Composable
internal fun PodcastRowItem(
    modifier: Modifier = Modifier,
    item: Podcast,
    onClick: (Podcast) -> Unit,
    onLongClick: (Podcast) -> Unit,
    providerIconFetcher: ProviderIconFetcher?,
) {
    RowItem(
        modifier = modifier,
        name = item.displayName,
        subtitle = item.localizedSubtitle(),
        description = contentDescription(item),
        prefixContent = { MediaArtwork(item) },
        badges = { RowBadges(item, providerIconFetcher) },
        onClick = { onClick(item) },
        onLongClick = { onLongClick(item) },
    )
}

@Composable
internal fun PodcastEpisodeRowItem(
    modifier: Modifier = Modifier,
    item: PodcastEpisode,
    onClick: (PodcastEpisode) -> Unit,
    onLongClick: (PodcastEpisode) -> Unit,
    providerIconFetcher: ProviderIconFetcher?,
    titleMaxLines: Int = 2,
) {
    RowItem(
        modifier = modifier,
        titleMaxLines = titleMaxLines,
        name = item.displayName,
        subtitle = item.localizedSubtitle(),
        description = contentDescription(item),
        prefixContent = { MediaArtwork(item) },
        badges = { RowBadges(item, providerIconFetcher, item.fullyPlayed, item.resumePositionMs) },
        onClick = { onClick(item) },
        onLongClick = { onLongClick(item) },
    )
}

@Composable
internal fun <T> LiveStreamRowItem(
    modifier: Modifier = Modifier,
    item: T,
    onClick: (T) -> Unit,
    onLongClick: (T) -> Unit,
    providerIconFetcher: ProviderIconFetcher?,
    titleMaxLines: Int = 2,
) where T : AppMediaItem, T : PlayableItem {
    RowItem(
        modifier = modifier,
        titleMaxLines = titleMaxLines,
        name = item.displayName,
        subtitle = item.localizedSubtitle(),
        description = contentDescription(item),
        prefixContent = { MediaArtwork(item) },
        badges = { RowBadges(item, providerIconFetcher) },
        onClick = { onClick(item) },
        onLongClick = { onLongClick(item) },
    )
}

@Composable
fun GenreGridItem(
    modifier: Modifier = Modifier,
    item: Genre,
    onClick: (Genre) -> Unit,
    onLongClick: (Genre) -> Unit,
    providerIconFetcher: ProviderIconFetcher? = null,
) {
    GridItem(
        modifier = modifier,
        description = contentDescription(item),
        onClick = { onClick(item) },
        onLongClick = { onLongClick(item) },
    ) {
        Box {
            MediaArtwork(item)
            GridBadges(item, providerIconFetcher)
        }
        Spacer(Modifier.height(4.dp))
        MediaItemLabels(
            title = item.displayName,
            subtitle = item.localizedSubtitle().orEmpty(),
        )
    }
}

@Composable
internal fun GenreRowItem(
    modifier: Modifier = Modifier,
    item: Genre,
    onClick: (Genre) -> Unit,
    onLongClick: (Genre) -> Unit,
    providerIconFetcher: ProviderIconFetcher?,
) {
    RowItem(
        modifier = modifier,
        name = item.displayName,
        subtitle = item.localizedSubtitle(),
        description = contentDescription(item),
        prefixContent = { MediaArtwork(item) },
        badges = { RowBadges(item, providerIconFetcher) },
        onClick = { onClick(item) },
        onLongClick = { onLongClick(item) },
    )
}

/**
 * Browse-only cell for a [RecommendationFolder]: icon + name. Tapping it navigates one level
 * deeper. Folders never appear in library lists, so this is exercised only by the Browse screen.
 */
@Composable
internal fun FolderCell(
    modifier: Modifier = Modifier,
    item: RecommendationFolder,
    viewMode: ViewMode = ViewMode.GRID,
    onClick: (RecommendationFolder) -> Unit,
    onLongClick: (RecommendationFolder) -> Unit,
) {
    when (viewMode) {
        ViewMode.LIST -> RowItem(
            modifier = modifier.fillMaxWidth(),
            name = item.displayName,
            subtitle = null,
            description = contentDescription(item),
            prefixContent = { MediaArtwork(item) },
            badges = { RowBadges(item, providerIconFetcher = null) },
            onClick = { onClick(item) },
            onLongClick = { onLongClick(item) },
        )

        ViewMode.GRID -> GridItem(
            modifier = modifier,
            description = contentDescription(item),
            onClick = { onClick(item) },
            onLongClick = { onLongClick(item) },
        ) {
            Box {
                MediaArtwork(item)
                GridTypeBadge(item)
            }
            Spacer(Modifier.height(4.dp))
            MediaItemLabels(
                title = item.displayName,
                subtitle = null,
            )
        }
    }
}

@Composable
internal fun AudiobookRowItem(
    modifier: Modifier = Modifier,
    item: Audiobook,
    onClick: (Audiobook) -> Unit,
    onLongClick: (Audiobook) -> Unit,
    providerIconFetcher: ProviderIconFetcher?,
) {
    RowItem(
        modifier = modifier,
        name = item.displayName,
        subtitle = item.localizedSubtitle(),
        description = contentDescription(item),
        prefixContent = { MediaArtwork(item) },
        badges = { RowBadges(item, providerIconFetcher, item.fullyPlayed, item.resumePositionMs) },
        onClick = { onClick(item) },
        onLongClick = { onLongClick(item) },
    )
}

/** A series: the cover of its first book and the type badge; it has no provider, favorite or progress. */
@Composable
internal fun MediaCollectionGridItem(
    modifier: Modifier = Modifier,
    item: MediaCollection,
    onClick: (MediaCollection) -> Unit,
    onLongClick: (MediaCollection) -> Unit,
) {
    GridItem(
        modifier = modifier,
        description = contentDescription(item),
        onClick = { onClick(item) },
        onLongClick = { onLongClick(item) },
    ) {
        Box {
            MediaArtwork(item)
            GridTypeBadge(item)
        }
        Spacer(Modifier.height(4.dp))
        MediaItemLabels(
            title = item.displayName,
            subtitle = item.localizedSubtitle().orEmpty(),
        )
    }
}

@Composable
internal fun MediaCollectionRowItem(
    modifier: Modifier = Modifier,
    item: MediaCollection,
    onClick: (MediaCollection) -> Unit,
    onLongClick: (MediaCollection) -> Unit,
) {
    RowItem(
        modifier = modifier,
        name = item.displayName,
        subtitle = item.localizedSubtitle(),
        description = contentDescription(item),
        prefixContent = { MediaArtwork(item) },
        badges = { RowBadges(item, providerIconFetcher = null) },
        onClick = { onClick(item) },
        onLongClick = { onLongClick(item) },
    )
}

private val MEDIA_TITLE_WEIGHT = FontWeight.SemiBold
private const val SUBTITLE_ALPHA = 0.6f

/**
 * Canonical title + optional subtitle for every list/grid media item.
 * Title is emphasised via weight; the subtitle is dimmed with alpha rather than a
 * distinct color, keeping the hierarchy sleek and consistent app-wide.
 *
 * @param reserveSubtitleLine true keeps an empty subtitle line, so grid tiles of one row align.
 *   false drops it, so a row centers a lone title vertically.
 * @param textAlign the alignment of both lines; an artist tile centers them under its circle.
 */
@Composable
internal fun MediaItemLabels(
    title: String,
    subtitle: String?,
    titleMaxLines: Int = 1,
    reserveSubtitleLine: Boolean = true,
    textAlign: TextAlign = TextAlign.Unspecified,
) {
    Text(
        modifier = Modifier.fillMaxWidth(),
        text = title,
        style = mediaItemTitleStyle(),
        fontWeight = MEDIA_TITLE_WEIGHT,
        textAlign = textAlign,
        maxLines = titleMaxLines,
        overflow = TextOverflow.Ellipsis,
    )

    if (subtitle.isNullOrBlank() && !reserveSubtitleLine) return

    val subtitleStyle = mediaItemSubtitleStyle()
    val subtitleLineHeight = with(LocalDensity.current) {
        subtitleStyle.lineHeight.toDp()
    }

    Box(modifier = Modifier.height(subtitleLineHeight)) {
        if (!subtitle.isNullOrBlank()) {
            Text(
                modifier = Modifier.fillMaxWidth(),
                text = subtitle,
                style = subtitleStyle,
                fontWeight = MEDIA_TITLE_WEIGHT,
                textAlign = textAlign,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = SUBTITLE_ALPHA),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * The shared library row: leading image slot, title/subtitle, optional trailing slot.
 *
 * Takes strings and slots rather than an [AppMediaItem], so a row that is not a media item —
 * an AI Radio station, say — can reuse the exact same metrics and typography.
 *
 * @param enabled false makes the row inert, ripple included. For a row that carries its action
 *   in [suffixContent] instead, so a tap on the body is not answered with a ripple.
 * @param suffixContent trailing slot, rendered after the weighted label column. When present it
 *   may hold a control of its own, so the row's description is scoped to the labels — see below.
 * @param badges the badge line under the labels, see [RowBadges].
 */
@Composable
internal fun RowItem(
    modifier: Modifier = Modifier,
    name: String,
    subtitle: String?,
    description: String,
    prefixContent: @Composable (BoxScope.() -> Unit)?,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    enabled: Boolean = true,
    titleMaxLines: Int = 2,
    suffixContent: @Composable (RowScope.() -> Unit)? = null,
    badges: @Composable (() -> Unit)? = null,
) {
    // clearAndSetSemantics wipes every descendant's semantics too. On a whole-row tap target
    // that is what we want — one node, one description. But a trailing control would be made
    // invisible and unreachable to accessibility services by it, so a row that has one scopes
    // the description to its labels and lets that control keep its own.
    val describe = Modifier.clearAndSetSemantics { contentDescription = description }
    val describesWholeRow = suffixContent == null
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .combinedClickable(enabled = enabled, onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .then(if (describesWholeRow) describe else Modifier),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        prefixContent?.let {
            Box(modifier = Modifier.size(rowImageSize())) { it() }
            Spacer(Modifier.width(12.dp))
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .then(if (describesWholeRow) Modifier else describe),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            MediaItemLabels(
                title = name,
                subtitle = subtitle,
                titleMaxLines = titleMaxLines,
                reserveSubtitleLine = false,
            )
            badges?.invoke()
        }
        suffixContent?.invoke(this)
    }
}

@Composable
internal fun mediaItemTitleStyle(): TextStyle {
    return MaterialTheme.typography.bodyMedium
}

@Composable
internal fun mediaItemSubtitleStyle(): TextStyle {
    return MaterialTheme.typography.bodySmall
}

@Composable
private fun contentDescription(appMediaItem: AppMediaItem): String {
    return if (appMediaItem is Album) {
        stringResource(Res.string.cd_album_item, appMediaItem.displayName, appMediaItem.provider)
    } else {
        appMediaItem.displayName
    }
}
