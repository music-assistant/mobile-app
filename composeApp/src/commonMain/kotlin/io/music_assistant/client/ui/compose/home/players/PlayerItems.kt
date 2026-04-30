package io.music_assistant.client.ui.compose.home.players

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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import io.music_assistant.client.data.model.client.AppMediaItem
import io.music_assistant.client.data.model.client.AppMediaItem.Companion.description
import io.music_assistant.client.data.model.client.PlayerData
import io.music_assistant.client.player.sendspin.SendspinState
import io.music_assistant.client.ui.compose.common.PlayerColors
import io.music_assistant.client.ui.compose.common.action.PlayerAction
import io.music_assistant.client.ui.compose.common.icons.AlbumIcon
import io.music_assistant.client.ui.compose.common.icons.TrackIcon
import io.music_assistant.client.ui.compose.common.painters.rememberPlaceholderPainter
import io.music_assistant.client.utils.formatDuration
import musicassistantclient.composeapp.generated.resources.Res
import musicassistantclient.composeapp.generated.resources.cd_playing
import org.jetbrains.compose.resources.stringResource
import kotlin.time.DurationUnit

@Composable
fun CompactPlayerItem(
    item: PlayerData,
    colors: PlayerColors,
    serverUrl: String? = null,
    playerAction: (PlayerData, PlayerAction) -> Unit = { _, _ -> },
    onSelectPlayer: (() -> Unit)? = null,
    onGroupButton: (() -> Unit)? = null,
    showAdditionalControls: Boolean = false,
    sendSpinState: SendspinState?,
) {
    val track = item.queueInfo?.currentItem?.track
    val onPrimaryContainer = MaterialTheme.colorScheme.onPrimaryContainer

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start,
        ) {
            // Album cover on the far left
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(colors.dominant.copy(alpha = track?.let { 1f } ?: DISABLED_ALPHA)),
                contentAlignment = Alignment.Center,
            ) {
                if (track != null) {
                    val placeholder = rememberPlaceholderPainter(
                        backgroundColor = colors.dominant,
                        iconColor = onPrimaryContainer,
                        icon = TrackIcon,
                    )
                    AsyncImage(
                        placeholder = placeholder,
                        fallback = placeholder,
                        model = track.imageInfo?.url(serverUrl),
                        contentDescription = track.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Icon(
                        imageVector = AlbumIcon,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = onPrimaryContainer.copy(alpha = DISABLED_ALPHA),
                    )
                }
            }

            // Track info
            val trackName = track?.title ?: "nothing playing"
            val playingContentDescription = stringResource(Res.string.cd_playing, trackName)
            Column(
                modifier = Modifier
                .padding(horizontal = 16.dp)
                .clearAndSetSemantics {
                    contentDescription = playingContentDescription
                },
            ) {
                Text(
                    modifier = Modifier.basicMarquee().alpha(if (track != null) 1f else DISABLED_ALPHA),
                    text = trackName,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                track?.subtitle?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                } ?: run {
                    if (item.queueInfo?.currentItem?.isPlayable == showAdditionalControls) {
                        Text(
                            text = "Cannot play this item",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = DISABLED_ALPHA),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }

        PlayerControls(
            playerData = item,
            playerAction = playerAction,
            showAdditionalButtons = showAdditionalControls,
            mainButtonSize = 48.dp,
            showSkip = true,
            tint = colors.controlTint,
        )

        if (onSelectPlayer != null) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.End,
            ) {
                PlayerSelectionLayout(
                    player = item,
                    sendSpinState = sendSpinState,
                    onSelectPlayer = onSelectPlayer,
                    onGroupButton = onGroupButton ?: {},
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FullPlayerItem(
    modifier: Modifier,
    item: PlayerData,
    isLocal: Boolean,
    colors: PlayerColors,
    serverUrl: String?,
    playerAction: (PlayerData, PlayerAction) -> Unit,
    @Suppress("UnusedParameter") onFavoriteClick: (AppMediaItem) -> Unit, // FIXME inconsistent stuff happening
) {
    val track = item.queueInfo?.currentItem?.track
    val onPrimaryContainer = MaterialTheme.colorScheme.onPrimaryContainer
    val controlTint = colors.controlTint

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
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
                .background(colors.dominant.copy(alpha = track?.let { 1f } ?: DISABLED_ALPHA)),
            contentAlignment = Alignment.Center,
        ) {
            if (track != null) {
                val placeholder =
                    rememberPlaceholderPainter(
                        backgroundColor = colors.dominant,
                        iconColor = onPrimaryContainer,
                        icon = track.defaultIcon,
                    )
                track.imageInfo?.url(serverUrl)?.let {
                    AsyncImage(
                        placeholder = placeholder,
                        fallback = placeholder,
                        model = it,
                        contentDescription = track.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } ?: Icon(
                    imageVector = track.defaultIcon,
                    contentDescription = null,
                    modifier = Modifier.size(120.dp),
                    tint = onPrimaryContainer,
                )
            } else {
                Icon(
                    imageVector = AlbumIcon,
                    contentDescription = null,
                    modifier = Modifier.size(120.dp),
                    tint = onPrimaryContainer.copy(alpha = DISABLED_ALPHA),
                )
            }
        }

        // Track info
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                modifier = Modifier.basicMarquee().alpha(if (track != null) 1f else DISABLED_ALPHA),
                text = track?.title ?: "nothing playing",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (item.queueInfo?.currentItem?.isPlayable == false) {
                Text(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    text = "Cannot play this item",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = DISABLED_ALPHA),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            } else {
                Text(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    text = track?.subtitle ?: "",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                modifier = Modifier.padding(horizontal = 16.dp),
                text = item.queueInfo?.currentItem?.audioFormat(item.playerId)?.description ?: "",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = DISABLED_ALPHA),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        val duration = track?.duration?.takeIf { it > 0 }?.toFloat()

        // Position is calculated in MainDataSource and updated twice per second
        val displayPosition = item.queueInfo?.elapsedTime?.toFloat() ?: 0f

        // Track user drag state separately
        var userDragPosition by remember { mutableStateOf<Float?>(null) }

        // Use user drag position if dragging, otherwise use calculated position
        val sliderPosition = userDragPosition ?: displayPosition

        val progressSliderColors = SliderDefaults.colors().copy(
            thumbColor = controlTint,
            activeTrackColor = controlTint,
        )
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        ) { // Progress bar
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
                            colors = progressSliderColors,
                        )
                    }
                },
                track = { sliderState ->
                    val audiobook = track as? AppMediaItem.Audiobook
                    val chapters = audiobook?.chapters
                    Box {
                        SliderDefaults.Track(
                            sliderState = sliderState,
                            colors = progressSliderColors,
                            thumbTrackGapSize = 0.dp,
                            trackInsideCornerSize = 0.dp,
                            drawStopIndicator = null,
                            enabled = track != null && !item.player.isAnnouncing,
                            modifier = Modifier.height(8.dp),
                        )
                        if (!chapters.isNullOrEmpty() && duration != null && duration > 0f) {
                            val tickColor =
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                            Canvas(modifier = Modifier.fillMaxWidth().height(8.dp)) {
                                chapters.drop(1).forEach { chapter ->
                                    val fraction =
                                        (chapter.start.toFloat() / duration).coerceIn(0f, 1f)
                                    val x = fraction * size.width
                                    drawLine(
                                        color = tickColor,
                                        start = Offset(x, 0f),
                                        end = Offset(x, size.height),
                                        strokeWidth = 2.dp.toPx(),
                                    )
                                }
                            }
                        }
                    }
                },
            )

            // Duration labels
            Row(
                modifier = Modifier.fillMaxWidth().offset(y = (-16).dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = sliderPosition.takeIf { track != null }
                        .formatDuration(DurationUnit.SECONDS)
                        .takeIf { duration != null } ?: "",
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = track
                        ?.let { duration?.formatDuration(DurationUnit.SECONDS) ?: "\u221E" }
                        ?: "",
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        PlayerControls(
            playerData = item,
            playerAction = playerAction,
            mainButtonSize = 60.dp,
            tint = controlTint,
        )
    }
}

private const val DISABLED_ALPHA = 0.4f
