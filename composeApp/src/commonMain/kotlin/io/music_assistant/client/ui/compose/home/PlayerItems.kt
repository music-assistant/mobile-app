package io.music_assistant.client.ui.compose.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import io.music_assistant.client.data.model.client.AppMediaItem
import io.music_assistant.client.data.model.client.AppMediaItem.Companion.description
import io.music_assistant.client.data.model.client.PlayerData
import io.music_assistant.client.ui.compose.common.action.PlayerAction
import io.music_assistant.client.ui.compose.common.painters.rememberPlaceholderPainter
import io.music_assistant.client.utils.formatDuration
import kotlin.time.DurationUnit

@Composable
fun CompactPlayerItem(
    item: PlayerData,
    serverUrl: String?,
    playerAction: (PlayerData, PlayerAction) -> Unit,
) {
    val track = item.queueInfo?.currentItem?.track
    val primaryContainer = MaterialTheme.colorScheme.primaryContainer
    val onPrimaryContainer = MaterialTheme.colorScheme.onPrimaryContainer

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Album cover on the far left
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(primaryContainer.copy(alpha = track?.let { 1f } ?: 0.4f)),
                contentAlignment = Alignment.Center
            ) {
                if (track != null) {
                    val placeholder = rememberPlaceholderPainter(
                        backgroundColor = primaryContainer,
                        iconColor = onPrimaryContainer,
                        icon = Icons.Default.MusicNote
                    )
                    AsyncImage(
                        placeholder = placeholder,
                        fallback = placeholder,
                        model = track.imageInfo?.url(serverUrl),
                        contentDescription = track.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Album,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = onPrimaryContainer.copy(alpha = 0.4f)
                    )
                }
            }

            // Track info
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = track?.name ?: "--idle--",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                track?.subtitle?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                } ?: run {
                    if (item.queueInfo?.currentItem?.isPlayable == false) {
                        Text(
                            text = "Cannot play this item",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            PlayerControls(
                playerData = item,
                playerAction = playerAction,
                showVolumeButtons = false,
                showAdditionalButtons = false,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FullPlayerItem(
    modifier: Modifier,
    item: PlayerData,
    isLocal: Boolean,
    serverUrl: String?,
    playerAction: (PlayerData, PlayerAction) -> Unit,
    onFavoriteClick: (AppMediaItem) -> Unit, // FIXME inconsistent stuff happening
) {
    val track = item.queueInfo?.currentItem?.track
    val primaryContainer = MaterialTheme.colorScheme.primaryContainer
    val onPrimaryContainer = MaterialTheme.colorScheme.onPrimaryContainer



    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            modifier = Modifier.padding(horizontal = 16.dp),
            text = item.player.providerType.takeIf { !isLocal } ?: "",
            fontSize = 12.sp,
        )
        Box(
            modifier = Modifier
                .weight(1f, fill = false)
                .aspectRatio(1f)
                .heightIn(max = 500.dp)
                .padding(16.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(primaryContainer.copy(alpha = track?.let { 1f } ?: 0.4f)),
            contentAlignment = Alignment.Center
        ) {
            if (track != null) {
                val placeholder =
                    rememberPlaceholderPainter(
                        backgroundColor = primaryContainer,
                        iconColor = onPrimaryContainer,
                        icon = track.defaultIcon
                    )
                track.imageInfo?.url(serverUrl)?.let {
                    AsyncImage(
                        placeholder = placeholder,
                        fallback = placeholder,
                        model = it,
                        contentDescription = track.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } ?: Icon(
                    imageVector = track.defaultIcon,
                    contentDescription = null,
                    modifier = Modifier.size(120.dp),
                    tint = onPrimaryContainer
                )
            } else {
                Icon(
                    imageVector = Icons.Default.Album,
                    contentDescription = null,
                    modifier = Modifier.size(120.dp),
                    tint = onPrimaryContainer.copy(alpha = 0.4f)
                )
            }
        }


        // Track info
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                modifier = Modifier.basicMarquee(),
                text = track?.name ?: "--idle--",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (item.queueInfo?.currentItem?.isPlayable == false) {
                Text(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    text = "Cannot play this item",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            } else {
                Text(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    text = track?.subtitle ?: "",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                modifier = Modifier.padding(horizontal = 16.dp),
                text = item.queueInfo?.currentItem?.audioFormat(item.playerId)?.description ?: "",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        val duration = track?.duration?.takeIf { it > 0 }?.toFloat()

        // Position is calculated in MainDataSource and updated twice per second
        val displayPosition = item.queueInfo?.elapsedTime?.toFloat() ?: 0f

        // Track user drag state separately
        var userDragPosition by remember { mutableStateOf<Float?>(null) }

        // Use user drag position if dragging, otherwise use calculated position
        val sliderPosition = userDragPosition ?: displayPosition

        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
        ) {// Progress bar
            Slider(
                value = sliderPosition,
                valueRange = duration?.let { 0f..it } ?: 0f..1f,
                enabled = displayPosition.takeIf { duration != null } != null,
                onValueChange = {
                    userDragPosition = it  // Track drag position locally
                },
                onValueChangeFinished = {
                    userDragPosition?.let { seekPos ->
                        playerAction(item, PlayerAction.SeekTo(seekPos.toLong()))
                        userDragPosition = null  // Clear drag state
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                thumb = {
                    sliderPosition.takeIf { duration != null }?.let {
                        SliderDefaults.Thumb(
                            interactionSource = remember { MutableInteractionSource() },
                            thumbSize = DpSize(16.dp, 16.dp),
                            colors = SliderDefaults.colors()
                                .copy(thumbColor = MaterialTheme.colorScheme.secondary),
                        )
                    }
                },
                track = { sliderState ->
                    val audiobook = track as? AppMediaItem.Audiobook
                    val chapters = audiobook?.chapters
                    Box {
                        SliderDefaults.Track(
                            sliderState = sliderState,
                            thumbTrackGapSize = 0.dp,
                            trackInsideCornerSize = 0.dp,
                            drawStopIndicator = null,
                            enabled = track != null && !item.player.isAnnouncing,
                            modifier = Modifier.height(8.dp)
                        )
                        if (!chapters.isNullOrEmpty() && duration != null && duration > 0f) {
                            val tickColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                            Canvas(modifier = Modifier.fillMaxWidth().height(8.dp)) {
                                chapters.drop(1).forEach { chapter ->
                                    val fraction = (chapter.start.toFloat() / duration).coerceIn(0f, 1f)
                                    val x = fraction * size.width
                                    drawLine(
                                        color = tickColor,
                                        start = Offset(x, 0f),
                                        end = Offset(x, size.height),
                                        strokeWidth = 2.dp.toPx()
                                    )
                                }
                            }
                        }
                    }
                }
            )

            // Duration labels
            Row(
                modifier = Modifier.fillMaxWidth().offset(y = (-16).dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = sliderPosition.takeIf { track != null }
                        .formatDuration(DurationUnit.SECONDS).takeIf { duration != null } ?: "",
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = track
                        ?.let { duration?.formatDuration(DurationUnit.SECONDS) ?: "\u221E" }
                        ?: "",
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        PlayerControls(
            playerData = item,
            playerAction = playerAction,
            showVolumeButtons = false,
            mainButtonSize = 64.dp
        )
    }
}
