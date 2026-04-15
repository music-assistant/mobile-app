@file:OptIn(ExperimentalMaterial3Api::class)

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
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.music_assistant.client.data.model.client.AppMediaItem
import io.music_assistant.client.data.model.client.PlayerData
import io.music_assistant.client.player.sendspin.SendspinState
import io.music_assistant.client.ui.compose.common.action.PlayerAction
import io.music_assistant.client.ui.compose.common.action.QueueAction
import io.music_assistant.client.ui.compose.common.asControlTint
import io.music_assistant.client.ui.compose.common.icons.VolumeIcon
import io.music_assistant.client.ui.compose.common.icons.VolumeMutedIcon
import io.music_assistant.client.ui.compose.common.rememberAnimatedDominantColor
import io.music_assistant.client.ui.compose.home.CollapsibleQueue
import io.music_assistant.client.ui.compose.home.HomeScreenViewModel
import io.music_assistant.client.ui.compose.home.HorizontalPagerIndicator
import io.music_assistant.client.utils.conditional

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
    onItemMoved: ((Int) -> Unit)?,
    queueAction: (QueueAction) -> Unit,
    moveToPlayer: (String) -> Unit,
    isExpandedScreen: Boolean,
    contentPadding: PaddingValues
) {
    val modifier = if (expanded) {
        modifier
    } else {
        modifier.height(collapsedPlayerHeight(isExpandedScreen))
    }

    var isQueueExpanded by remember { mutableStateOf(false) }

    // Extract playerData list to ensure proper recomposition
    val playerDataList = playersState.playerData
    Column(modifier = modifier) {
        if (playerDataList.size > 1) {
            HorizontalPagerIndicator(
                pagerState = playerPagerState,
                allowMoving = expanded,
                onItemMoved = onItemMoved
            )
        }

        HorizontalPager(
            modifier = Modifier,
            state = playerPagerState,
            key = { page -> playerDataList.getOrNull(page)?.player?.id ?: page }
        ) { page ->
            val player = playerDataList.getOrNull(page) ?: return@HorizontalPager
            var showSelectDialog by remember { mutableStateOf(false) }
            var showGroupDialog by remember { mutableStateOf(false) }
            val onSelectPlayer = { showSelectDialog = true }
            val onGroupButton = { showGroupDialog = true }
            if (showSelectDialog) {
                SelectPlayerDialog(
                    selectedPlayer = player,
                    players = playerDataList,
                    onDismissRequest = { showSelectDialog = false },
                    onMoveToPlayer = { moveToPlayer(it) },
                )
            }
            if (showGroupDialog) {
                GroupSettingsDialog(
                    player = player,
                    onDismissRequest = { showGroupDialog = false },
                    groupAction = simplePlayerAction
                )
            }

            val imageUrl = player.queueInfo?.currentItem?.track?.imageInfo?.url(serverUrl)
            val dominantColor by rememberAnimatedDominantColor(
                imageUrl = imageUrl,
                fallback = MaterialTheme.colorScheme.primaryContainer
            )
            Box(modifier = Modifier.fillMaxSize()) {
                Column(
                    Modifier
                        .fillMaxSize()
                        .background(
                            brush = if (player.isLocal) {
                                Brush.verticalGradient(
                                    listOf(
                                        MaterialTheme.colorScheme.surfaceContainerHigh,
                                        MaterialTheme.colorScheme.surfaceContainerLow
                                    )
                                )
                            } else {
                                Brush.verticalGradient(
                                    listOf(
                                        MaterialTheme.colorScheme.surfaceContainerHigh,
                                        dominantColor.copy(alpha = 0.4f)
                                    )
                                )
                            }
                        )
                ) {
                    if (expanded) {
                        ExpandedPlayerPage(
                            player = player,
                            dominantColor = dominantColor,
                            onSelectPlayer = onSelectPlayer,
                            onGroupButton = onGroupButton,
                            serverUrl = serverUrl,
                            playerAction = playerAction,
                            onFavoriteClick = onFavoriteClick,
                            onClose = onClose,
                            queueAction = queueAction,
                            allPlayers = playerDataList,
                            moveToPlayer = moveToPlayer,
                            page = page,
                            playerPagerState = playerPagerState,
                            isExpandedScreen = isExpandedScreen,
                            sendspinState = playersState.sendspinState,
                            isQueueExpanded = isQueueExpanded,
                            onExpandQueue = { isQueueExpanded = it },
                            contentPadding = contentPadding
                        )
                    } else {
                        CollapsedPlayerPage(
                            isExpandedScreen = isExpandedScreen,
                            player = player,
                            dominantColor = dominantColor,
                            sendspinState = playersState.sendspinState,
                            onSelectPlayer = onSelectPlayer,
                            onGroupButton = onGroupButton,
                            serverUrl = serverUrl,
                            playerAction = playerAction
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
        contentAlignment = Alignment.Center
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
    dominantColor: Color,
    onSelectPlayer: () -> Unit,
    onGroupButton: () -> Unit,
    serverUrl: String?,
    playerAction: (PlayerData, PlayerAction) -> Unit,
    onFavoriteClick: (AppMediaItem) -> Unit,
    onClose: () -> Unit,
    queueAction: (QueueAction) -> Unit,
    allPlayers: List<PlayerData>,
    moveToPlayer: (String) -> Unit,
    page: Int,
    playerPagerState: PagerState,
    isExpandedScreen: Boolean,
    sendspinState: SendspinState?,
    isQueueExpanded: Boolean,
    onExpandQueue: (Boolean) -> Unit,
    contentPadding: PaddingValues
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center
        ) {
            PlayerSelectionLayout(
                player = player,
                sendSpinState = sendspinState,
                onSelectPlayer = onSelectPlayer,
                onGroupButton = onGroupButton
            )
        }

        AnimatedVisibility(
            visible = isQueueExpanded,
            enter = fadeIn(tween(300)) + expandVertically(tween(300)),
            exit = fadeOut(tween(200)) + shrinkVertically(tween(300))
        ) {
            Box(
                modifier = Modifier
                    .padding(top = 2.dp)
                    .fillMaxWidth()
                    .wrapContentSize()
                    .clickable { onExpandQueue(false) }
            ) {
                CompactPlayerItem(
                    item = player,
                    dominantColor = dominantColor,
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
                    ifFalse = { wrapContentHeight() }
                )
        ) {
            AnimatedVisibility(
                visible = !isQueueExpanded,
                enter = fadeIn(tween(300)) + expandVertically(tween(300)),
                exit = fadeOut(tween(200)) + shrinkVertically(tween(300))
            ) {
                FullPlayerItem(
                    modifier = Modifier.fillMaxSize(),
                    item = player,
                    isLocal = player.isLocal,
                    dominantColor = dominantColor,
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
                val controlTint = dominantColor.asControlTint()
                val volumeSliderColors = SliderDefaults.colors().copy(
                    thumbColor = controlTint,
                    activeTrackColor = controlTint,
                )
                Row(
                    modifier = Modifier.fillMaxWidth().height(36.dp)
                        .padding(horizontal = 64.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        modifier = Modifier
                            .size(24.dp)
                            .alpha(if (player.player.canMute) 1F else 0.5f)
                            .clickable(enabled = player.player.canMute) {
                                playerAction(
                                    player,
                                    PlayerAction.ToggleMute(player.player.volumeMuted)
                                )
                            },
                        imageVector = if (player.player.volumeMuted)
                            VolumeMutedIcon
                        else
                            VolumeIcon,
                        contentDescription = "Volume",
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
                                }
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
                                modifier = Modifier.height(4.dp)
                            )
                        }
                    )
                }
            } else {
                Text(
                    modifier = Modifier.fillMaxWidth().height(36.dp),
                    text = "use device buttons to adjust the volume",
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )
            }
        }

        Spacer(modifier = Modifier.fillMaxWidth().height(8.dp))

        CollapsibleQueue(
            modifier = Modifier
                .conditional(
                    condition = isQueueExpanded,
                    ifTrue = { weight(1f) },
                    ifFalse = { wrapContentHeight() }
                ),
            queue = player.queue,
            isQueueExpanded = isQueueExpanded,
            onQueueExpandedSwitch = { onExpandQueue(!isQueueExpanded) },
            onGoToLibrary = onClose,
            serverUrl = serverUrl,
            queueAction = queueAction,
            tint = dominantColor.asControlTint(),
            players = allPlayers,
            onPlayerSelected = { moveToPlayer(it) },
            isCurrentPage = page == playerPagerState.currentPage,
            contentPadding = contentPadding
        )
    }
}

@Composable
private fun CollapsedPlayerPage(
    isExpandedScreen: Boolean,
    player: PlayerData,
    dominantColor: Color,
    sendspinState: SendspinState?,
    onSelectPlayer: () -> Unit,
    onGroupButton: () -> Unit,
    serverUrl: String?,
    playerAction: (PlayerData, PlayerAction) -> Unit
) {
    if (!isExpandedScreen) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center
        ) {
            PlayerSelectionLayout(
                player = player,
                sendSpinState = sendspinState,
                onSelectPlayer = onSelectPlayer,
                onGroupButton = onGroupButton
            )
        }
    }

    CompactPlayerItem(
        item = player,
        dominantColor = dominantColor,
        serverUrl = serverUrl,
        playerAction = playerAction,
        onSelectPlayer = if (isExpandedScreen) onSelectPlayer else null,
        onGroupButton = if (isExpandedScreen) onGroupButton else null,
        sendSpinState = sendspinState
    )
}

fun collapsedPlayerHeight(isExpandedScreen: Boolean): Dp {
    return if (isExpandedScreen) {
        84.dp
    } else {
        130.dp
    }
}
