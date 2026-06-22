// Compose layout values (sizes, alphas, animation durations) are visual design tokens.
@file:Suppress("MagicNumber")

package io.music_assistant.client.ui.compose.home.players

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowRight
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.filled.AllInclusive
import androidx.compose.material.icons.filled.CellTower
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.input.pointer.util.addPointerInputChange
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.window.core.layout.WindowSizeClass
import io.music_assistant.client.data.model.client.AppMediaItemFixtures
import io.music_assistant.client.data.model.client.PlayerData
import io.music_assistant.client.data.model.client.PlayerDataFixtures
import io.music_assistant.client.data.model.client.PlayerDataFixtures.toQueue
import io.music_assistant.client.data.model.client.PlayerDataFixtures.toQueueTrack
import io.music_assistant.client.data.model.client.items.AppMediaItem
import io.music_assistant.client.data.model.client.items.Track
import io.music_assistant.client.player.sendspin.SendspinState
import io.music_assistant.client.ui.alphaOn
import io.music_assistant.client.ui.compose.common.CenteredThreeSlotRow
import io.music_assistant.client.ui.compose.common.DataState
import io.music_assistant.client.ui.compose.common.OverflowMenu
import io.music_assistant.client.ui.compose.common.OverflowMenuButton
import io.music_assistant.client.ui.compose.common.OverflowMenuOption
import io.music_assistant.client.ui.compose.common.PlayerColors
import io.music_assistant.client.ui.compose.common.action.PlayerAction
import io.music_assistant.client.ui.compose.common.action.QueueAction
import io.music_assistant.client.ui.compose.common.icons.VolumeIcon
import io.music_assistant.client.ui.compose.common.icons.VolumeMutedIcon
import io.music_assistant.client.ui.compose.common.items.AddToPlaylistDialog
import io.music_assistant.client.ui.compose.common.items.PlaylistActions
import io.music_assistant.client.ui.compose.common.items.navigationOptions
import io.music_assistant.client.ui.compose.common.rememberAnimatedPlayerColors
import io.music_assistant.client.ui.compose.common.rememberExtractedColorsSource
import io.music_assistant.client.ui.compose.common.viewmodel.ActionsViewModel
import io.music_assistant.client.ui.compose.home.CollapsibleQueue
import io.music_assistant.client.ui.compose.home.HomeScreenViewModel
import io.music_assistant.client.ui.compose.home.HorizontalPagerIndicator
import io.music_assistant.client.ui.compose.home.Queue
import io.music_assistant.client.ui.inactive
import io.music_assistant.client.utils.WindowClass
import io.music_assistant.client.utils.conditional
import kotlinx.coroutines.flow.Flow
import musicassistantclient.composeapp.generated.resources.Res
import musicassistantclient.composeapp.generated.resources.action_add_to_playlist
import musicassistantclient.composeapp.generated.resources.bound_player_joined_to
import musicassistantclient.composeapp.generated.resources.bound_player_part_of_group
import musicassistantclient.composeapp.generated.resources.bound_player_playing_with
import musicassistantclient.composeapp.generated.resources.bound_player_playing_with_group
import musicassistantclient.composeapp.generated.resources.cd_more
import musicassistantclient.composeapp.generated.resources.cd_mute
import musicassistantclient.composeapp.generated.resources.cd_unmute
import musicassistantclient.composeapp.generated.resources.players_dsp_settings
import musicassistantclient.composeapp.generated.resources.players_loading
import musicassistantclient.composeapp.generated.resources.players_none_available
import musicassistantclient.composeapp.generated.resources.queue_clear
import musicassistantclient.composeapp.generated.resources.queue_dsm_disable
import musicassistantclient.composeapp.generated.resources.queue_dsm_enable
import musicassistantclient.composeapp.generated.resources.queue_no_other_players
import musicassistantclient.composeapp.generated.resources.queue_transfer
import org.jetbrains.compose.resources.stringResource
import kotlin.math.ceil
import kotlin.math.roundToInt

/**
 * Seek target (whole seconds) for a chapter tap. The seek API takes integer
 * seconds, so a fractional [startSec] is rounded up — landing inside the
 * tapped chapter rather than a fraction of a second before it, in the prior one.
 */
