package io.music_assistant.client.ui.compose.common.items

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.FeaturedPlayList
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Podcasts
import androidx.compose.material.icons.filled.Radio
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import io.music_assistant.client.data.model.client.AppMediaItem
import io.music_assistant.client.data.model.client.PlayableItem
import io.music_assistant.client.ui.compose.common.painters.rememberPlaceholderPainter
import io.music_assistant.client.ui.compose.common.painters.rememberVinylRecordPainter
import io.music_assistant.client.ui.compose.common.painters.rememberWaveformPainter

/**
 * Artist media item with circular image.
 *
 * @param item The artist item to display
 * @param serverUrl Server URL for image loading
 * @param onClick Click handler
 * @param showSubtitle Whether to show subtitle (default true)
 */
@Composable
fun ArtistGridItem(
    modifier: Modifier = Modifier,
    item: AppMediaItem.Artist,
    serverUrl: String?,
    onClick: (AppMediaItem.Artist) -> Unit,
    onLongClick: (AppMediaItem.Artist) -> Unit,
    showSubtitle: Boolean,
    providerIconFetcher: (@Composable (Modifier, String) -> Unit)?
) {
    GridItem(
        modifier = modifier,
        onClick = { onClick(item) },
        onLongClick = { onLongClick(item) },
    ) {
        Box {
            ArtistImage(96.dp, item, serverUrl)
            Badges(
                item = item,
                providerIconFetcher = providerIconFetcher
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            modifier = Modifier.width(96.dp),
            text = item.name,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (showSubtitle) {
            Text(
                modifier = Modifier.width(96.dp),
                text = item.subtitle.orEmpty(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun ArtistImage(
    itemSize: Dp,
    item: AppMediaItem.Artist,
    serverUrl: String?
) {
    val primaryContainer = MaterialTheme.colorScheme.primaryContainer
    val onPrimaryContainer = MaterialTheme.colorScheme.onPrimaryContainer
    Box(
        modifier = Modifier
            .size(itemSize)
            .clip(CircleShape)
            .background(primaryContainer)
    ) {
        val placeholder = rememberPlaceholderPainter(
            backgroundColor = primaryContainer,
            iconColor = onPrimaryContainer,
            icon = Icons.Default.Mic
        )
        AsyncImage(
            placeholder = placeholder,
            fallback = placeholder,
            model = item.imageInfo?.url(serverUrl),
            contentDescription = item.name,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
    }
}

/**
 * Album media item with vinyl record design.
 *
 * @param item The album item to display
 * @param serverUrl Server URL for image loading
 * @param onClick Click handler
 * @param showSubtitle Whether to show subtitle (default true)
 */
@Composable
fun AlbumGridItem(
    modifier: Modifier = Modifier,
    item: AppMediaItem.Album,
    serverUrl: String?,
    onClick: (AppMediaItem.Album) -> Unit,
    onLongClick: (AppMediaItem.Album) -> Unit,
    showSubtitle: Boolean = true,
    providerIconFetcher: (@Composable (Modifier, String) -> Unit)?
) {
    GridItem(
        modifier = modifier,
        onClick = { onClick(item) },
        onLongClick = { onLongClick(item) },
    ) {
        Box {
            AlbumImage(96.dp, item, serverUrl)
            Badges(
                item = item,
                providerIconFetcher = providerIconFetcher
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            modifier = Modifier.width(96.dp),
            text = item.name,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
        if (showSubtitle) {
            Text(
                modifier = Modifier.width(96.dp),
                text = item.subtitle.orEmpty(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun AlbumImage(
    itemSize: Dp,
    item: AppMediaItem.Album,
    serverUrl: String?
) {
    val primaryContainer = MaterialTheme.colorScheme.primary
    val background = MaterialTheme.colorScheme.background
    Box(
        modifier = Modifier
            .size(itemSize)
            .clip(RoundedCornerShape(8.dp))
    ) {
        val vinylRecord = rememberVinylRecordPainter(
            backgroundColor = background,
            labelColor = primaryContainer,
        )
        val stripWidth = 10.dp
        val holeRadius = 16.dp

        val cutStripShape = remember(stripWidth) { CutStripShape(stripWidth) }
        val holeShape = remember(holeRadius) { HoleShape(holeRadius) }

        Image(
            painter = vinylRecord,
            contentDescription = "Vinyl Record",
            modifier = Modifier.fillMaxSize().clip(CircleShape)
        )

        AsyncImage(
            placeholder = vinylRecord,
            fallback = vinylRecord,
            model = item.imageInfo?.url(serverUrl),
            contentDescription = item.name,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .clip(cutStripShape)
                .clip(holeShape)
        )
    }
}

/**
 * Playlist media item.
 *
 * @param item The playlist item to display
 * @param serverUrl Server URL for image loading
 * @param onClick Click handler
 * @param showSubtitle Whether to show subtitle (default true)
 */
@Composable
fun PlaylistGridItem(
    modifier: Modifier = Modifier,
    item: AppMediaItem.Playlist,
    serverUrl: String?,
    onClick: (AppMediaItem.Playlist) -> Unit,
    onLongClick: (AppMediaItem.Playlist) -> Unit,
    showSubtitle: Boolean = true,
    providerIconFetcher: (@Composable (Modifier, String) -> Unit)? = null
) {
    GridItem(
        modifier = modifier,
        onClick = { onClick(item) },
        onLongClick = { onLongClick(item) },
    ) {
        Box {
            PlaylistImage(96.dp, item, serverUrl)
            Badges(
                item = item,
                providerIconFetcher = providerIconFetcher
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            modifier = Modifier.width(96.dp),
            text = item.name,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
        if (showSubtitle) {
            Text(
                modifier = Modifier.width(96.dp),
                text = item.subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun PlaylistImage(
    itemSize: Dp,
    item: AppMediaItem.Playlist,
    serverUrl: String?
) {
    val primary = MaterialTheme.colorScheme.primary
    val primaryContainer = MaterialTheme.colorScheme.primaryContainer
    val onPrimaryContainer = MaterialTheme.colorScheme.onPrimaryContainer
    Box(
        modifier = Modifier
            .size(itemSize)
            .clip(RoundedCornerShape(8.dp))
    ) {
        val bindingWidth = 10.dp
        val notebookCutShape = remember(bindingWidth) { NotebookCutShape(bindingWidth) }

        // Draw notebook cover background (clipped)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(notebookCutShape)
                .background(primaryContainer)
        )

        // Draw binding ellipses in the cut area
        val ellipseRadius = 4.dp
        val ellipseCount = 7
        Canvas(
            modifier = Modifier.fillMaxSize()
        ) {
            val ellipseRadiusPx = ellipseRadius.toPx()
            val topPadding = ellipseRadiusPx * 2
            val bottomPadding = ellipseRadiusPx * 2
            val availableHeight = size.height - topPadding - bottomPadding
            val spacing = if (ellipseCount > 1) {
                availableHeight / (ellipseCount - 1)
            } else {
                0f
            }

            // Draw ellipses in the binding strip area
            for (i in 0 until ellipseCount) {
                val y = topPadding + (i * spacing)
                drawCircle(
                    color = primary,
                    radius = ellipseRadiusPx,
                    center = Offset(x = bindingWidth.toPx() / 2f, y = y)
                )
            }
        }

        // Draw artwork (clipped)
        val placeholder = rememberPlaceholderPainter(
            backgroundColor = primaryContainer,
            iconColor = onPrimaryContainer,
            icon = Icons.AutoMirrored.Filled.FeaturedPlayList
        )
        AsyncImage(
            placeholder = placeholder,
            fallback = placeholder,
            model = item.imageInfo?.url(serverUrl),
            contentDescription = item.name,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .clip(notebookCutShape)
        )
    }
}

@Composable
fun PodcastGridItem(
    modifier: Modifier = Modifier,
    item: AppMediaItem.Podcast,
    serverUrl: String?,
    onClick: (AppMediaItem.Podcast) -> Unit,
    onLongClick: (AppMediaItem.Podcast) -> Unit,
    showSubtitle: Boolean = true,
    providerIconFetcher: (@Composable (Modifier, String) -> Unit)? = null
) {
    GridItem(
        modifier = modifier,
        onClick = { onClick(item) },
        onLongClick = { onLongClick(item) },
    ) {
        Box {
            PodcastImage(96.dp, item, serverUrl)
            Badges(
                item = item,
                providerIconFetcher = providerIconFetcher
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            modifier = Modifier.width(96.dp),
            text = item.name,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
        if (showSubtitle) {
            Text(
                modifier = Modifier.width(96.dp),
                text = item.subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun PodcastImage(
    itemSize: Dp,
    item: AppMediaItem.Podcast,
    serverUrl: String?
) {
    val primary = MaterialTheme.colorScheme.primary
    val primaryContainer = MaterialTheme.colorScheme.primaryContainer
    val onPrimaryContainer = MaterialTheme.colorScheme.onPrimaryContainer
    Box(
        modifier = Modifier
            .size(itemSize)
            .clip(RoundedCornerShape(8.dp))
    ) {
        val cutSize = itemSize / 3
        val cornerCutShape = remember(cutSize) { CornerCutShape(cutSize) }

        // Draw background (clipped)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(cornerCutShape)
                .background(primaryContainer)
        )

        // Draw concentric circles in the cut corner area (centered on cut edge, rippling outward)
        val circleCount = 10
        Canvas(
            modifier = Modifier.fillMaxSize()
        ) {
            val center = cutSize.toPx() * 0.7f
            val spacing = center / circleCount

            for (i in 1 until circleCount) {
                drawCircle(
                    color = primary,
                    radius = i * spacing,
                    center = Offset(center, center),
                    style = Stroke(width = 2f)
                )
            }
        }

        // Draw artwork (clipped)
        val placeholder = rememberPlaceholderPainter(
            backgroundColor = primaryContainer,
            iconColor = onPrimaryContainer,
            icon = Icons.Default.Podcasts
        )
        AsyncImage(
            placeholder = placeholder,
            fallback = placeholder,
            model = item.imageInfo?.url(serverUrl),
            contentDescription = item.name,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .clip(cornerCutShape)
        )
    }
}

/**
 * Track media item with waveform overlay.
 *
 * @param item The track item to display
 * @param serverUrl Server URL for image loading
 * @param onClick Click handler
 * @param showSubtitle Whether to show subtitle (default true)
 */
@Composable
internal fun TrackGridItem(
    modifier: Modifier = Modifier,
    item: AppMediaItem.Track,
    serverUrl: String?,
    onClick: (AppMediaItem.Track) -> Unit,
    onLongClick: (AppMediaItem.Track) -> Unit,
    showSubtitle: Boolean = true,
    providerIconFetcher: (@Composable (Modifier, String) -> Unit)?
) {
    GridItem(
        modifier = modifier,
        onClick = { onClick(item) },
        onLongClick = { onLongClick(item) },
    ) {
        Box {
            TrackImage(96.dp, item, serverUrl)
            Badges(
                item = item,
                providerIconFetcher = providerIconFetcher
            )
        }
        GridPlayableItemLabels(item, 96.dp, showSubtitle)
    }
}

@Composable
private fun TrackImage(
    itemSize: Dp,
    item: PlayableItem,
    serverUrl: String?,
) {
    val primary = MaterialTheme.colorScheme.primary
    val primaryContainer = MaterialTheme.colorScheme.primaryContainer
    val onPrimaryContainer = MaterialTheme.colorScheme.onPrimaryContainer
    Box(
        modifier = Modifier
            .size(itemSize)
            .clip(RoundedCornerShape(8.dp))
            .background(primaryContainer)
    ) {
        val placeholder = rememberPlaceholderPainter(
            backgroundColor = primaryContainer,
            iconColor = onPrimaryContainer,
            icon = Icons.Default.MusicNote
        )
        AsyncImage(
            placeholder = placeholder,
            fallback = placeholder,
            model = item.imageInfo?.url(serverUrl),
            contentDescription = item.name,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )

        // Draw waveform overlay at the bottom
        val waveformPainter = rememberWaveformPainter(primary)
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(itemSize / 3)
                .align(Alignment.BottomCenter)
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
    item: AppMediaItem.PodcastEpisode,
    serverUrl: String?,
    onClick: (AppMediaItem.PodcastEpisode) -> Unit,
    onLongClick: (AppMediaItem.PodcastEpisode) -> Unit,
    showSubtitle: Boolean = true,
    providerIconFetcher: (@Composable (Modifier, String) -> Unit)?
) {
    GridItem(
        modifier = modifier,
        onClick = { onClick(item) },
        onLongClick = { onLongClick(item) },
    ) {
        Box {
            PodcastEpisodeImage(96.dp, item, serverUrl)
            Badges(
                item = item,
                providerIconFetcher = providerIconFetcher
            )
            ProgressBadge(
                fullyPlayed = item.fullyPlayed,
                resumePositionMs = item.resumePositionMs
            )
        }
        GridPlayableItemLabels(item, 96.dp, showSubtitle)
    }
}

@Composable
private fun PodcastEpisodeImage(
    itemSize: Dp,
    item: PlayableItem,
    serverUrl: String?,
) {
    val primary = MaterialTheme.colorScheme.primary
    val primaryContainer = MaterialTheme.colorScheme.primaryContainer
    val onPrimaryContainer = MaterialTheme.colorScheme.onPrimaryContainer
    Box(
        modifier = Modifier
            .size(itemSize)
            .clip(RoundedCornerShape(8.dp))
            .background(primaryContainer)
    ) {
        val placeholder = rememberPlaceholderPainter(
            backgroundColor = primaryContainer,
            iconColor = onPrimaryContainer,
            icon = Icons.Default.Podcasts
        )
        AsyncImage(
            placeholder = placeholder,
            fallback = placeholder,
            model = item.imageInfo?.url(serverUrl),
            contentDescription = item.name,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )

        // Draw concentric circles from bottom center
        val circleCount = 8
        Canvas(
            modifier = Modifier.fillMaxSize()
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
                        style = Stroke(width = 3f)
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
 * @param serverUrl Server URL for image loading
 * @param onClick Click handler
 * @param showSubtitle Whether to show subtitle (default true)
 */
@Composable
internal fun RadioGridItem(
    modifier: Modifier = Modifier,
    item: AppMediaItem.RadioStation,
    serverUrl: String?,
    onClick: (AppMediaItem.RadioStation) -> Unit,
    onLongClick: (AppMediaItem.RadioStation) -> Unit,
    showSubtitle: Boolean = true,
    providerIconFetcher: (@Composable (Modifier, String) -> Unit)?
) {
    GridItem(
        modifier = modifier,
        onClick = { onClick(item) },
        onLongClick = { onLongClick(item) },
    ) {
        Box {
            RadioImage(96.dp, item, serverUrl)
            Badges(
                item = item,
                providerIconFetcher = providerIconFetcher
            )
        }
        GridPlayableItemLabels(item, 96.dp, showSubtitle)
    }
}

@Composable
private fun RadioImage(
    itemSize: Dp,
    item: PlayableItem,
    serverUrl: String?
) {
    val primaryContainer = MaterialTheme.colorScheme.primaryContainer
    val onPrimaryContainer = MaterialTheme.colorScheme.onPrimaryContainer
    Box(
        modifier = Modifier
            .size(itemSize)
            .clip(WavyHexagonShape())
            .background(primaryContainer)
    ) {
        val placeholder = rememberPlaceholderPainter(
            backgroundColor = primaryContainer,
            iconColor = onPrimaryContainer,
            icon = Icons.Default.Radio
        )
        AsyncImage(
            placeholder = placeholder,
            fallback = placeholder,
            model = item.imageInfo?.url(serverUrl),
            contentDescription = item.name,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
    }
}

/**
 * Audiobook media item with book spine design.
 *
 * @param item The audiobook item to display
 * @param serverUrl Server URL for image loading
 * @param onClick Click handler
 * @param showSubtitle Whether to show subtitle (default true)
 */
@Composable
internal fun AudiobookGridItem(
    modifier: Modifier = Modifier,
    item: AppMediaItem.Audiobook,
    serverUrl: String?,
    onClick: (AppMediaItem.Audiobook) -> Unit,
    onLongClick: (AppMediaItem.Audiobook) -> Unit,
    showSubtitle: Boolean = true,
    providerIconFetcher: (@Composable (Modifier, String) -> Unit)?
) {
    GridItem(
        modifier = modifier,
        onClick = { onClick(item) },
        onLongClick = { onLongClick(item) },
    ) {
        Box {
            AudiobookImage(96.dp, item, serverUrl)
            Badges(
                item = item,
                providerIconFetcher = providerIconFetcher
            )
            ProgressBadge(
                fullyPlayed = item.fullyPlayed,
                resumePositionMs = item.resumePositionMs
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            modifier = Modifier.width(96.dp),
            text = item.name,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
        if (showSubtitle) {
            Text(
                modifier = Modifier.width(96.dp),
                text = item.subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun AudiobookImage(
    itemSize: Dp,
    item: AppMediaItem.Audiobook,
    serverUrl: String?
) {
    val primary = MaterialTheme.colorScheme.primary
    val primaryContainer = MaterialTheme.colorScheme.primaryContainer
    val onPrimaryContainer = MaterialTheme.colorScheme.onPrimaryContainer
    Box(
        modifier = Modifier
            .size(itemSize)
            .clip(RoundedCornerShape(8.dp))
    ) {
        val spineWidth = 8.dp
        val bookSpineShape = remember(spineWidth) { BookSpineShape(spineWidth) }

        // Draw book cover background (clipped)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(bookSpineShape)
                .background(primaryContainer)
        )

        // Draw spine strip on the left
        Box(
            modifier = Modifier
                .width(spineWidth)
                .height(itemSize)
                .background(primary)
        )

        // Draw artwork (clipped to exclude spine)
        val placeholder = rememberPlaceholderPainter(
            backgroundColor = primaryContainer,
            iconColor = onPrimaryContainer,
            icon = Icons.AutoMirrored.Filled.MenuBook
        )
        AsyncImage(
            placeholder = placeholder,
            fallback = placeholder,
            model = item.imageInfo?.url(serverUrl),
            contentDescription = item.name,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .clip(bookSpineShape)
        )
    }
}

@Composable
private fun GridPlayableItemLabels(
    item: PlayableItem,
    itemSize: Dp,
    showSubtitle: Boolean
) {
    Spacer(Modifier.height(4.dp))
    Text(
        text = item.name,
        style = MaterialTheme.typography.bodyMedium,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.width(itemSize),
        textAlign = TextAlign.Center,
    )
    if (showSubtitle) {
        Text(
            text = item.subtitle.orEmpty(),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.width(itemSize),
            textAlign = TextAlign.Center,
        )
    }
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
    Column(
        modifier = modifier
            .wrapContentSize()
            .clip(RoundedCornerShape(8.dp))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        content()
    }
}

@Composable
fun BoxScope.Badges(
    item: AppMediaItem,
    providerIconFetcher: (@Composable (Modifier, String) -> Unit)?
) {
    val modifier =
        Modifier.align(Alignment.BottomEnd).size(16.dp)
    if (item.favorite == true) {
        Icon(
            modifier = modifier,
            imageVector = Icons.Filled.Favorite,
            contentDescription = "Favorite",
            tint = Color(0xFFEF7BC4)
        )
    } else {
        providerIconFetcher?.invoke(modifier.background(Color.Gray, CircleShape), item.provider)
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
                        CircleShape
                    )
                    .padding(2.dp),
                imageVector = Icons.Default.Check,
                contentDescription = "Fully played",
                tint = MaterialTheme.colorScheme.primary
            )
        }
        resumePositionMs != null && resumePositionMs > 0 -> {
            Icon(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(18.dp)
                    .background(
                        MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.85f),
                        CircleShape
                    )
                    .padding(2.dp),
                imageVector = Icons.Default.Schedule,
                contentDescription = "In progress",
                tint = MaterialTheme.colorScheme.tertiary
            )
        }
    }
}

// ── Row layout ────────────────────────────────────────────────────────────────

private val ROW_IMAGE_SIZE = 48.dp

@Composable
internal fun TrackRowItem(
    modifier: Modifier = Modifier,
    item: AppMediaItem.Track,
    serverUrl: String?,
    onClick: (AppMediaItem.Track) -> Unit,
    onLongClick: (AppMediaItem.Track) -> Unit,
    providerIconFetcher: (@Composable (Modifier, String) -> Unit)?
) {
    RowItem(
        modifier = modifier,
        name = item.name,
        subtitle = item.subtitle,
        imageContent = {
            TrackImage(ROW_IMAGE_SIZE, item, serverUrl)
            Badges(
                item = item,
                providerIconFetcher = providerIconFetcher
            )
        },
        onClick = { onClick(item) },
        onLongClick = { onLongClick(item) }
    )
}

@Composable
internal fun AlbumRowItem(
    modifier: Modifier = Modifier,
    item: AppMediaItem.Album,
    serverUrl: String?,
    onClick: (AppMediaItem.Album) -> Unit,
    onLongClick: (AppMediaItem.Album) -> Unit,
    providerIconFetcher: (@Composable (Modifier, String) -> Unit)?
) {
    RowItem(
        modifier = modifier,
        name = item.name,
        subtitle = item.subtitle,
        imageContent = {
            AlbumImage(ROW_IMAGE_SIZE, item, serverUrl)
            Badges(item = item, providerIconFetcher = providerIconFetcher)
        },
        onClick = { onClick(item) },
        onLongClick = { onLongClick(item) }
    )
}

@Composable
internal fun ArtistRowItem(
    modifier: Modifier = Modifier,
    item: AppMediaItem.Artist,
    serverUrl: String?,
    onClick: (AppMediaItem.Artist) -> Unit,
    onLongClick: (AppMediaItem.Artist) -> Unit,
    providerIconFetcher: (@Composable (Modifier, String) -> Unit)?
) {
    RowItem(
        modifier = modifier,
        name = item.name,
        subtitle = item.subtitle,
        imageContent = {
            ArtistImage(ROW_IMAGE_SIZE, item, serverUrl)
            Badges(item = item, providerIconFetcher = providerIconFetcher)
        },
        onClick = { onClick(item) },
        onLongClick = { onLongClick(item) }
    )
}

@Composable
internal fun PlaylistRowItem(
    modifier: Modifier = Modifier,
    item: AppMediaItem.Playlist,
    serverUrl: String?,
    onClick: (AppMediaItem.Playlist) -> Unit,
    onLongClick: (AppMediaItem.Playlist) -> Unit,
    providerIconFetcher: (@Composable (Modifier, String) -> Unit)?
) {
    RowItem(
        modifier = modifier,
        name = item.name,
        subtitle = item.subtitle,
        imageContent = {
            PlaylistImage(ROW_IMAGE_SIZE, item, serverUrl)
            Badges(item = item, providerIconFetcher = providerIconFetcher)
        },
        onClick = { onClick(item) },
        onLongClick = { onLongClick(item) }
    )
}

@Composable
internal fun PodcastRowItem(
    modifier: Modifier = Modifier,
    item: AppMediaItem.Podcast,
    serverUrl: String?,
    onClick: (AppMediaItem.Podcast) -> Unit,
    onLongClick: (AppMediaItem.Podcast) -> Unit,
    providerIconFetcher: (@Composable (Modifier, String) -> Unit)?
) {
    RowItem(
        modifier = modifier,
        name = item.name,
        subtitle = item.subtitle,
        imageContent = {
            PodcastImage(ROW_IMAGE_SIZE, item, serverUrl)
            Badges(item = item, providerIconFetcher = providerIconFetcher)
        },
        onClick = { onClick(item) },
        onLongClick = { onLongClick(item) }
    )
}

@Composable
internal fun PodcastEpisodeRowItem(
    modifier: Modifier = Modifier,
    item: AppMediaItem.PodcastEpisode,
    serverUrl: String?,
    onClick: (AppMediaItem.PodcastEpisode) -> Unit,
    onLongClick: (AppMediaItem.PodcastEpisode) -> Unit,
    providerIconFetcher: (@Composable (Modifier, String) -> Unit)?
) {
    RowItem(
        modifier = modifier,
        name = item.name,
        subtitle = item.subtitle,
        imageContent = {
            PodcastEpisodeImage(ROW_IMAGE_SIZE, item, serverUrl)
            Badges(
                item = item,
                providerIconFetcher = providerIconFetcher
            )
            ProgressBadge(
                fullyPlayed = item.fullyPlayed,
                resumePositionMs = item.resumePositionMs
            )
        },
        onClick = { onClick(item) },
        onLongClick = { onLongClick(item) }
    )
}

@Composable
internal fun RadioRowItem(
    modifier: Modifier = Modifier,
    item: AppMediaItem.RadioStation,
    serverUrl: String?,
    onClick: (AppMediaItem.RadioStation) -> Unit,
    onLongClick: (AppMediaItem.RadioStation) -> Unit,
    providerIconFetcher: (@Composable (Modifier, String) -> Unit)?
) {
    RowItem(
        modifier = modifier,
        name = item.name,
        subtitle = item.subtitle,
        imageContent = {
            RadioImage(ROW_IMAGE_SIZE, item, serverUrl)
            Badges(
                item = item,
                providerIconFetcher = providerIconFetcher
            )
        },
        onClick = { onClick(item) },
        onLongClick = { onLongClick(item) }
    )
}

@Composable
internal fun AudiobookRowItem(
    modifier: Modifier = Modifier,
    item: AppMediaItem.Audiobook,
    serverUrl: String?,
    onClick: (AppMediaItem.Audiobook) -> Unit,
    onLongClick: (AppMediaItem.Audiobook) -> Unit,
    providerIconFetcher: (@Composable (Modifier, String) -> Unit)?
) {
    RowItem(
        modifier = modifier,
        name = item.name,
        subtitle = item.subtitle,
        imageContent = {
            AudiobookImage(ROW_IMAGE_SIZE, item, serverUrl)
            Badges(
                item = item,
                providerIconFetcher = providerIconFetcher
            )
            ProgressBadge(
                fullyPlayed = item.fullyPlayed,
                resumePositionMs = item.resumePositionMs
            )
        },
        onClick = { onClick(item) },
        onLongClick = { onLongClick(item) }
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
    val titleStyle = MaterialTheme.typography.bodyMedium
    val twoLineHeight = with(LocalDensity.current) { (titleStyle.lineHeight.toPx() * 2).toDp() }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.size(ROW_IMAGE_SIZE)) { imageContent() }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Box(
                modifier = Modifier.fillMaxWidth().height(twoLineHeight),
                contentAlignment = Alignment.CenterStart
            ) {
                Text(
                    text = name,
                    style = titleStyle,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            if (!subtitle.isNullOrEmpty()) {
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
