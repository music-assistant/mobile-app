@file:OptIn(ExperimentalMaterial3Api::class)
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
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowRight
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.Speaker
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Tune
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.music_assistant.client.data.model.client.AppMediaItem
import io.music_assistant.client.data.model.client.AppMediaItemFixtures
import io.music_assistant.client.data.model.client.PlayerData
import io.music_assistant.client.data.model.client.PlayerDataFixtures
import io.music_assistant.client.data.model.client.Queue
import io.music_assistant.client.data.model.client.QueueInfo
import io.music_assistant.client.data.model.client.QueueTrack
import io.music_assistant.client.data.model.server.RepeatMode
import io.music_assistant.client.player.sendspin.SendspinState
import io.music_assistant.client.ui.alphaOn
import io.music_assistant.client.ui.compose.common.DataState
import io.music_assistant.client.ui.compose.common.ExtractedColorsFetcher
import io.music_assistant.client.ui.compose.common.OverflowMenu
import io.music_assistant.client.ui.compose.common.OverflowMenuButton
import io.music_assistant.client.ui.compose.common.OverflowMenuOption
import io.music_assistant.client.ui.compose.common.PlayerColors
import io.music_assistant.client.ui.compose.common.action.PlayerAction
import io.music_assistant.client.ui.compose.common.action.QueueAction
import io.music_assistant.client.ui.compose.common.icons.SpeakerMultipleIcon
import io.music_assistant.client.ui.compose.common.icons.VolumeIcon
import io.music_assistant.client.ui.compose.common.icons.VolumeMutedIcon
import io.music_assistant.client.ui.compose.common.items.navigationOptions
import io.music_assistant.client.ui.compose.common.rememberAnimatedPlayerColors
import io.music_assistant.client.ui.compose.home.CollapsibleQueue
import io.music_assistant.client.ui.compose.home.HomeScreenViewModel
import io.music_assistant.client.ui.compose.home.HorizontalPagerIndicator
import io.music_assistant.client.ui.inactive
import io.music_assistant.client.utils.conditional
import musicassistantclient.composeapp.generated.resources.Res
import musicassistantclient.composeapp.generated.resources.cd_more
import musicassistantclient.composeapp.generated.resources.cd_mute
import musicassistantclient.composeapp.generated.resources.cd_unmute
import musicassistantclient.composeapp.generated.resources.players_dsp_settings
import musicassistantclient.composeapp.generated.resources.queue_clear
import musicassistantclient.composeapp.generated.resources.queue_no_other_players
import musicassistantclient.composeapp.generated.resources.queue_transfer
import org.jetbrains.compose.resources.stringResource
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PlayersPager(
    modifier: Modifier = Modifier,
    playerPagerState: PagerState,
    playersState: HomeScreenViewModel.PlayersState.Data,
    serverUrl: String?,
    simplePlayerAction: (String, PlayerAction) -> Unit,
    playerAction: (PlayerData, PlayerAction) -> Unit,
    onFavoriteClick: (AppMediaItem) -> Unit,
    expanded: Boolean,
    onClose: () -> Unit,
    navigateToItem: (AppMediaItem) -> Unit,
    onPlayersReorder: (List<String>) -> Unit,
    queueAction: (QueueAction) -> Unit,
    moveToPlayer: (String) -> Unit,
    isExpandedScreen: Boolean,
    contentPadding: PaddingValues,
    localPlayerId: String,
    onAdjustPlaybackDelay: (Int) -> Unit,
    fetchColors: ExtractedColorsFetcher,
) {
    val modifier = if (expanded) {
        modifier
    } else {
        modifier.height(collapsedPlayerHeight(isExpandedScreen))
    }

    var isQueueExpanded by remember { mutableStateOf(false) }

    // Extract playerData list to ensure proper recomposition
    val playerDataList = playersState.playerData

    // Select-player dialog is hoisted out of the pager so that reordering-induced
    // pager scrolls don't tear down the dialog's composable.
    var selectDialogPlayerId by remember { mutableStateOf<String?>(null) }
    val selectDialogPlayer = selectDialogPlayerId?.let { id ->
        playerDataList.firstOrNull { it.player.id == id }
    }
    if (selectDialogPlayer != null) {
        SelectPlayerDialog(
            selectedPlayer = selectDialogPlayer,
            players = playerDataList,
            onDismissRequest = { selectDialogPlayerId = null },
            onMoveToPlayer = { moveToPlayer(it) },
            onReorder = onPlayersReorder,
        )
    }

    val playerColors = playerDataList.associateWith {
        val imageUrl = it.queueInfo?.currentItem?.track?.imageInfo?.url(serverUrl)
        rememberAnimatedPlayerColors(
            imageUrl = imageUrl,
            fallback = MaterialTheme.colorScheme.primaryContainer,
            fetchColors = fetchColors,
        )
    }

    Column(modifier = modifier) {
        if (playerDataList.size > 1) {
            HorizontalPagerIndicator(
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
                    groupAction = simplePlayerAction,
                    localPlayerId = localPlayerId,
                    onAdjustPlaybackDelay = onAdjustPlaybackDelay,
                )
            }
            if (showDspDialog) {
                DspSettingsDialog(
                    playerId = player.player.id,
                    onDismissRequest = { showDspDialog = false },
                )
            }

            val colors by playerColors.getValue(player)

            Box(modifier = Modifier.fillMaxSize()) {
                Column(
                    Modifier
                        .fillMaxSize()
                        .background(
                            brush = if (player.isLocal) {
                                Brush.verticalGradient(
                                    listOf(
                                        MaterialTheme.colorScheme.surfaceContainerHigh,
                                        MaterialTheme.colorScheme.surfaceContainerLow,
                                    ),
                                )
                            } else {
                                Brush.verticalGradient(
                                    listOf(
                                        MaterialTheme.colorScheme.surfaceContainerHigh,
                                        colors.dominant.inactive(),
                                    ),
                                )
                            },
                        ),
                ) {
                    if (expanded) {
                        ExpandedPlayerPage(
                            player = player,
                            colors = colors,
                            onSelectPlayer = onSelectPlayer,
                            onGroupButton = onGroupButton,
                            onDspButton = onDspButton.takeIf { !player.player.isGroup },
                            serverUrl = serverUrl,
                            playerAction = playerAction,
                            onFavoriteClick = onFavoriteClick,
                            onClose = onClose,
                            queueAction = queueAction,
                            allPlayers = playerDataList,
                            moveToPlayer = moveToPlayer,
                            isExpandedScreen = isExpandedScreen,
                            sendspinState = playersState.sendspinState,
                            isQueueExpanded = isQueueExpanded,
                            onExpandQueue = { isQueueExpanded = it },
                            contentPadding = contentPadding,
                            isCurrentPage = page == playerPagerState.currentPage,
                            navigateToItem = navigateToItem,
                        )
                    } else {
                        CollapsedPlayerPage(
                            isExpandedScreen = isExpandedScreen,
                            player = player,
                            colors = colors,
                            sendspinState = playersState.sendspinState,
                            onSelectPlayer = onSelectPlayer,
                            onGroupButton = onGroupButton,
                            serverUrl = serverUrl,
                            playerAction = playerAction,
                        )
                    }
                }
                player.parentBind?.let {
                    BoundPlayerInfo(
                        modifier = Modifier.fillMaxSize(),
                        playerName = player.player.name,
                        parent = it,
                        moveToPlayer = moveToPlayer,
                    )
                }
            }
        }
    }
}

