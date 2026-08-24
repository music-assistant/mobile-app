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
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Lyrics
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import io.music_assistant.client.data.model.client.PlayerData
import io.music_assistant.client.data.model.client.PlayerDataFixtures
import io.music_assistant.client.data.model.client.ResolvedChapter
import io.music_assistant.client.data.model.client.items.AppMediaItem
import io.music_assistant.client.data.model.client.items.Audiobook
import io.music_assistant.client.data.model.client.items.PodcastEpisode
import io.music_assistant.client.data.model.client.items.QualityTier
import io.music_assistant.client.data.model.client.items.Track
import io.music_assistant.client.data.model.client.items.canBeFavorited
import io.music_assistant.client.data.model.client.items.qualityTier
import io.music_assistant.client.data.model.client.presentationChapter
import io.music_assistant.client.data.model.client.toAbsoluteSeekSeconds
import io.music_assistant.client.imageloader.rememberArtworkRequest
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
import musicassistantclient.composeapp.generated.resources.cd_lyrics
import musicassistantclient.composeapp.generated.resources.cd_playing
import musicassistantclient.composeapp.generated.resources.player_power_on
import musicassistantclient.composeapp.generated.resources.player_powered_off
import musicassistantclient.composeapp.generated.resources.players_nothing
import musicassistantclient.composeapp.generated.resources.queue_cannot_play
import org.jetbrains.compose.resources.stringResource
import kotlin.time.DurationUnit

private const val SEEK_STICK_EPSILON_SECONDS = 0.5f

