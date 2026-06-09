package io.music_assistant.client.ui.compose.home.players

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import io.music_assistant.client.data.model.client.PlayerData
import io.music_assistant.client.data.model.client.items.AppMediaItem
import io.music_assistant.client.data.model.client.items.Audiobook
import io.music_assistant.client.data.model.client.items.PodcastEpisode
import io.music_assistant.client.data.model.client.items.QualityTier
import io.music_assistant.client.data.model.client.items.canBeFavorited
import io.music_assistant.client.data.model.client.items.qualityTier
import io.music_assistant.client.player.sendspin.SendspinState
import io.music_assistant.client.ui.alphaOn
import io.music_assistant.client.ui.compose.common.CenteredThreeSlotRow
import io.music_assistant.client.ui.compose.common.PlayerColors
import io.music_assistant.client.ui.compose.common.action.PlayerAction
import io.music_assistant.client.ui.compose.common.icons.AlbumIcon
import io.music_assistant.client.ui.compose.common.icons.TrackIcon
import io.music_assistant.client.ui.compose.common.painters.rememberPlaceholderPainter
import io.music_assistant.client.ui.fadingEdges
import io.music_assistant.client.ui.inactive
import io.music_assistant.client.ui.theme.favoriteTint
import io.music_assistant.client.utils.formatDuration
import kotlinx.coroutines.flow.Flow
import musicassistantclient.composeapp.generated.resources.Res
import musicassistantclient.composeapp.generated.resources.cd_favorite
import musicassistantclient.composeapp.generated.resources.cd_playing
import musicassistantclient.composeapp.generated.resources.players_nothing
import musicassistantclient.composeapp.generated.resources.queue_cannot_play
import org.jetbrains.compose.resources.stringResource
import kotlin.time.DurationUnit

