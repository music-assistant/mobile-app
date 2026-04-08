@file:OptIn(ExperimentalMaterial3Api::class)

package io.music_assistant.client.ui.compose.home

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeMute
import androidx.compose.material.icons.automirrored.filled.VolumeUp
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.music_assistant.client.data.model.client.AppMediaItem
import io.music_assistant.client.data.model.client.PlayerData
import io.music_assistant.client.ui.compose.common.action.PlayerAction
import io.music_assistant.client.ui.compose.common.action.QueueAction
import io.music_assistant.client.ui.compose.common.icons.VolumeIcon
import io.music_assistant.client.ui.compose.common.icons.VolumeMutedIcon
import io.music_assistant.client.ui.compose.common.rememberAnimatedDominantColor
import io.music_assistant.client.ui.compose.home.players.GroupSettingsDialog
import io.music_assistant.client.ui.compose.home.players.PlayerSelectionLayout
import io.music_assistant.client.ui.compose.home.players.SelectPlayerDialog
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
    isExpandedScreen: Boolean
) {
    val modifier = if (expanded) {
        modifier
    } else {
        modifier.height(collapsedPlayerHeight(isExpandedScreen))
    }

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
            modifier = Modifier
                .wrapContentHeight(),
            state = playerPagerState,
            key = { page -> playerDataList.getOrNull(page)?.player?.id ?: page }
        ) { page ->
            val player = playerDataList.getOrNull(page) ?: return@HorizontalPager
            val isLocalPlayer = player.playerId == playersState.localPlayerId

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

            var isQueueExpanded by remember { mutableStateOf(false) }

            val imageUrl = player.queueInfo?.currentItem?.track?.imageInfo?.url(serverUrl)
            val dominantColor by rememberAnimatedDominantColor(
                imageUrl = imageUrl,
                fallback = MaterialTheme.colorScheme.primaryContainer
            )

            Column(
                Modifier.background(
                    brush = if (isLocalPlayer) {
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
                                dominantColor.copy(alpha = 0.6f)
                            )
                        )
                    }
                )
            ) {
                if (!isExpandedScreen || expanded) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        PlayerSelectionLayout(
                            player = player,
                            playersState = playersState,
                            onSelectPlayer = onSelectPlayer,
                            onGroupButton = onGroupButton
                        )
                    }
                }

                AnimatedVisibility(
                    visible = isQueueExpanded.takeIf { expanded } != false,
                    enter = fadeIn(tween(300)) + expandVertically(tween(300)),
                    exit = fadeOut(tween(200)) + shrinkVertically(tween(300))
                ) {
                    Box(
                        modifier = Modifier
                            .padding(top = 2.dp)
                            .fillMaxWidth()
                            .wrapContentSize()
                            .conditional(
                                expanded,
                                { clickable { isQueueExpanded = false } }
                            )
                    ) {
                        CompactPlayerItem(
                            item = player,
                            playersState = playersState,
                            serverUrl = serverUrl,
                            playerAction = playerAction,
                            onSelectPlayer = if (isExpandedScreen && !isQueueExpanded) onSelectPlayer else null,
                            onGroupButton = if (isExpandedScreen && !isQueueExpanded) onGroupButton else null,
                            showAdditionalControls = isExpandedScreen,
                        )
                    }
                }

                Column(
                    modifier = Modifier
                        .conditional(
                            condition = isQueueExpanded.takeIf { expanded } == false,
                            ifTrue = { weight(1f) },
                            ifFalse = { wrapContentHeight() }
                        )
                ) {
                    AnimatedVisibility(
                        visible = isQueueExpanded.takeIf { expanded } == false,
                        enter = fadeIn(tween(300)) + expandVertically(tween(300)),
                        exit = fadeOut(tween(200)) + shrinkVertically(tween(300))
                    ) {

                        FullPlayerItem(
                            modifier = Modifier.fillMaxSize(),
                            item = player,
                            isLocal = isLocalPlayer,
                            serverUrl = serverUrl,
                            playerAction = playerAction,
                            onFavoriteClick = onFavoriteClick,
                        )
                    }
                }

                if (
                    expanded
                    && player.player.isVolumeSliderAccessible
                    && player.player.currentVolume != null
                ) {
                    if (!isLocalPlayer) {
                        var currentVolume by remember(player.player.currentVolume) {
                            mutableStateOf(player.player.currentVolume)
                        }
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
                                tint = MaterialTheme.colorScheme.primary
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
                                        if (player.groupChildren.none { it.isBound }) {
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
                                        colors = SliderDefaults.colors()
                                            .copy(thumbColor = MaterialTheme.colorScheme.secondary),
                                    )
                                },
                                track = { sliderState ->
                                    SliderDefaults.Track(
                                        sliderState = sliderState,
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

                player.queue.takeIf { expanded }?.let { queue ->
                    CollapsibleQueue(
                        modifier = Modifier
                            .conditional(
                                condition = isQueueExpanded,
                                ifTrue = { weight(1f) },
                                ifFalse = { wrapContentHeight() }
                            ),
                        queue = queue,
                        isQueueExpanded = isQueueExpanded,
                        onQueueExpandedSwitch = { isQueueExpanded = !isQueueExpanded },
                        onGoToLibrary = onClose,
                        serverUrl = serverUrl,
                        queueAction = queueAction,
                        players = playerDataList,
                        onPlayerSelected = { playerId ->
                            moveToPlayer(playerId)
                        },
                        isCurrentPage = page == playerPagerState.currentPage
                    )
                }
            }
        }
    }
}

fun collapsedPlayerHeight(isExpandedScreen: Boolean): Dp {
    return if (isExpandedScreen) {
        84.dp
    } else {
        130.dp
    }
}