@Composable
fun BoundPlayerInfo(
    modifier: Modifier,
    playerName: String,
    parent: PlayerData.ParentBind,
    moveToPlayer: (String) -> Unit,
) {
    val status = when {
        parent.isPlaying -> "playing with${if (parent.isGroup) " group" else ""}"
        parent.isGroup -> "part of group"
        else -> "joined to"
    }
    Box(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.9f))
            .clickable { moveToPlayer(parent.id) },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "$playerName is $status ${parent.name}\n(tap to view)",
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
    serverUrl: String?,
    playerAction: (PlayerData, PlayerAction) -> Unit,
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
) {
    Column(
        modifier = Modifier.padding(top = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.CenterEnd,
        ) {
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                PlayerSelectionButton(
                    player = player,
                    sendSpinState = sendspinState,
                    onSelectPlayer = onSelectPlayer,
                    onGroupButton = onGroupButton,
                )
            }

            PlayerOverflowMenu(
                player = player,
                allPlayers = allPlayers,
                queueAction = queueAction,
                navigateToItem = {
                    navigateToItem(it)
                    onClose()
                },
                onPlayerSelected = { moveToPlayer(it) },
                onOpenDsp = onDspButton,
            )
        }

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
                    item = player,
                    colors = colors,
                    serverUrl = serverUrl,
                    playerAction = playerAction,
                    onSelectPlayer = if (isExpandedScreen && !isQueueExpanded) onSelectPlayer else null,
                    onGroupButton = if (isExpandedScreen && !isQueueExpanded) onGroupButton else null,
                    showAdditionalControls = isExpandedScreen,
                    sendSpinState = sendspinState,
                )
            }
        }

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
                    modifier = Modifier.fillMaxSize(),
                    item = player,
                    isLocal = player.isLocal,
                    colors = colors,
                    serverUrl = serverUrl,
                    playerAction = playerAction,
                    onFavoriteClick = onFavoriteClick,
                )
            }
        }

        if (player.player.isVolumeSliderAccessible && player.player.currentVolume != null) {
            if (!player.isLocal) {
                var currentVolume by remember(player.player.currentVolume) {
                    mutableStateOf(player.player.currentVolume)
                }
                val controlTint = colors.controlTint
                val volumeSliderColors = SliderDefaults.colors().copy(
                    thumbColor = controlTint,
                    activeTrackColor = controlTint,
                    inactiveTrackColor = controlTint.inactive(),
                )
                Row(
                    modifier = Modifier.fillMaxWidth().height(36.dp)
                        .padding(horizontal = 32.dp),
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
                                        PlayerAction.GroupToggleMute(player.player.currentMuteState)
                                    } else {
                                        PlayerAction.ToggleMute(player.player.currentMuteState)
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
                    Slider(
                        modifier = Modifier.weight(1f),
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
                    Text(
                        modifier = Modifier.width(24.dp),
                        text = currentVolume.roundToInt().toString(),
                        textAlign = TextAlign.End,
                        style = MaterialTheme.typography.bodyMedium,
                        color = controlTint,
                    )
                }
            } else {
                Text(
                    modifier = Modifier.fillMaxWidth().height(36.dp),
                    text = "use device buttons to adjust the volume",
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                )
            }
        }

        Spacer(modifier = Modifier.fillMaxWidth().height(8.dp))

        CollapsibleQueue(
            modifier = Modifier
                .conditional(
                    condition = isQueueExpanded,
                    ifTrue = { weight(1f) },
                    ifFalse = { wrapContentHeight() },
                ),
            queue = player.queue,
            isQueueExpanded = isQueueExpanded,
            onQueueExpandedSwitch = { onExpandQueue(!isQueueExpanded) },
            onGoToLibrary = onClose,
            serverUrl = serverUrl,
            queueAction = queueAction,
            tint = colors.controlTint,
            isCurrentPage = isCurrentPage,
            contentPadding = contentPadding,
        )
    }
}