// Buffered-ahead band uses the slider's own control tint, one alpha, no health tint. Sits just
// above the inactive track (0.4) so the buffered region reads a touch more opaque than "not yet
// buffered" without introducing a second color.
private const val BUFFER_TRACK_ALPHA = 0.45f

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
                        model = rememberArtworkRequest(currentMedia.imageUrl),
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
            val poweredOff = item.player.isPoweredOff
            val (trackName, trackContentDescription) = if (poweredOff) {
                stringResource(Res.string.player_powered_off)
                    .let { it to it }
            } else {
                trackNameAndContentDescription(currentMedia?.title)
            }
            // Leading inset == fade width: at rest the left gradient covers only this empty pad
            // (first glyph crisp); the marquee scrolls the [pad][text] unit so text dissolves
            // toward the artwork when it overflows.
            val marqueeFade = 16.dp
            Column(
                modifier = Modifier
                    .clearAndSetSemantics {
                        contentDescription = trackContentDescription
                    },
            ) {
            Text(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fadingEdges(marqueeFade)
                        .basicMarquee()
                        .padding(start = marqueeFade)
                        .alphaOn(poweredOff || currentMedia?.title != null),
                    text = trackName,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                // Powered off: no subtitle line.
                if (!poweredOff) {
                    currentMedia?.subtitle?.let {
                        Text(
                            modifier = Modifier
                                .fillMaxWidth()
                                .fadingEdges(marqueeFade)
                                .basicMarquee()
                                .padding(start = marqueeFade),
                            text = it,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    } ?: run {
                        if (item.queueInfo?.currentItem?.isPlayable == showAdditionalControls) {
                            Text(
                                modifier = Modifier.padding(horizontal = marqueeFade),
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
        }

        if (item.player.isPoweredOff) {
            IconButton(
                modifier = Modifier.size(48.dp),
                onClick = { playerAction(item, PlayerAction.SetPower(true)) },
            ) {
                Icon(
                    imageVector = Icons.Default.PowerSettingsNew,
                    contentDescription = stringResource(Res.string.player_power_on),
                    tint = colors.controlTint,
                )
            }
        } else {
            PlayerControls(
                playerData = item,
                playerAction = playerAction,
                showAdditionalButtons = showAdditionalControls,
                mainButtonSize = 48.dp,
                showSkip = true,
                showSkipBack = onSelectPlayer != null,
                tint = colors.controlTint,
            )
        }

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

internal fun Track.nowPlayingFolderHierarchy(): String? {
    val mappings = providerMappings.orEmpty()
    val pathCandidates =
        mappings
            .sortedByDescending {
                it.providerDomain == "filesystem_local" ||
                    it.providerInstance.startsWith("filesystem_local--")
            }
            .map { it.itemId } + listOfNotNull(uri)

    return pathCandidates.firstNotNullOfOrNull { candidate ->
        val hasScheme = "://" in candidate
        val path = candidate.substringAfter("://", candidate)
        val parentPath = path.substringBeforeLast("/", "")
        val segments = parentPath.split("/").filter { it.isNotBlank() }.toMutableList()

        if (hasScheme && segments.firstOrNull()?.lowercase() in setOf("track", "tracks")) {
            segments.removeAt(0)
        }

        segments.joinToString(" › ").takeIf { it.isNotBlank() }
    }
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
    bufferedAheadSecFlow: Flow<Double>? = null,
    lyricsAvailable: Boolean = false,
    onLyricsClick: () -> Unit = {},
    // Server preference gate for the chapter-relative timeline.
    chapterProgressEnabled: Boolean = true,
) {
    val currentMedia = item.player.currentMedia

    // Folder hierarchy for the full Now Playing screen.
    //
    // Library tracks have a normalized URI (for example library://track/13328).
    // The original filesystem-relative path is retained in the filesystem provider
    // mapping's itemId.
    //
    // Display:
    // Hungarian › Bon Bon › Album
    val nowPlayingFolderHierarchy =
        (item.queueInfo?.currentItem?.track as? Track)?.nowPlayingFolderHierarchy()

    val onPrimaryContainer = MaterialTheme.colorScheme.onPrimaryContainer
    val controlTint = colors.controlTint

    // Do not add padding here - title/subtitle should be full width.
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Box(
            modifier = Modifier.padding(horizontal = FULL_PLAYER_HORIZONTAL_PADDING)
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
                    model = rememberArtworkRequest(it),
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

        // Track info
        val poweredOff = item.player.isPoweredOff
        val (trackName, trackContentDescription) = if (poweredOff) {
            stringResource(Res.string.player_powered_off).let { it to it }
        } else {
            trackNameAndContentDescription(currentMedia?.title)
        }

        // Powered off: present the "no media" state — disabled slider, empty time labels.
        val duration = if (poweredOff) null else currentMedia?.duration?.takeIf { it > 0 }?.toFloat()

        // Live position from PlayerPositionTracker — single source of truth shared
        // with notification + Android Auto. Recomposition scope is limited to this
        // slider; the marquee/art/controls only recompose on real state changes.
        val displayPosition = livePositionFlow
            ?.collectAsStateWithLifecycle(initialValue = item.queueInfo?.elapsedTime ?: 0.0)
            ?.value?.toFloat()
            ?: item.queueInfo?.elapsedTime?.toFloat()
            ?: 0f

        // Chapter presentation maps the slider/subtitle to the active chapter.
        // Domain positions, seeks, and tracker values remain absolute.
        val currentChapter = if (chapterProgressEnabled && !poweredOff) {
            item.presentationChapter(displayPosition.toDouble())
        } else {
            null
        }
        val timelineDuration = currentChapter?.duration?.toFloat() ?: duration
        val timelinePosition =
            currentChapter?.relativeSec(displayPosition.toDouble())?.toFloat() ?: displayPosition

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clearAndSetSemantics { contentDescription = trackContentDescription },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (!poweredOff) {
                nowPlayingFolderHierarchy?.let { folderHierarchy ->
                    Text(
                        text = folderHierarchy,
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(
                                    horizontal =
                                        FULL_PLAYER_HORIZONTAL_PADDING,
                                ),
                        style =
                            MaterialTheme.typography.bodySmall,
                        color =
                            MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )

                    Spacer(
                        modifier = Modifier.height(4.dp),
                    )
                }
            }

            Text(
                modifier = Modifier
                    .fillMaxWidth()
                    .fadingEdges()
                    .basicMarquee()
                    .padding(horizontal = FULL_PLAYER_HORIZONTAL_PADDING)
                    .alphaOn(poweredOff || currentMedia?.title != null),
                text = trackName,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            when {
                // Powered off: no subtitle line.
                poweredOff -> Unit
                item.queueInfo?.currentItem?.isPlayable == false -> {
                    Text(
                        text = stringResource(Res.string.queue_cannot_play),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.inactive(),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                else -> {
                    // Always render the subtitle line so every player item keeps the same height,
                    // even when blank. But attach `basicMarquee()` ONLY when there's real text:
                    // marquee on a blank string builds a degenerate layer tree that overflows the
                    // RenderThread's native stack (SIGSEGV in HWUI prepareTree) — no-subtitle radios
                    // hit this. The empty Text still reserves one line; it just doesn't scroll.
                    // Chapter mode uses the active chapter name as the subtitle.
                    val subtitle = currentChapter?.displayName ?: currentMedia?.subtitle
                    Text(
                        modifier = Modifier.fillMaxWidth()
                            .then(
                                if (subtitle.isNullOrBlank()) {
                                    Modifier
                                } else {
                                    Modifier
                                        .fadingEdges()
                                        .basicMarquee()
                                        .padding(horizontal = FULL_PLAYER_HORIZONTAL_PADDING)
                                },
                            )
                            .alphaOn(currentMedia?.title != null),
                        text = subtitle.orEmpty(), // TODO take from currentItem?
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }

        // Buffered-ahead seconds (local player only; null → 0 for remote/preview).
        val bufferedAheadSec = bufferedAheadSecFlow
            ?.collectAsStateWithLifecycle(initialValue = 0.0)?.value?.toFloat() ?: 0f

        // Hold the released absolute seek until the tracker publishes its frozen anchor.
        // Drag values are timeline-relative; convert the latch for display so it reconciles
        // even when the seek lands in another chapter.
        var userDragPosition by remember { mutableStateOf<Float?>(null) }
        var releasedSeekPosition by remember { mutableStateOf<Float?>(null) }
        // Latch the chapter at drag start so boundary crossings cannot shift the thumb.
        // It outlives the gesture: the released latch is an absolute position in THIS
        // chapter, and [currentChapter] may already have moved on to the next one.
        var dragChapter by remember { mutableStateOf<ResolvedChapter?>(null) }

        // A queue-item change mid-drag (track end, Next from another controller)
        // invalidates the gesture: a release would seek the NEW item to the OLD
        // item's coordinates. Cancel the drag and drop the released-seek latch
        // (mirrors the web frontend clearing its pending seek on item change).
        LaunchedEffect(item.queueInfo?.currentItem?.id) {
            userDragPosition = null
            releasedSeekPosition = null
            dragChapter = null
        }

        LaunchedEffect(displayPosition, releasedSeekPosition) {
            val released = releasedSeekPosition ?: return@LaunchedEffect
            if (kotlin.math.abs(displayPosition - released) < SEEK_STICK_EPSILON_SECONDS) {
                releasedSeekPosition = null
                dragChapter = null
            }
        }

        val sliderPosition = when {
            poweredOff -> 0f
            else ->
                userDragPosition
                ?: releasedSeekPosition?.let { released ->
                    dragChapter?.relativeSec(released.toDouble())?.toFloat() ?: released
                }
                ?: timelinePosition
        }

        val progressSliderColors = SliderDefaults.colors().copy(
            thumbColor = controlTint,
            activeTrackColor = controlTint,
            inactiveTrackColor = controlTint.inactive(),
        )
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = FULL_PLAYER_HORIZONTAL_PADDING),
        ) {
            Slider(
                value = sliderPosition,
                valueRange = timelineDuration?.let { 0f..it } ?: 0f..1f,
                enabled = displayPosition.takeIf { timelineDuration != null } != null,
                onValueChange = {
                    if (userDragPosition == null) dragChapter = currentChapter
                    userDragPosition = it  // Track drag position locally
                },
                onValueChangeFinished = {
                    userDragPosition?.let { seekPos ->
                        // Convert the latched chapter-relative value to an absolute,
                        // whole-second target to match server/tracker state.
                        val seekSeconds = dragChapter.toAbsoluteSeekSeconds(seekPos.toDouble())
                        releasedSeekPosition = seekSeconds.toFloat()
                        playerAction(item, PlayerAction.SeekTo(seekSeconds))
                        userDragPosition = null  // Clear drag state
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                thumb = {
                    sliderPosition.takeIf { timelineDuration != null }?.let {
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
                            enabled = currentMedia != null && !item.player.isAnnouncing && !poweredOff,
                            modifier = Modifier.height(8.dp),
                        )
                        // Buffered-ahead highlight — the region between the real playhead and
                        // the buffered point, at an alpha between the inactive (0.4) and active
                        // (1.0) track. Local player with audio buffered ahead; shown while paused
                        // too (pause keeps the buffer — only a genuine stop zeroes bufferedAhead).
                        if (item.isLocal &&
                            timelineDuration != null && timelineDuration > 0f && bufferedAheadSec > 0f
                        ) {
                            val playheadFraction =
                                (timelinePosition / timelineDuration).coerceIn(0f, 1f)
                            val bufferedFraction =
                                ((timelinePosition + bufferedAheadSec) / timelineDuration)
                                    .coerceIn(0f, 1f)
                            // Same hue as the slider itself, just a touch more opaque than the
                            // inactive track — a subtle "already buffered" band, no health tint.
                            val bufferedColor = controlTint.copy(alpha = BUFFER_TRACK_ALPHA)
                            Canvas(modifier = Modifier.fillMaxWidth().height(8.dp)) {
                                val startX = playheadFraction * size.width
                                val endX = bufferedFraction * size.width
                                if (endX > startX) {
                                    drawRect(
                                        color = bufferedColor,
                                        topLeft = Offset(startX, 0f),
                                        size = Size(endX - startX, size.height),
                                    )
                                }
                            }
                        }
                        // Chapter ticks use absolute starts, so show them only on the full-book timeline.
                        if (currentChapter == null &&
                            !chapters.isNullOrEmpty() && duration != null && duration > 0f
                        ) {
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
            val showSpeed = isSpokenContent && playbackSpeed != null && !poweredOff

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
                            .takeIf { timelineDuration != null } ?: "",
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
                                text = "${formatDecimal(snapSpeed(playbackSpeed), 2)}x",
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
                                .alpha(if (tier != null && !poweredOff) 1f else 0f)
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
                        text = currentMedia.takeUnless { poweredOff }
                            ?.let { timelineDuration?.formatDuration(DurationUnit.SECONDS) ?: "\u221E" }
                            ?: "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
            )
        }

        // Powered off: favorite + transport controls give way to a single power-on button,
        // sized to match the play/pause control.
        if (poweredOff) {
            IconButton(
                modifier = Modifier.size(60.dp),
                onClick = { playerAction(item, PlayerAction.SetPower(true)) },
            ) {
                Icon(
                    modifier = Modifier.size(48.dp),
                    imageVector = Icons.Default.PowerSettingsNew,
                    contentDescription = stringResource(Res.string.player_power_on),
                    tint = controlTint,
                )
            }
            return@Column
        }

        // Favorite flag lives on the queue's current Track, not on the lightweight
        // `currentMedia`, so the heart reads from there.
        val currentTrack = item.queueInfo?.currentItem?.track as? AppMediaItem
        val favoriteSlot = 48.dp // Material IconButton size; mirrored by the trailing spacer.
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = FULL_PLAYER_HORIZONTAL_PADDING),
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
            // Mirrors the heart slot: lyrics button when available, else a spacer
            // so the transport controls stay centered.
            if (lyricsAvailable) {
                IconButton(
                    modifier = Modifier.size(favoriteSlot),
                    onClick = onLyricsClick,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Lyrics,
                        contentDescription = stringResource(Res.string.cd_lyrics),
                        tint = colors.controlTint,
                    )
                }
            } else {
                Spacer(Modifier.size(favoriteSlot))
            }
        }
    }
}

private val FULL_PLAYER_HORIZONTAL_PADDING = 16.dp

private val previewPoweredOffColors = PlayerColors(dominant = Color.DarkGray, controlTint = Color.White)

@Preview
@Composable
private fun CompactPlayerItemPoweredOffPreview() {
    MaterialTheme {
        CompactPlayerItem(
            modifier = Modifier,
            item = PlayerDataFixtures.playerData(canPower = true, isPowered = false),
            colors = previewPoweredOffColors,
            onSelectPlayer = {},
            sendSpinState = null,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Preview
@Composable
private fun FullPlayerItemPoweredOffPreview() {
    MaterialTheme {
        FullPlayerItem(
            modifier = Modifier,
            item = PlayerDataFixtures.playerData(canPower = true, isPowered = false),
            colors = previewPoweredOffColors,
            playerAction = { _, _ -> },
            onFavoriteClick = {},
            livePositionFlow = null,
        )
    }
}