internal fun chapterSeekSeconds(startSec: Double): Long = ceil(startSec).toLong()

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun PlayersPager(
    playerPagerState: PagerState,
    state: HomeScreenViewModel.PlayersState,
    homeScreenViewModel: HomeScreenViewModel,
    actionsViewModel: ActionsViewModel,
    dspSettingsViewModel: DspSettingsViewModel,
    expanded: Boolean,
    onClose: () -> Unit,
    contentPadding: PaddingValues,
    navigateToItem: (AppMediaItem) -> Unit,
) {
    if (state is HomeScreenViewModel.PlayersState.Data && state.playerData.isNotEmpty()) {
        val moveToPlayer: (String) -> Unit = { id: String ->
            state.playerData.find { it.player.id == id }
                ?.let { homeScreenViewModel.selectPlayer(it.player) }
        }

        val colorsSource = rememberExtractedColorsSource()

        val playerAction1 =
            { data: PlayerData, action: PlayerAction ->
                homeScreenViewModel.playerAction(
                    data,
                    action,
                )
            }
        var isQueueExpanded by remember { mutableStateOf(false) }
        // Extract playerData list to ensure proper recomposition
        val playerDataList = state.playerData
        // Select-player dialog is hoisted out of the pager so that reordering-induced
        // pager scrolls don't tear down the dialog's composable.
        var selectDialogPlayerId by remember<MutableState<String?>> { mutableStateOf(null) }
        val selectDialogPlayer = selectDialogPlayerId?.let { id ->
            playerDataList.firstOrNull { it.player.id == id }
        }
        if (selectDialogPlayer != null) {
            SelectPlayerDialog(
                selectedPlayer = selectDialogPlayer,
                players = playerDataList,
                onDismissRequest = { selectDialogPlayerId = null },
                onMoveToPlayer = { moveToPlayer(it) },
                onReorder = { homeScreenViewModel.onPlayersSortChanged(it) },
            )
        }
        val playerColors = playerDataList.associateWith {
            val media = it.player.currentMedia
            rememberAnimatedPlayerColors(
                imageUrl = media?.imageUrl,
                fallback = MaterialTheme.colorScheme.primaryContainer,
                source = colorsSource,
            )
        }
        val isExpandedScreen = WindowClass.isAtLeastExpanded()
        val modifier = if (!expanded) {
            Modifier
        } else {
            Modifier.windowInsetsPadding(
                WindowInsets.safeDrawing.only(WindowInsetsSides.Top),
            )
        }
        Column(modifier = modifier) {
            if (playerDataList.size > 1) {
                HorizontalPagerIndicator(
                    modifier = Modifier.padding(top = 4.dp),
                    pagerState = playerPagerState,
                )
            }

            HorizontalPager(
                modifier = Modifier,
                state = playerPagerState,
                key = { page -> playerDataList.getOrNull(page)?.player?.id ?: page },
            ) { page ->
                val player = playerDataList.getOrNull(page) ?: return@HorizontalPager
                var showGroupDialog by remember { mutableStateOf(false) }
                var showDspDialog by remember { mutableStateOf(false) }
                val onSelectPlayer = { selectDialogPlayerId = player.player.id }
                val onGroupButton = { showGroupDialog = true }
                val onDspButton = { showDspDialog = true }
                if (showGroupDialog) {
                    GroupSettingsDialog(
                        player = player,
                        onDismissRequest = { showGroupDialog = false },
                        groupAction = { playerId: String, action: PlayerAction ->
                            homeScreenViewModel.playerAction(
                                playerId,
                                action,
                            )
                        },
                        localPlayerId = homeScreenViewModel.localPlayerId,
                        onAdjustPlaybackDelay = {
                            homeScreenViewModel.adjustSendspinStaticDelayMs(it)
                        },
                    )
                }
                if (showDspDialog) {
                    DspSettingsDialog(
                        playerId = player.player.id,
                        dspSettingsViewModel = dspSettingsViewModel,
                        onDismissRequest = { showDspDialog = false },
                    )
                }

                val colors by playerColors.getValue(player)

                Box(modifier = Modifier.wrapContentHeight()) {
                    Column(
                        Modifier
                            .background(
                                brush = Brush.verticalGradient(
                                    listOf(
                                        MaterialTheme.colorScheme.surfaceContainerHigh,
                                        colors.dominant.inactive(),
                                    ),
                                ),
                            ),
                    ) {
                        // Memoized per queue id so the same Flow instance survives
                        // recomposition — otherwise collectAsStateWithLifecycle would
                        // tear down and restart the position collector on every pass.
                        val queueId = player.queueInfo?.id
                        val livePositionFlow = remember(queueId) {
                            queueId?.let { homeScreenViewModel.observePosition(it) }
                        }
                        if (!expanded) {
                            CollapsedPlayerPage(
                                isExpandedScreen = isExpandedScreen,
                                player = player,
                                colors = colors,
                                sendspinState = state.sendspinState,
                                onSelectPlayer = onSelectPlayer,
                                onGroupButton = onGroupButton,
                                playerAction = playerAction1,
                            )
                        } else {
                            ExpandedPlayerPage(
                                player = player,
                                colors = colors,
                                onSelectPlayer = onSelectPlayer,
                                onGroupButton = onGroupButton,
                                onDspButton = onDspButton.takeIf { !player.player.isGroup },
                                playerAction = playerAction1,
                                playlistActions = actionsViewModel,
                                onFavoriteClick = {
                                    actionsViewModel.onFavoriteClick(it)
                                },
                                onClose = onClose,
                                queueAction = { homeScreenViewModel.queueAction(it) },
                                allPlayers = playerDataList,
                                moveToPlayer = moveToPlayer,
                                isExpandedScreen = isExpandedScreen,
                                sendspinState = state.sendspinState,
                                isQueueExpanded = isQueueExpanded,
                                onExpandQueue = { isQueueExpanded = it },
                                contentPadding = contentPadding,
                                isCurrentPage = page == playerPagerState.currentPage,
                                navigateToItem = navigateToItem,
                                livePositionFlow = livePositionFlow,
                            )
                        }
                    }
                    player.parentBind?.let {
                        BoundPlayerInfo(
                            modifier = Modifier.matchParentSize(),
                            playerName = player.player.name,
                            parent = it,
                            moveToPlayer = moveToPlayer,
                        )
                    }
                }
            }
        }
    } else {
        Box(Modifier.fillMaxWidth().height(84.dp)) {
            val text = stringResource(
                when (state) {
                    is HomeScreenViewModel.PlayersState.Loading -> Res.string.players_loading
                    else -> Res.string.players_none_available
                },
            )

            Text(
                modifier = Modifier.align(Alignment.Center),
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun BoundPlayerInfo(
    modifier: Modifier,
    playerName: String,
    parent: PlayerData.ParentBind,
    moveToPlayer: (String) -> Unit,
) {
    val template = when {
        parent.isPlaying && parent.isGroup -> Res.string.bound_player_playing_with_group
        parent.isPlaying -> Res.string.bound_player_playing_with
        parent.isGroup -> Res.string.bound_player_part_of_group
        else -> Res.string.bound_player_joined_to
    }
    Box(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.9f))
            .clickable { moveToPlayer(parent.id) },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = stringResource(template, playerName, parent.name),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun ExpandedPlayerPage(
    player: PlayerData,
    colors: PlayerColors,
    onSelectPlayer: () -> Unit,
    onGroupButton: () -> Unit,
    onDspButton: (() -> Unit)?,
    playerAction: (PlayerData, PlayerAction) -> Unit,
    playlistActions: PlaylistActions? = null,
    onFavoriteClick: (AppMediaItem) -> Unit,
    onClose: () -> Unit,
    queueAction: (QueueAction) -> Unit,
    allPlayers: List<PlayerData>,
    moveToPlayer: (String) -> Unit,
    isExpandedScreen: Boolean,
    sendspinState: SendspinState?,
    isQueueExpanded: Boolean,
    onExpandQueue: (Boolean) -> Unit,
    contentPadding: PaddingValues,
    isCurrentPage: Boolean,
    navigateToItem: (AppMediaItem) -> Unit = {},
    livePositionFlow: Flow<Double>?,
) {
    val isLargeScreen = WindowClass.isAtLeastLarge()
    val dismissThresholdPx = with(LocalDensity.current) { 120.dp.toPx() }
    // Minimum gesture speed (px/s) to count as a fling rather than a slow drag.
    val minFlingVelocityPx = with(LocalDensity.current) { 1000.dp.toPx() }
    val queueCollapseNestedScroll = remember(onExpandQueue, minFlingVelocityPx) {
        object : NestedScrollConnection {
            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                // available.y is the leftover downward fling velocity once the queue list
                // can no longer scroll (i.e. it's at the top) — a genuine downward fling.
                if (available.y > minFlingVelocityPx) onExpandQueue(false)
                return Velocity.Zero
            }
        }
    }
    Column(
        modifier = Modifier.padding(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        CenteredThreeSlotRow(
            modifier = Modifier.fillMaxWidth(),
            start = {
                IconButton(onClick = onClose) {
                    Icon(
                        Icons.Default.ExpandMore,
                        "Collapse",
                        modifier = Modifier.size(32.dp),
                    )
                }
            },
            center = {
                PlayerSelectionButton(
                    player = player,
                    controlTint = colors.controlTint,
                    sendSpinState = sendspinState,
                    onSelectPlayer = onSelectPlayer,
                    onGroupButton = onGroupButton,
                )
            },
            end = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (player.queueInfo?.isRadioOn == true) {
                        Icon(
                            imageVector = Icons.Default.CellTower,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = colors.controlTint,
                        )
                    }
                    PlayerOverflowMenu(
                        currentPlayer = player,
                        allPlayers = allPlayers,
                        playerAction = { playerAction(player, it) },
                        queueAction = queueAction,
                        navigateToItem = {
                            navigateToItem(it)
                            onClose()
                        },
                        onPlayerSelected = { moveToPlayer(it) },
                        onOpenDsp = onDspButton,
                        playlistActions = playlistActions,
                    )
                }
            },
        )

        AnimatedVisibility(
            visible = isQueueExpanded,
            enter = fadeIn(tween(300)) + expandVertically(tween(300)),
            exit = fadeOut(tween(200)) + shrinkVertically(tween(300)),
        ) {
            Box(
                modifier = Modifier
                    .padding(top = 2.dp)
                    .fillMaxWidth()
                    .wrapContentSize()
                    .clickable { onExpandQueue(false) },
            ) {
                CompactPlayerItem(
                    modifier = Modifier,
                    item = player,
                    colors = colors,
                    playerAction = playerAction,
                    onSelectPlayer = if (isExpandedScreen && !isQueueExpanded) onSelectPlayer else null,
                    onGroupButton = if (isExpandedScreen && !isQueueExpanded) onGroupButton else null,
                    showAdditionalControls = isExpandedScreen,
                    sendSpinState = sendspinState,
                )
            }
        }

        Row {
            Column(
                modifier = Modifier
                    .padding(top = 8.dp)
                    .conditional(isLargeScreen) {
                        widthIn(max = WindowSizeClass.WIDTH_DP_EXPANDED_LOWER_BOUND.dp)
                    },
            ) {
                Column(
                    modifier = Modifier
                        .conditional(
                            condition = !isQueueExpanded,
                            ifTrue = { weight(1f) },
                            ifFalse = { wrapContentHeight() },
                        ),
                ) {
                    AnimatedVisibility(
                        visible = !isQueueExpanded,
                        enter = fadeIn(tween(300)) + expandVertically(tween(300)),
                        exit = fadeOut(tween(200)) + shrinkVertically(tween(300)),
                    ) {
                        FullPlayerItem(
                            modifier = Modifier
                                .fillMaxSize()
                                .pointerInput(
                                    onClose,
                                    onExpandQueue,
                                    isLargeScreen,
                                    dismissThresholdPx,
                                    minFlingVelocityPx,
                                ) {
                                    var totalDrag = 0f
                                    val velocityTracker = VelocityTracker()
                                    detectVerticalDragGestures(
                                        onDragStart = {
                                            totalDrag = 0f
                                            velocityTracker.resetTracking()
                                        },
                                        onDragCancel = {
                                            totalDrag = 0f
                                            velocityTracker.resetTracking()
                                        },
                                        onVerticalDrag = { change, dragAmount ->
                                            totalDrag += dragAmount
                                            velocityTracker.addPointerInputChange(change)
                                        },
                                        onDragEnd = {
                                            // Fire only on a true fling: far enough AND fast enough,
                                            // so a slow crawl past the threshold no longer triggers.
                                            val velocity = velocityTracker.calculateVelocity().y
                                            if (totalDrag > dismissThresholdPx &&
                                                velocity > minFlingVelocityPx
                                            ) {
                                                onClose()
                                            } else if (!isLargeScreen &&
                                                totalDrag < -dismissThresholdPx &&
                                                velocity < -minFlingVelocityPx
                                            ) {
                                                onExpandQueue(true)
                                            }
                                            totalDrag = 0f
                                        },
                                    )
                                },
                            item = player,
                            colors = colors,
                            playerAction = playerAction,
                            onFavoriteClick = onFavoriteClick,
                            livePositionFlow = livePositionFlow,
                        )
                    }
                }

                // Fixed-height shell keeps album art space consistent whether
                // the volume control is shown for this player.
                Box(modifier = Modifier.fillMaxWidth().height(36.dp)) {
                    if (player.player.isVolumeSliderAccessible && player.player.currentVolume != null) {
                        var currentVolume by remember(player.player.currentVolume) {
                            mutableStateOf(player.player.currentVolume)
                        }
                        val controlTint = colors.controlTint
                        val volumeSliderColors = SliderDefaults.colors().copy(
                            thumbColor = controlTint,
                            activeTrackColor = controlTint,
                            inactiveTrackColor = controlTint.inactive(),
                        )
                        val isGroupBound = player.childrenBinds.any { it.isBound }
                        val isGroupForGesture by rememberUpdatedState(isGroupBound)
                        Row(
                            modifier = Modifier.fillMaxSize().padding(horizontal = 32.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Icon(
                                modifier = Modifier
                                    .size(24.dp)
                                    .alphaOn(player.player.canMute)
                                    .clickable(enabled = player.player.canMute) {
                                        playerAction(
                                            player,
                                            if (player.childrenBinds.none { it.isBound }) {
                                                PlayerAction.ToggleMute(player.player.currentMuteState)
                                            } else {
                                                PlayerAction.GroupToggleMute(player.player.currentMuteState)
                                            },
                                        )
                                    },
                                imageVector = if (player.player.currentMuteState) {
                                    VolumeMutedIcon
                                } else {
                                    VolumeIcon
                                },
                                contentDescription = if (player.player.currentMuteState) {
                                    stringResource(
                                        Res.string.cd_unmute,
                                    )
                                } else {
                                    stringResource(Res.string.cd_mute)
                                },
                                tint = controlTint,
                            )
                            VolumeSliderBox(
                                modifier = Modifier.weight(1f),
                                volume = { currentVolume },
                                onStepDown = {
                                    playerAction(
                                        player,
                                        if (isGroupForGesture) {
                                            PlayerAction.GroupVolumeDown
                                        } else {
                                            PlayerAction.VolumeDown
                                        },
                                    )
                                },
                                onStepUp = {
                                    playerAction(
                                        player,
                                        if (isGroupForGesture) {
                                            PlayerAction.GroupVolumeUp
                                        } else {
                                            PlayerAction.VolumeUp
                                        },
                                    )
                                },
                            ) {
                                Slider(
                                    modifier = Modifier.fillMaxWidth(),
                                    value = currentVolume,
                                    valueRange = 0f..100f,
                                    onValueChange = {
                                        currentVolume = it
                                    },
                                    onValueChangeFinished = {
                                        playerAction(
                                            player,
                                            if (player.childrenBinds.none { it.isBound }) {
                                                PlayerAction.VolumeSet(currentVolume.toDouble())
                                            } else {
                                                PlayerAction.GroupVolumeSet(currentVolume.toDouble())
                                            },
                                        )
                                    },
                                    thumb = {
                                        SliderDefaults.Thumb(
                                            interactionSource = remember { MutableInteractionSource() },
                                            thumbSize = DpSize(16.dp, 16.dp),
                                            colors = volumeSliderColors,
                                        )
                                    },
                                    track = { sliderState ->
                                        SliderDefaults.Track(
                                            sliderState = sliderState,
                                            colors = volumeSliderColors,
                                            thumbTrackGapSize = 0.dp,
                                            trackInsideCornerSize = 0.dp,
                                            drawStopIndicator = null,
                                            modifier = Modifier.height(4.dp),
                                        )
                                    },
                                )
                            }
                            VolumeValue(
                                volume = currentVolume.roundToInt(),
                                style = MaterialTheme.typography.bodyMedium,
                                color = controlTint,
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.fillMaxWidth().height(8.dp))

                if (!isLargeScreen) {
                    CollapsibleQueue(
                        playlistActions = playlistActions,
                        modifier = Modifier
                            .conditional(
                                condition = isQueueExpanded,
                                ifTrue = {
                                    weight(1f).nestedScroll(queueCollapseNestedScroll)
                                },
                                ifFalse = { wrapContentHeight() },
                            ),
                        queue = player.queue,
                        isQueueExpanded = isQueueExpanded,
                        onQueueExpandedSwitch = { onExpandQueue(!isQueueExpanded) },
                        onGoToLibrary = onClose,
                        queueAction = queueAction,
                        tint = colors.controlTint,
                        isCurrentPage = isCurrentPage,
                        contentPadding = contentPadding,
                        livePositionFlow = livePositionFlow,
                        onChapterClick = { chapter ->
                            playerAction(player, PlayerAction.SeekTo(chapterSeekSeconds(chapter.start)))
                        },
                    )
                } else {
                    Spacer(
                        modifier = Modifier.fillMaxWidth()
                            .height(contentPadding.calculateBottomPadding()),
                    )
                }
            }

            if (isLargeScreen && player.queue is DataState.Data) {
                Queue(
                    queue = player.queue,
                    onGoToLibrary = onClose,
                    isQueueExpanded = true,
                    isCurrentPage = isCurrentPage,
                    contentPadding = contentPadding,
                    queueAction = queueAction,
                    playlistActions = playlistActions,
                    livePositionFlow = livePositionFlow,
                    onChapterClick = { chapter ->
                        playerAction(player, PlayerAction.SeekTo(chapterSeekSeconds(chapter.start)))
                    },
                )
            }
        }
    }
}

@Composable
private fun PlayerOverflowMenu(
    currentPlayer: PlayerData,
    allPlayers: List<PlayerData>,
    playerAction: (PlayerAction) -> Unit,
    queueAction: (QueueAction) -> Unit,
    navigateToItem: (AppMediaItem) -> Unit,
    onPlayerSelected: (String) -> Unit,
    onOpenDsp: (() -> Unit)?,
    playlistActions: PlaylistActions? = null,
) {
    var transferMenuExpanded by remember { mutableStateOf(false) }
    val currentTrack = currentPlayer.queueInfo?.currentItem?.track as? Track
    var showAddToPlaylist by remember { mutableStateOf(false) }

    val queueData = currentPlayer.queue as? DataState.Data
    val queueInfo = queueData?.data?.info
    val queueId = queueInfo?.id
    val queueHasItems = !(queueData?.data?.items as? DataState.Data)?.data.isNullOrEmpty()
    val queueOptions = if (queueId != null && queueHasItems) {
        buildList {
            add(
                OverflowMenuOption(
                    title = stringResource(Res.string.queue_transfer),
                    icon = Icons.Default.SwapHoriz,
                    trailingIcon = Icons.AutoMirrored.Default.ArrowRight,
                    onClick = { transferMenuExpanded = true },
                ),
            )
            add(
                OverflowMenuOption(
                    title = stringResource(Res.string.queue_clear),
                    icon = Icons.Default.DeleteSweep,
                    onClick = { queueAction(QueueAction.ClearQueue(queueId)) },
                ),
            )
            if (queueData.data.info.let { it.dontStopTheMusicEnabled != null && !it.isDynamicPlaylist }) {
                add(
                    OverflowMenuOption(
                        title = stringResource(
                            if (queueData.data.info.dontStopTheMusicEnabled == true) {
                                Res.string.queue_dsm_disable
                            } else {
                                Res.string.queue_dsm_enable
                            },
                        ),
                        icon = Icons.Default.AllInclusive,
                        onClick = {
                            playerAction(
                                PlayerAction.ToggleDontStopTheMusic(
                                    queueData.data.info.dontStopTheMusicEnabled == true,
                                ),
                            )
                        },
                    ),
                )
            }
        }
    } else {
        emptyList()
    }

    if (queueId != null) {
        Box(modifier = Modifier.wrapContentSize(Alignment.TopStart)) {
            OverflowMenu(
                expanded = transferMenuExpanded,
                onClose = { transferMenuExpanded = false },
                options = allPlayers.filter { p -> p.player.id != queueId }.map { playerData ->
                    OverflowMenuOption(
                        title = playerData.player.nameAndSuffix,
                        leadingContent = {
                            PlayerIcon(
                                player = playerData.player,
                                isLocal = playerData.isLocal,
                                modifier = Modifier.size(24.dp),
                            )
                        },
                        onClick = {
                            queueAction(
                                QueueAction.Transfer(
                                    queueId,
                                    playerData.player.id,
                                    currentPlayer.player.isPlaying,
                                ),
                            )
                            onPlayerSelected.invoke(playerData.player.id)
                        },
                    )
                }.ifEmpty {
                    listOf(
                        OverflowMenuOption(
                            title = stringResource(Res.string.queue_no_other_players),
                            onClick = { /* No-op */ },
                        ),
                    )
                },
            )
        }
    }

    val playerOptions = buildList {
        if (onOpenDsp != null) {
            add(
                OverflowMenuOption(
                    title = stringResource(Res.string.players_dsp_settings),
                    icon = Icons.Default.Tune,
                    onClick = onOpenDsp,
                ),
            )
        }
        if (currentTrack != null && playlistActions != null) {
            add(
                OverflowMenuOption(
                    title = stringResource(Res.string.action_add_to_playlist),
                    icon = Icons.AutoMirrored.Filled.PlaylistAdd,
                    onClick = { showAddToPlaylist = true },
                ),
            )
        }
    }

    val navigationOptions =
        (currentPlayer.queueInfo?.currentItem?.track as? AppMediaItem)?.navigationOptions(
            navigateToItem,
        )
            ?: emptyList()

    val menuOptions = queueOptions + playerOptions + navigationOptions
    if (menuOptions.isNotEmpty()) {
        OverflowMenuButton(
            modifier = Modifier,
            options = menuOptions,
        ) { onClick ->
            IconButton(onClick = onClick) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = stringResource(Res.string.cd_more),
                )
            }
        }
    }

    if (showAddToPlaylist && currentTrack != null && playlistActions != null) {
        AddToPlaylistDialog(
            item = currentTrack,
            playlistActions = playlistActions,
            onDismiss = { showAddToPlaylist = false },
        )
    }
}

@Composable
private fun CollapsedPlayerPage(
    isExpandedScreen: Boolean,
    player: PlayerData,
    colors: PlayerColors,
    sendspinState: SendspinState?,
    onSelectPlayer: () -> Unit,
    onGroupButton: () -> Unit,
    playerAction: (PlayerData, PlayerAction) -> Unit,
) {
    if (!isExpandedScreen) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
        ) {
            PlayerSelectionButton(
                player = player,
                controlTint = colors.controlTint,
                sendSpinState = sendspinState,
                onSelectPlayer = onSelectPlayer,
                onGroupButton = onGroupButton,
            )
        }
    }

    CompactPlayerItem(
        modifier = Modifier.padding(top = 8.dp, bottom = 16.dp),
        item = player,
        colors = colors,
        playerAction = playerAction,
        onSelectPlayer = if (isExpandedScreen) onSelectPlayer else null,
        onGroupButton = if (isExpandedScreen) onGroupButton else null,
        sendSpinState = sendspinState,
    )
}

@Preview
@Composable
fun ExpandedPlayerPagePreview() {
    MaterialTheme(colorScheme = darkColorScheme()) {
        val track = AppMediaItemFixtures.track()
        val playerData = PlayerDataFixtures.playerData(
            listOf(track.toQueueTrack()).toQueue(true),
        )

        ExpandedPlayerPage(
            player = playerData,
            colors = PlayerColors(
                MaterialTheme.colorScheme.surface,
                MaterialTheme.colorScheme.onSurface,
            ),
            onSelectPlayer = {},
            onGroupButton = {},
            onDspButton = null,
            playerAction = { _, _ -> },
            onFavoriteClick = {},
            onClose = {},
            queueAction = {},
            allPlayers = listOf(playerData),
            moveToPlayer = {},
            isExpandedScreen = false,
            sendspinState = null,
            isQueueExpanded = false,
            onExpandQueue = {},
            contentPadding = PaddingValues(),
            isCurrentPage = true,
            livePositionFlow = null,
        )
    }
}

@Preview(
    widthDp = WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND,
    heightDp = WindowSizeClass.HEIGHT_DP_MEDIUM_LOWER_BOUND,
)
@Composable
fun ExpandedPlayerPageMediumScreenPreview() {
    MaterialTheme(colorScheme = darkColorScheme()) {
        val track = AppMediaItemFixtures.track()
        val playerData = PlayerDataFixtures.playerData(listOf(track.toQueueTrack()).toQueue())

        ExpandedPlayerPage(
            player = playerData,
            colors = PlayerColors(
                MaterialTheme.colorScheme.surface,
                MaterialTheme.colorScheme.onSurface,
            ),
            onSelectPlayer = {},
            onGroupButton = {},
            onDspButton = null,
            playerAction = { _, _ -> },
            onFavoriteClick = {},
            onClose = {},
            queueAction = {},
            allPlayers = listOf(playerData),
            moveToPlayer = {},
            isExpandedScreen = false,
            sendspinState = null,
            isQueueExpanded = false,
            onExpandQueue = {},
            contentPadding = PaddingValues(),
            isCurrentPage = true,
            livePositionFlow = null,
        )
    }
}

@Preview(
    widthDp = WindowSizeClass.WIDTH_DP_EXPANDED_LOWER_BOUND,
    heightDp = WindowSizeClass.HEIGHT_DP_EXPANDED_LOWER_BOUND,
)
@Composable
fun ExpandedPlayerPageExpandedScreenPreview() {
    MaterialTheme(colorScheme = darkColorScheme()) {
        val track = AppMediaItemFixtures.track()
        val playerData = PlayerDataFixtures.playerData(listOf(track.toQueueTrack()).toQueue())

        ExpandedPlayerPage(
            player = playerData,
            colors = PlayerColors(
                MaterialTheme.colorScheme.surface,
                MaterialTheme.colorScheme.onSurface,
            ),
            onSelectPlayer = {},
            onGroupButton = {},
            onDspButton = null,
            playerAction = { _, _ -> },
            onFavoriteClick = {},
            onClose = {},
            queueAction = {},
            allPlayers = listOf(playerData),
            moveToPlayer = {},
            isExpandedScreen = true,
            sendspinState = null,
            isQueueExpanded = false,
            onExpandQueue = {},
            contentPadding = PaddingValues(),
            isCurrentPage = true,
            livePositionFlow = null,
        )
    }
}

/**
 * Some screens (like an iPad) are in between the expanded and large size classes - this simulates
 * that case
 */
@Preview(
    widthDp = WindowSizeClass.WIDTH_DP_LARGE_LOWER_BOUND - 1,
    heightDp = WindowSizeClass.HEIGHT_DP_EXPANDED_LOWER_BOUND,
)
@Composable
fun ExpandedPlayerPageExpandedScreenPlusPreview() {
    MaterialTheme(colorScheme = darkColorScheme()) {
        val track = AppMediaItemFixtures.track()
        val playerData = PlayerDataFixtures.playerData(listOf(track.toQueueTrack()).toQueue())

        ExpandedPlayerPage(
            player = playerData,
            colors = PlayerColors(
                MaterialTheme.colorScheme.surface,
                MaterialTheme.colorScheme.onSurface,
            ),
            onSelectPlayer = {},
            onGroupButton = {},
            onDspButton = null,
            playerAction = { _, _ -> },
            onFavoriteClick = {},
            onClose = {},
            queueAction = {},
            allPlayers = listOf(playerData),
            moveToPlayer = {},
            isExpandedScreen = false,
            sendspinState = null,
            isQueueExpanded = false,
            onExpandQueue = {},
            contentPadding = PaddingValues(),
            isCurrentPage = true,
            livePositionFlow = null,
        )
    }
}

@Preview(
    widthDp = WindowSizeClass.WIDTH_DP_LARGE_LOWER_BOUND,
    heightDp = WindowSizeClass.HEIGHT_DP_EXPANDED_LOWER_BOUND,
)
@Composable
fun ExpandedPlayerPageLargeScreenPreview() {
    MaterialTheme(colorScheme = darkColorScheme()) {
        val track = AppMediaItemFixtures.track()
        val playerData = PlayerDataFixtures.playerData(listOf(track.toQueueTrack()).toQueue(hasRadio = true))

        ExpandedPlayerPage(
            player = playerData,
            colors = PlayerColors(
                MaterialTheme.colorScheme.surface,
                MaterialTheme.colorScheme.onSurface,
            ),
            onSelectPlayer = {},
            onGroupButton = {},
            onDspButton = null,
            playerAction = { _, _ -> },
            onFavoriteClick = {},
            onClose = {},
            queueAction = {},
            allPlayers = listOf(playerData),
            moveToPlayer = {},
            isExpandedScreen = true,
            sendspinState = null,
            isQueueExpanded = false,
            onExpandQueue = {},
            contentPadding = PaddingValues(),
            isCurrentPage = true,
            livePositionFlow = null,
        )
    }
}