@Composable
private fun PlayerOverflowMenu(
    player: PlayerData,
    allPlayers: List<PlayerData>,
    queueAction: (QueueAction) -> Unit,
    navigateToItem: (AppMediaItem) -> Unit,
    onPlayerSelected: (String) -> Unit,
    onOpenDsp: (() -> Unit)?,
) {
    var transferMenuExpanded by remember { mutableStateOf(false) }

    val queueData = player.queue as? DataState.Data
    val queueId = queueData?.data?.info?.id
    val queueHasItems = !(queueData?.data?.items as? DataState.Data)?.data.isNullOrEmpty()
    val queueOptions = if (queueId != null && queueHasItems) {
        listOf(
            OverflowMenuOption(
                title = stringResource(Res.string.queue_transfer),
                icon = Icons.Default.SwapHoriz,
                trailingIcon = Icons.AutoMirrored.Default.ArrowRight,
                onClick = { transferMenuExpanded = true },
            ),
            OverflowMenuOption(
                title = stringResource(Res.string.queue_clear),
                icon = Icons.Default.DeleteSweep,
                onClick = { queueAction(QueueAction.ClearQueue(queueId)) },
            ),
        )
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
                        icon = when {
                            playerData.isLocal -> Icons.Default.Smartphone
                            playerData.player.isGroup -> SpeakerMultipleIcon
                            else -> Icons.Default.Speaker
                        },
                        onClick = {
                            queueAction(
                                QueueAction.Transfer(
                                    queueId,
                                    playerData.player.id,
                                    playerData.player.isPlaying,
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

    val playerOptions = if (onOpenDsp != null) {
        listOf(
            OverflowMenuOption(
                title = stringResource(Res.string.players_dsp_settings),
                icon = Icons.Default.Tune,
                onClick = onOpenDsp,
            ),
        )
    } else {
        emptyList()
    }

    val navigationOptions =
        (player.queueInfo?.currentItem?.track as? AppMediaItem)?.navigationOptions(navigateToItem)
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
}

@Composable
private fun CollapsedPlayerPage(
    isExpandedScreen: Boolean,
    player: PlayerData,
    colors: PlayerColors,
    sendspinState: SendspinState?,
    onSelectPlayer: () -> Unit,
    onGroupButton: () -> Unit,
    serverUrl: String?,
    playerAction: (PlayerData, PlayerAction) -> Unit,
) {
    if (!isExpandedScreen) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
        ) {
            PlayerSelectionButton(
                player = player,
                sendSpinState = sendspinState,
                onSelectPlayer = onSelectPlayer,
                onGroupButton = onGroupButton,
            )
        }
    }

    CompactPlayerItem(
        item = player,
        colors = colors,
        serverUrl = serverUrl,
        playerAction = playerAction,
        onSelectPlayer = if (isExpandedScreen) onSelectPlayer else null,
        onGroupButton = if (isExpandedScreen) onGroupButton else null,
        sendSpinState = sendspinState,
    )
}

fun collapsedPlayerHeight(isExpandedScreen: Boolean): Dp {
    return if (isExpandedScreen) {
        84.dp
    } else {
        130.dp
    }
}

@Preview
@Composable
fun ExpandedPlayerPagePreview() {
    MaterialTheme {
        val track = AppMediaItemFixtures.tracks(listOf("Test Track")).first()
        val queueInfo = QueueInfo(
            id = "queue1",
            available = true,
            shuffleEnabled = false,
            repeatMode = RepeatMode.OFF,
            elapsedTime = 100.0,
            elapsedTimeLastUpdated = null,
            currentItem = QueueTrack(
                track = track,
                id = "",
                isPlayable = true,
                format = null,
                dsp = null,
            ),
        )

        val playerData = PlayerDataFixtures.playerData().copy(
            queue = DataState.Data(
                Queue(
                    info = queueInfo,
                    items = DataState.NoData(),
                ),
            ),
        )

        ExpandedPlayerPage(
            player = playerData,
            colors = PlayerColors(
                MaterialTheme.colorScheme.primary,
                MaterialTheme.colorScheme.onPrimary,
            ),
            onSelectPlayer = {},
            onGroupButton = {},
            onDspButton = null,
            serverUrl = null,
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
        )
    }
}
