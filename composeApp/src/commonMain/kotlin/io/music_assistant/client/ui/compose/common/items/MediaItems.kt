// Compose layout values (sizes, alphas) are visual design tokens.
@file:Suppress("MagicNumber")

package io.music_assistant.client.ui.compose.common.items

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material.icons.filled.Podcasts
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
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
import io.music_assistant.client.data.model.client.items.PlayableItem
import io.music_assistant.client.data.model.client.items.Playlist
import io.music_assistant.client.data.model.client.items.Podcast
import io.music_assistant.client.data.model.client.items.PodcastEpisode
import io.music_assistant.client.data.model.client.items.RadioStation
import io.music_assistant.client.data.model.client.items.Track
import io.music_assistant.client.data.model.client.items.image
import io.music_assistant.client.ui.compose.common.icons.ArtistIcon
import io.music_assistant.client.ui.compose.common.icons.BookAudioIcon
import io.music_assistant.client.ui.compose.common.icons.GenreIcon
import io.music_assistant.client.ui.compose.common.icons.PlaylistIcon
import io.music_assistant.client.ui.compose.common.icons.RadioIcon
import io.music_assistant.client.ui.compose.common.icons.TrackIcon
import io.music_assistant.client.ui.compose.common.painters.rememberPlaceholderPainter
import io.music_assistant.client.ui.compose.common.painters.rememberVinylRecordPainter
import io.music_assistant.client.ui.compose.common.painters.rememberWaveformPainter
import io.music_assistant.client.ui.theme.favoriteTint
import io.music_assistant.client.utils.gridItemMinSize
import io.music_assistant.client.utils.rowImageSize
import musicassistantclient.composeapp.generated.resources.Res
import musicassistantclient.composeapp.generated.resources.cd_favorite
import musicassistantclient.composeapp.generated.resources.cd_fully_played
import musicassistantclient.composeapp.generated.resources.cd_in_progress
import musicassistantclient.composeapp.generated.resources.cd_vinyl_record
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
    providerIconFetcher: (@Composable (Modifier, String) -> Unit)?,
) {
    GridItem(
        modifier = modifier,
        onClick = { onClick(item) },
        onLongClick = { onLongClick(item) },
    ) {
        Box {
            ArtistImage(item)
            Badges(
                item = item,
                providerIconFetcher = providerIconFetcher,
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            modifier = Modifier.fillMaxWidth(),
            text = item.displayName,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            modifier = Modifier.fillMaxWidth(),
            text = item.localizedSubtitle().orEmpty(),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
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

/**
 * Album media item with vinyl record design.
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
    providerIconFetcher: (@Composable (Modifier, String) -> Unit)?,
) {
    GridItem(
        modifier = modifier,
        onClick = { onClick(item) },
        onLongClick = { onLongClick(item) },
    ) {
        Box {
            AlbumImage(item)
            Badges(
                item = item,
                providerIconFetcher = providerIconFetcher,
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            modifier = Modifier.fillMaxWidth(),
            text = item.displayName,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
        Text(
            modifier = Modifier.fillMaxWidth(),
            text = item.localizedSubtitle().orEmpty(),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun AlbumImage(
    item: Album,
) {
    val primaryContainer = MaterialTheme.colorScheme.primary
    val background = MaterialTheme.colorScheme.background
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(RoundedCornerShape(8.dp)),
    ) {
        val vinylRecord = rememberVinylRecordPainter(
            backgroundColor = background,
            labelColor = primaryContainer,
        )

        val cutStripShape = remember { CutStripShape() }

        Image(
            painter = vinylRecord,
            contentDescription = stringResource(Res.string.cd_vinyl_record),
            modifier = Modifier.fillMaxSize().clip(CircleShape),
        )

        AsyncImage(
            placeholder = vinylRecord,
            fallback = vinylRecord,
            model = item.image(ImageType.THUMB)?.url,
            contentDescription = item.displayName,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .clip(cutStripShape),
        )
    }
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
    providerIconFetcher: (@Composable (Modifier, String) -> Unit)? = null,
) {
    GridItem(
        modifier = modifier,
        onClick = { onClick(item) },
        onLongClick = { onLongClick(item) },
    ) {
        Box {
            PlaylistImage(item)
            Badges(
                item = item,
                providerIconFetcher = providerIconFetcher,
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            modifier = Modifier.fillMaxWidth(),
            text = item.displayName,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
        Text(
            modifier = Modifier.fillMaxWidth(),
            text = item.localizedSubtitle().orEmpty(),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun PlaylistImage(
    item: Playlist,
) {
    val primary = MaterialTheme.colorScheme.primary
    val primaryContainer = MaterialTheme.colorScheme.primaryContainer
    val onPrimaryContainer = MaterialTheme.colorScheme.onPrimaryContainer
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(RoundedCornerShape(8.dp)),
    ) {
        val notebookCutShape = remember { NotebookCutShape() }

        // Draw notebook cover background (clipped)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(notebookCutShape)
                .background(primaryContainer),
        )

        // Draw binding dots, sized and positioned relative to the cut strip
        val ellipseCount = 7
        Canvas(
            modifier = Modifier.fillMaxSize(),
        ) {
            val bindingWidth = size.width * NotebookCutShape.STRIP_FRACTION
            val dotRadius = bindingWidth * 0.4f
            val centerX = bindingWidth / 2f
            val padding = dotRadius * 2
            val availableHeight = size.height - padding * 2
            val spacing = if (ellipseCount > 1) {
                availableHeight / (ellipseCount - 1)
            } else {
                0f
            }

            for (i in 0 until ellipseCount) {
                drawCircle(
                    color = primary,
                    radius = dotRadius,
                    center = Offset(x = centerX, y = padding + i * spacing),
                )
            }
        }

        // Draw artwork (clipped)
        val placeholder = rememberPlaceholderPainter(
            backgroundColor = primaryContainer,
            iconColor = onPrimaryContainer,
            icon = PlaylistIcon,
        )
        AsyncImage(
            placeholder = placeholder,
            fallback = placeholder,
            model = item.image(ImageType.THUMB)?.url,
            contentDescription = item.displayName,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .clip(notebookCutShape),
        )
    }
}

@Composable
fun PodcastGridItem(
    modifier: Modifier = Modifier,
    item: Podcast,
    onClick: (Podcast) -> Unit,
    onLongClick: (Podcast) -> Unit,
    providerIconFetcher: (@Composable (Modifier, String) -> Unit)? = null,
) {
    GridItem(
        modifier = modifier,
        onClick = { onClick(item) },
        onLongClick = { onLongClick(item) },
    ) {
        Box {
            PodcastImage(item)
            Badges(
                item = item,
                providerIconFetcher = providerIconFetcher,
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            modifier = Modifier.fillMaxWidth(),
            text = item.displayName,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
        Text(
            modifier = Modifier.fillMaxWidth(),
            text = item.localizedSubtitle().orEmpty(),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun PodcastImage(
    item: Podcast,
) {
    val primary = MaterialTheme.colorScheme.primary
    val primaryContainer = MaterialTheme.colorScheme.primaryContainer
    val onPrimaryContainer = MaterialTheme.colorScheme.onPrimaryContainer
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(RoundedCornerShape(8.dp)),
    ) {
        val cornerCutShape = remember { CornerCutShape() }

        // Draw background (clipped)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(cornerCutShape)
                .background(primaryContainer),
        )

        // Draw concentric circles in the cut corner area (centered on cut edge, rippling outward)
        val circleCount = 10
        Canvas(
            modifier = Modifier.fillMaxSize(),
        ) {
            val center = (size.width / 3f) * 0.7f
            val spacing = center / circleCount

            for (i in 1 until circleCount) {
                drawCircle(
                    color = primary,
                    radius = i * spacing,
                    center = Offset(center, center),
                    style = Stroke(width = 2f),
                )
            }
        }

        // Draw artwork (clipped)
        val placeholder = rememberPlaceholderPainter(
            backgroundColor = primaryContainer,
            iconColor = onPrimaryContainer,
            icon = Icons.Default.Podcasts,
        )
        AsyncImage(
            placeholder = placeholder,
            fallback = placeholder,
            model = item.image(ImageType.THUMB)?.url,
            contentDescription = item.displayName,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .clip(cornerCutShape),
        )
    }
}

/**
 * Track media item with waveform overlay.
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
    providerIconFetcher: (@Composable (Modifier, String) -> Unit)?,
) {
    GridItem(
        modifier = modifier,
        onClick = { onClick(item) },
        onLongClick = { onLongClick(item) },
    ) {
        Box {
            TrackImage(item)
            Badges(
                item = item,
                providerIconFetcher = providerIconFetcher,
            )
        }
        GridPlayableItemLabels(item)
    }
}

@Composable
private fun TrackImage(
    item: PlayableItem,
) {
    val primary = MaterialTheme.colorScheme.primary
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
            icon = TrackIcon,
        )
        AsyncImage(
            placeholder = placeholder,
            fallback = placeholder,
            model = item.image(ImageType.THUMB)?.url,
            contentDescription = item.displayName,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )

        // Draw waveform overlay at the bottom
        val waveformPainter = rememberWaveformPainter(primary)
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(1f / 3f)
                .align(Alignment.BottomCenter),
        ) {
            with(waveformPainter) {
                draw(size)
            }
        }
    }
}

@Composable
internal fun PodcastEpisodeGridItem(
    modifier: Modifier = Modifier,
    item: PodcastEpisode,
    onClick: (PodcastEpisode) -> Unit,
    onLongClick: (PodcastEpisode) -> Unit,
    providerIconFetcher: (@Composable (Modifier, String) -> Unit)?,
) {
    GridItem(
        modifier = modifier,
        onClick = { onClick(item) },
        onLongClick = { onLongClick(item) },
    ) {
        Box {
            PodcastEpisodeImage(item)
            Badges(
                item = item,
                providerIconFetcher = providerIconFetcher,
            )
            ProgressBadge(
                fullyPlayed = item.fullyPlayed,
                resumePositionMs = item.resumePositionMs,
            )
        }
        GridPlayableItemLabels(item)
    }
}

@Composable
private fun PodcastEpisodeImage(
    item: PlayableItem,
) {
    val primary = MaterialTheme.colorScheme.primary
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
            icon = Icons.Default.Podcasts,
        )
        AsyncImage(
            placeholder = placeholder,
            fallback = placeholder,
            model = item.image(ImageType.THUMB)?.url,
            contentDescription = item.displayName,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )

        // Draw concentric circles from bottom center
        val circleCount = 8
        Canvas(
            modifier = Modifier.fillMaxSize(),
        ) {
            val bottomCenter = Offset(size.width / 2f, size.height)
            val maxRadius = size.height / 2f
            val spacing = maxRadius / circleCount

            for (i in 1..circleCount) {
                val alpha = 1f - (i.toFloat() / circleCount) // Fade as circles get bigger
                if (alpha > 0f) {
                    drawCircle(
                        color = primary.copy(alpha = alpha),
                        radius = i * spacing,
                        center = bottomCenter,
                        style = Stroke(width = 3f),
                    )
                }
            }
        }
    }
}

/**
 * Radio station media item with wavy octagon shape.
 *
 * @param item The radio station item to display
 * @param onClick Click handler

 */
@Composable
internal fun RadioGridItem(
    modifier: Modifier = Modifier,
    item: RadioStation,
    onClick: (RadioStation) -> Unit,
    onLongClick: (RadioStation) -> Unit,
    providerIconFetcher: (@Composable (Modifier, String) -> Unit)?,
) {
    GridItem(
        modifier = modifier,
        onClick = { onClick(item) },
        onLongClick = { onLongClick(item) },
    ) {
        Box {
            RadioImage(item)
            Badges(
                item = item,
                providerIconFetcher = providerIconFetcher,
            )
        }
        GridPlayableItemLabels(item)
    }
}

@Composable
private fun RadioImage(
    item: PlayableItem,
) {
    val primaryContainer = MaterialTheme.colorScheme.primaryContainer
    val onPrimaryContainer = MaterialTheme.colorScheme.onPrimaryContainer
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(WavyHexagonShape())
            .background(primaryContainer),
    ) {
        val placeholder = rememberPlaceholderPainter(
            backgroundColor = primaryContainer,
            iconColor = onPrimaryContainer,
            icon = RadioIcon,
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

/**
 * Audiobook media item with book spine design.
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
    providerIconFetcher: (@Composable (Modifier, String) -> Unit)?,
) {
    GridItem(
        modifier = modifier,
        onClick = { onClick(item) },
        onLongClick = { onLongClick(item) },
    ) {
        Box {
            AudiobookImage(item)
            Badges(
                item = item,
                providerIconFetcher = providerIconFetcher,
            )
            ProgressBadge(
                fullyPlayed = item.fullyPlayed,
                resumePositionMs = item.resumePositionMs,
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            modifier = Modifier.fillMaxWidth(),
            text = item.displayName,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
        Text(
            modifier = Modifier.fillMaxWidth(),
            text = item.localizedSubtitle().orEmpty(),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun AudiobookImage(
    item: Audiobook,
) {
    val primary = MaterialTheme.colorScheme.primary
    val primaryContainer = MaterialTheme.colorScheme.primaryContainer
    val onPrimaryContainer = MaterialTheme.colorScheme.onPrimaryContainer
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(RoundedCornerShape(8.dp)),
    ) {
        val spineWidth = 8.dp
        val bookSpineShape = remember(spineWidth) { BookSpineShape(spineWidth) }

        // Draw book cover background (clipped)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(bookSpineShape)
                .background(primaryContainer),
        )

        // Draw spine strip on the left
        Box(
            modifier = Modifier
                .width(spineWidth)
                .fillMaxHeight()
                .background(primary),
        )

        // Draw artwork (clipped to exclude spine)
        val placeholder = rememberPlaceholderPainter(
            backgroundColor = primaryContainer,
            iconColor = onPrimaryContainer,
            icon = BookAudioIcon,
        )
        AsyncImage(
            placeholder = placeholder,
            fallback = placeholder,
            model = item.image(ImageType.THUMB)?.url,
            contentDescription = item.displayName,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .clip(bookSpineShape),
        )
    }
}

@Composable
private fun GridPlayableItemLabels(item: PlayableItem) {
    val subtitleText = (item as? AppMediaItem)?.localizedSubtitle()
    Spacer(Modifier.height(4.dp))
    Text(
        text = "${item.displayName}${
            item.version
                ?.trim()?.takeIf { it.isNotBlank() }?.let { " ($it)" } ?: ""
        }",
        style = MaterialTheme.typography.bodyMedium,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.fillMaxWidth(),
        textAlign = TextAlign.Center,
    )
    Text(
        text = subtitleText.orEmpty(),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.fillMaxWidth(),
        textAlign = TextAlign.Center,
    )
}

/**
 * Common wrapper for media items with click handling.
 */
@Composable
private fun GridItem(
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    BoxWithConstraints {
        val cellWidthModifier = if (constraints.hasBoundedWidth) {
            Modifier.fillMaxWidth()
        } else {
            Modifier.width(gridItemMinSize())
        }
        Column(
            modifier = cellWidthModifier
                .then(modifier)
                .clip(RoundedCornerShape(8.dp))
                .combinedClickable(onClick = onClick, onLongClick = onLongClick)
                .padding(4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            content()
        }
    }
}

@Composable
fun BoxScope.Badges(
    item: AppMediaItem,
    providerIconFetcher: (@Composable (Modifier, String) -> Unit)?,
    badgeSize: Dp = 16.dp,
    badgePadding: Dp = 0.dp,
) {
    val modifier = Modifier.padding(badgePadding).size(badgeSize)
    val bottomEnd = modifier.align(Alignment.BottomEnd)
    if (item.favorite == true) {
        Icon(
            modifier = bottomEnd,
            imageVector = Icons.Filled.Favorite,
            contentDescription = stringResource(Res.string.cd_favorite),
            tint = favoriteTint,
        )
    } else {
        providerIconFetcher?.invoke(
            bottomEnd.background(Color.Gray, CircleShape),
            item.provider,
        )
    }
    if (item.isExplicit) {
        Icon(
            modifier = modifier.align(Alignment.TopEnd)
                .background(Color.White, RoundedCornerShape(2.dp)),
            imageVector = Icons.Filled.Explicit,
            contentDescription = stringResource(Res.string.cd_favorite),
            tint = Color.Black,
        )
    }
}

/**
 * Progress indicator badge for audiobooks and podcast episodes.
 * Shows a checkmark for fully played items, or a clock for in-progress items.
 * Positioned at top-end of the image Box (bottom-end is used by Badges).
 */
@Composable
fun BoxScope.ProgressBadge(
    fullyPlayed: Boolean?,
    resumePositionMs: Long?,
) {
    when {
        fullyPlayed == true -> {
            Icon(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(18.dp)
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
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(18.dp)
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
    onClick: (Track) -> Unit,
    onLongClick: (Track) -> Unit,
    providerIconFetcher: (@Composable (Modifier, String) -> Unit)?,
) {
    RowItem(
        modifier = modifier,
        name = item.displayName,
        subtitle = item.localizedSubtitle(),
        imageContent = {
            TrackImage(item)
            Badges(
                item = item,
                providerIconFetcher = providerIconFetcher,
            )
        },
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
    providerIconFetcher: (@Composable (Modifier, String) -> Unit)?,
) {
    RowItem(
        modifier = modifier,
        name = item.displayName,
        subtitle = item.localizedSubtitle(),
        imageContent = {
            AlbumImage(item)
            Badges(
                item = item,
                providerIconFetcher = providerIconFetcher,
            )
        },
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
    providerIconFetcher: (@Composable (Modifier, String) -> Unit)?,
) {
    RowItem(
        modifier = modifier,
        name = item.displayName,
        subtitle = item.localizedSubtitle(),
        imageContent = {
            ArtistImage(item)
            Badges(
                item = item,
                providerIconFetcher = providerIconFetcher,
            )
        },
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
    providerIconFetcher: (@Composable (Modifier, String) -> Unit)?,
) {
    RowItem(
        modifier = modifier,
        name = item.displayName,
        subtitle = item.localizedSubtitle(),
        imageContent = {
            PlaylistImage(item)
            Badges(
                item = item,
                providerIconFetcher = providerIconFetcher,
            )
        },
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
    providerIconFetcher: (@Composable (Modifier, String) -> Unit)?,
) {
    RowItem(
        modifier = modifier,
        name = item.displayName,
        subtitle = item.localizedSubtitle(),
        imageContent = {
            PodcastImage(item)
            Badges(
                item = item,
                providerIconFetcher = providerIconFetcher,
            )
        },
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
    providerIconFetcher: (@Composable (Modifier, String) -> Unit)?,
) {
    RowItem(
        modifier = modifier,
        name = item.displayName,
        subtitle = item.localizedSubtitle(),
        imageContent = {
            PodcastEpisodeImage(item)
            Badges(
                item = item,
                providerIconFetcher = providerIconFetcher,
            )
            ProgressBadge(
                fullyPlayed = item.fullyPlayed,
                resumePositionMs = item.resumePositionMs,
            )
        },
        onClick = { onClick(item) },
        onLongClick = { onLongClick(item) },
    )
}

@Composable
internal fun RadioRowItem(
    modifier: Modifier = Modifier,
    item: RadioStation,
    onClick: (RadioStation) -> Unit,
    onLongClick: (RadioStation) -> Unit,
    providerIconFetcher: (@Composable (Modifier, String) -> Unit)?,
) {
    RowItem(
        modifier = modifier,
        name = item.displayName,
        subtitle = item.localizedSubtitle(),
        imageContent = {
            RadioImage(item)
            Badges(
                item = item,
                providerIconFetcher = providerIconFetcher,
            )
        },
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
    providerIconFetcher: (@Composable (Modifier, String) -> Unit)? = null,
) {
    GridItem(
        modifier = modifier,
        onClick = { onClick(item) },
        onLongClick = { onLongClick(item) },
    ) {
        Box {
            GenreImage(item)
            Badges(
                item = item,
                providerIconFetcher = providerIconFetcher,
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            modifier = Modifier.fillMaxWidth(),
            text = item.displayName,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
        Text(
            modifier = Modifier.fillMaxWidth(),
            text = item.localizedSubtitle().orEmpty(),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun GenreImage(
    item: Genre,
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
            icon = GenreIcon,
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

@Composable
internal fun GenreRowItem(
    modifier: Modifier = Modifier,
    item: Genre,
    onClick: (Genre) -> Unit,
    onLongClick: (Genre) -> Unit,
    providerIconFetcher: (@Composable (Modifier, String) -> Unit)?,
) {
    RowItem(
        modifier = modifier,
        name = item.displayName,
        subtitle = item.localizedSubtitle(),
        imageContent = {
            GenreImage(item)
            Badges(
                item = item,
                providerIconFetcher = providerIconFetcher,
            )
        },
        onClick = { onClick(item) },
        onLongClick = { onLongClick(item) },
    )
}

@Composable
internal fun AudiobookRowItem(
    modifier: Modifier = Modifier,
    item: Audiobook,
    onClick: (Audiobook) -> Unit,
    onLongClick: (Audiobook) -> Unit,
    providerIconFetcher: (@Composable (Modifier, String) -> Unit)?,
) {
    RowItem(
        modifier = modifier,
        name = item.displayName,
        subtitle = item.localizedSubtitle(),
        imageContent = {
            AudiobookImage(item)
            Badges(
                item = item,
                providerIconFetcher = providerIconFetcher,
            )
            ProgressBadge(
                fullyPlayed = item.fullyPlayed,
                resumePositionMs = item.resumePositionMs,
            )
        },
        onClick = { onClick(item) },
        onLongClick = { onLongClick(item) },
    )
}

@Composable
private fun RowItem(
    modifier: Modifier = Modifier,
    name: String,
    subtitle: String?,
    imageContent: @Composable BoxScope.() -> Unit,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.size(rowImageSize())) { imageContent() }
        Spacer(Modifier.width(12.dp))
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = name,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