@Composable
fun CompactPlayerItem(
    modifier: Modifier,
    item: PlayerData,
    colors: PlayerColors,
    playerAction: (PlayerData, PlayerAction) -> Unit = { _, _ -> },
    onSelectPlayer: (() -> Unit)? = null,
    onGroupButton: (() -> Unit)? = null,
    showAdditionalControls: Boolean = false,
    sendSpinState: SendspinState?,
) {
    val currentMedia = item.player.currentMedia
    val onPrimaryContainer = MaterialTheme.colorScheme.onPrimaryContainer

    Row(
        modifier = modifier
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
                    .background(colors.dominant.alphaOn(currentMedia != null)),
                contentAlignment = Alignment.Center,
            ) {
                if (currentMedia != null) {
                    val placeholder = rememberPlaceholderPainter(
                        backgroundColor = colors.dominant,
                        iconColor = onPrimaryContainer,
                        icon = TrackIcon,
                    )
                    AsyncImage(
                        placeholder = placeholder,
                        fallback = placeholder,
                        model = currentMedia.imageUrl,
                        contentDescription = currentMedia.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Icon(
                        imageVector = AlbumIcon,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = onPrimaryContainer.inactive(),
                    )
                }
            }

            // Track info
            val (trackName, trackContentDescription) = trackNameAndContentDescription(currentMedia?.title)
            Column(
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .clearAndSetSemantics {
                        contentDescription = trackContentDescription
                    },
            ) {
                Text(
                    modifier = Modifier.basicMarquee().alphaOn(currentMedia?.title != null),
                    text = trackName,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                currentMedia?.subtitle?.let {
                    Text(
                        modifier = Modifier.basicMarquee(),
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                } ?: run {
                    if (item.queueInfo?.currentItem?.isPlayable == showAdditionalControls) {
                        Text(
                            text = stringResource(Res.string.queue_cannot_play),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.inactive(),
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
            showSkipBack = onSelectPlayer != null,
            tint = colors.controlTint,
        )

        if (onSelectPlayer != null) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.End,
            ) {
                PlayerSelectionButton(
                    player = item,
                    controlTint = colors.controlTint,
                    sendSpinState = sendSpinState,
                    onSelectPlayer = onSelectPlayer,
                    onGroupButton = onGroupButton ?: {},
                )
            }
        }
    }
}

@Composable
private fun trackNameAndContentDescription(title: String?): Pair<String, String> {
    val playingContentDescription = if (title != null) {
        stringResource(Res.string.cd_playing, title)
    } else {
        stringResource(Res.string.players_nothing)
    }

    return Pair(title ?: stringResource(Res.string.players_nothing), playingContentDescription)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FullPlayerItem(
    modifier: Modifier,
    item: PlayerData,
    colors: PlayerColors,
    playerAction: (PlayerData, PlayerAction) -> Unit,
    onFavoriteClick: (AppMediaItem) -> Unit,
    livePositionFlow: Flow<Double>?,
) {
    val currentMedia = item.player.currentMedia
    val onPrimaryContainer = MaterialTheme.colorScheme.onPrimaryContainer
    val controlTint = colors.controlTint

    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Box(
            modifier = Modifier
                .weight(1f, fill = false)
                .aspectRatio(1f)
                .heightIn(max = 500.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(colors.dominant.alphaOn(currentMedia != null)),
            contentAlignment = Alignment.Center,
        ) {
            currentMedia?.imageUrl?.let {
                val placeholder =
                    rememberPlaceholderPainter(
                        backgroundColor = colors.dominant,
                        iconColor = onPrimaryContainer,
                        icon = currentMedia.defaultIcon,
                    )
                AsyncImage(
                    placeholder = placeholder,
                    fallback = placeholder,
                    model = it,
                    contentDescription = currentMedia.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } ?: Icon(
                imageVector = currentMedia?.defaultIcon ?: AlbumIcon,
                contentDescription = null,
                modifier = Modifier.size(120.dp),
                tint = onPrimaryContainer,
            )
        }

        // Track info — full width now that the favorite moved to the controls row.
        val (trackName, trackContentDescription) = trackNameAndContentDescription(currentMedia?.title)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clearAndSetSemantics { contentDescription = trackContentDescription },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                modifier = Modifier.fillMaxWidth().fadingEdges().basicMarquee()
                    .alphaOn(currentMedia?.title != null),
                text = trackName,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (item.queueInfo?.currentItem?.isPlayable == false) {
                Text(
                    text = stringResource(Res.string.queue_cannot_play),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.inactive(),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            } else {
                Text(
                    modifier = Modifier.fillMaxWidth().fadingEdges().basicMarquee()
                        .alphaOn(currentMedia?.title != null),
                    text = currentMedia?.subtitle ?: "", // TODO take from currentItem?
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        val duration = currentMedia?.duration?.takeIf { it > 0 }?.toFloat()

        // Live position from PlayerPositionTracker — single source of truth shared
        // with notification + Android Auto. Recomposition scope is limited to this
        // slider; the marquee/art/controls only recompose on real state changes.
        val displayPosition = livePositionFlow
            ?.collectAsStateWithLifecycle(initialValue = item.queueInfo?.elapsedTime ?: 0.0)
            ?.value?.toFloat()
            ?: item.queueInfo?.elapsedTime?.toFloat()
            ?: 0f

        // Track user drag state separately
        var userDragPosition by remember { mutableStateOf<Float?>(null) }

        // Use user drag position if dragging, otherwise use calculated position
        val sliderPosition = userDragPosition ?: displayPosition

        val progressSliderColors = SliderDefaults.colors().copy(
            thumbColor = controlTint,
            activeTrackColor = controlTint,
            inactiveTrackColor = controlTint.inactive(),
        )
        Column(
            modifier = Modifier.fillMaxWidth(),
        ) {
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
                    val audiobook = item.queueInfo?.currentItem?.track as? Audiobook
                    val chapters = audiobook?.chapters
                    Box {
                        SliderDefaults.Track(
                            sliderState = sliderState,
                            colors = progressSliderColors,
                            thumbTrackGapSize = 0.dp,
                            trackInsideCornerSize = 0.dp,
                            drawStopIndicator = null,
                            enabled = currentMedia != null && !item.player.isAnnouncing,
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
            val currentQueueItem = item.queueInfo?.currentItem
            val tier = currentQueueItem?.qualityTier
            val isLq = tier == QualityTier.LQ
            var showChainDialog by remember(currentQueueItem?.id) { mutableStateOf(false) }
            var showSpeedDialog by remember(currentQueueItem?.id) { mutableStateOf(false) }

            // Variable speed: server-supported only for audiobooks/podcasts, and only
            // when the queue payload carries `playback_speed` (feature-detect gate).
            val playbackSpeed = item.queueInfo?.playbackSpeed
            val isSpokenContent = currentQueueItem?.track is Audiobook ||
                currentQueueItem?.track is PodcastEpisode
            val showSpeed = isSpokenContent && playbackSpeed != null

            if (showChainDialog && currentQueueItem != null) {
                AudioChainDialog(
                    queueTrack = currentQueueItem,
                    player = item,
                    onDismissRequest = { showChainDialog = false },
                )
            }

            if (showSpeedDialog && playbackSpeed != null) {
                PlaybackSpeedDialog(
                    currentSpeed = playbackSpeed,
                    onConfirm = { playerAction(item, PlayerAction.SetPlaybackSpeed(it)) },
                    onDismissRequest = { showSpeedDialog = false },
                )
            }

            CenteredThreeSlotRow(
                modifier = Modifier.fillMaxWidth(),
                start = {
                    Text(
                        text = sliderPosition.takeIf { currentMedia != null }
                            .formatDuration(DurationUnit.SECONDS)
                            .takeIf { duration != null } ?: "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
                center = {
                    if (showSpeed) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(colors.controlTint)
                                .clickable { showSpeedDialog = true }
                                .padding(horizontal = 8.dp, vertical = 2.dp),
                        ) {
                            Text(
                                text = "${formatSpeed(playbackSpeed)}x",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = if (colors.controlTint.luminance() > 0.5f) {
                                    Color.Black
                                } else {
                                    Color.White
                                },
                            )
                        }
                    } else {
                        Box(
                            modifier = Modifier
                                .alpha(if (tier != null) 1f else 0f)
                                .clip(RoundedCornerShape(6.dp))
                                .background(
                                    if (isLq) {
                                        MaterialTheme.colorScheme.surfaceVariant
                                    } else {
                                        colors.controlTint
                                    },
                                )
                                .clickable(enabled = tier != null) { showChainDialog = true }
                                .padding(horizontal = 8.dp, vertical = 2.dp),
                        ) {
                            Text(
                                text = (tier ?: QualityTier.LQ).name,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = if (isLq) {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                } else {
                                    if (colors.controlTint.luminance() > 0.5f) {
                                        Color.Black
                                    } else {
                                        Color.White
                                    }
                                },
                            )
                        }
                    }
                },
                end = {
                    Text(
                        text = currentMedia
                            ?.let { duration?.formatDuration(DurationUnit.SECONDS) ?: "\u221E" }
                            ?: "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
            )
        }

        // Favorite flag lives on the queue's current Track, not on the lightweight
        // `currentMedia`, so the heart reads from there.
        val currentTrack = item.queueInfo?.currentItem?.track as? AppMediaItem
        val favoriteSlot = 48.dp // Material IconButton size; mirrored by the trailing spacer.
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (currentTrack?.canBeFavorited == true) {
                val isFavorite = currentTrack.favorite == true
                IconButton(
                    modifier = Modifier.size(favoriteSlot),
                    onClick = { onFavoriteClick(currentTrack) },
                ) {
                    Icon(
                        imageVector = if (isFavorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                        contentDescription = stringResource(Res.string.cd_favorite),
                        tint = if (isFavorite) favoriteTint else colors.controlTint,
                    )
                }
            } else {
                Spacer(Modifier.size(favoriteSlot)) // keep controls centered when heart is hidden
            }
            PlayerControls(
                playerData = item,
                playerAction = playerAction,
                mainButtonSize = 60.dp,
                tint = controlTint,
            )
            Spacer(Modifier.size(favoriteSlot)) // mirrors the heart, keeps controls centered
        }
    }
}
