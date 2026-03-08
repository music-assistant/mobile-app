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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeMute
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.music_assistant.client.data.model.client.AppMediaItem
import io.music_assistant.client.data.model.client.PlayerData
import io.music_assistant.client.player.sendspin.SendspinState
import io.music_assistant.client.ui.compose.common.action.PlayerAction
import io.music_assistant.client.ui.compose.common.action.QueueAction
import io.music_assistant.client.ui.compose.home.players.SelectPlayerDialog
import io.music_assistant.client.utils.conditional
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PlayersPager(
    modifier: Modifier = Modifier,
    playerPagerState: PagerState,
    playersState: HomeScreenViewModel.PlayersState.Data,
    serverUrl: String?,
    simplePlayerAction: (String, PlayerAction) -> Unit,
    playerAction: (PlayerData, PlayerAction) -> Unit,
    onPlayersRefreshClick: () -> Unit,
    onFavoriteClick: (AppMediaItem) -> Unit,
    showQueue: Boolean,
    isQueueExpanded: Boolean,
    onQueueExpandedSwitch: () -> Unit,
    onGoToLibrary: () -> Unit,
    onItemMoved: ((Int) -> Unit)?,
    queueAction: (QueueAction) -> Unit,
    settingsAction: (String) -> Unit,
    dspSettingsAction: (String) -> Unit,
) {
    // Extract playerData list to ensure proper recomposition
    val playerDataList = playersState.playerData
    val coroutineScope = rememberCoroutineScope()
    fun moveToPlayer(playerId: String) {
        val targetIndex =
            playerDataList.indexOfFirst { it.player.id == playerId }
        if (targetIndex != -1) {
            coroutineScope.launch {
                playerPagerState.animateScrollToPage(targetIndex)
            }
        }
    }


    var showSelectDialog by remember { mutableStateOf(false) }
    if (showSelectDialog) {
        SelectPlayerDialog(
            selectedPlayer = playerDataList[playersState.selectedPlayerIndex!!],
            players = playerDataList,
            onDismissRequest = { showSelectDialog = false },
            onMoveToPlayer = { moveToPlayer(it) },
            groupAction = simplePlayerAction
        )
    }

    Column(modifier = modifier) {
        if (playerDataList.size > 1) {
            HorizontalPagerIndicator(
                modifier = Modifier.padding(top = 8.dp),
                pagerState = playerPagerState,
                onItemMoved = onItemMoved,
            )
        }

        HorizontalPager(
            modifier = Modifier
                .wrapContentHeight()
                .padding(top = 8.dp),
            state = playerPagerState,
            key = { page -> playerDataList.getOrNull(page)?.player?.id ?: page }
        ) { page ->
            val player = playerDataList.getOrNull(page) ?: return@HorizontalPager
            val isLocalPlayer = player.playerId == playersState.localPlayerId

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
                                MaterialTheme.colorScheme.primaryContainer
                            )
                        )
                    }
                )
            ) {
                if (showQueue) {
                    PlayerNameRow(
                        playerName = player.player.displayName,
                        hasNoChildren = player.groupChildren.isEmpty(),
                        hasNoBoundChildren = player.groupChildren.none { it.isBound },
                        isLocalPlayer = isLocalPlayer,
                        sendspinState = if (isLocalPlayer) playersState.sendspinState else null,
                        onShowGroup = { showSelectDialog = true }
                    )
                }

                AnimatedVisibility(
                    visible = isQueueExpanded.takeIf { showQueue } != false,
                    enter = fadeIn(tween(300)) + expandVertically(tween(300)),
                    exit = fadeOut(tween(200)) + shrinkVertically(tween(300))
                ) {

                    Box(
                        modifier = Modifier
                            .padding(top = 8.dp)
                            .fillMaxWidth()
                            .wrapContentSize()
                            .conditional(
                                showQueue,
                                { clickable { onQueueExpandedSwitch() } }
                            )
                    ) {
                        CompactPlayerItem(
                            item = player,
                            serverUrl = serverUrl,
                            playerAction = playerAction,
                            playerDataList = playerDataList,
                            playersState = playersState,
                            onMoveToPlayer = { moveToPlayer(it) },
                            groupAction = simplePlayerAction
                        )
                    }
                }

                Column(
                    modifier = Modifier
                        .conditional(
                            condition = isQueueExpanded.takeIf { showQueue } == false,
                            ifTrue = { weight(1f) },
                            ifFalse = { wrapContentHeight() }
                        )
                ) {
                    AnimatedVisibility(
                        visible = isQueueExpanded.takeIf { showQueue } == false,
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
                    showQueue
                    && player.player.canSetVolume
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
                                    Icons.AutoMirrored.Filled.VolumeMute
                                else
                                    Icons.AutoMirrored.Filled.VolumeUp,
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

                player.queue.takeIf { showQueue }?.let { queue ->
                    CollapsibleQueue(
                        modifier = Modifier
                            .conditional(
                                condition = isQueueExpanded,
                                ifTrue = { weight(1f) },
                                ifFalse = { wrapContentHeight() }
                            ),
                        queue = queue,
                        isQueueExpanded = isQueueExpanded,
                        onQueueExpandedSwitch = { onQueueExpandedSwitch() },
                        onGoToLibrary = onGoToLibrary,
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

@Composable
private fun PlayerNameRow(
    playerName: String,
    hasNoChildren: Boolean,
    hasNoBoundChildren: Boolean,
    isLocalPlayer: Boolean,
    sendspinState: SendspinState?,
    onShowGroup: () -> Unit
) {
    val dotColor = sendspinState.toDotColor().takeIf { isLocalPlayer }

    Box(
        modifier = Modifier.fillMaxWidth()
    ) {
        val playerName: @Composable (Color) -> Unit = { textColor ->
            Row(
                modifier = Modifier.align(Alignment.Center),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = playerName + (if (isLocalPlayer) " (local)" else ""),
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    fontWeight = FontWeight.Medium,
                    color = textColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                dotColor?.let {
                    Spacer(modifier = Modifier.width(4.dp))
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(it, CircleShape)
                    )
                }
            }
        }

        when {
            hasNoChildren ->
                Box(
                    modifier = Modifier.height(48.dp).align(Alignment.Center)
                ) {
                    playerName(MaterialTheme.colorScheme.onSurface)
                }

            hasNoBoundChildren ->
                OutlinedButton(
                    modifier = Modifier.align(Alignment.Center),
                    enabled = true,
                    onClick = { onShowGroup() }
                ) {
                    playerName(MaterialTheme.colorScheme.onSurface)
                }

            else ->
                Button(
                    modifier = Modifier.align(Alignment.Center),
                    enabled = true,
                    onClick = { onShowGroup() }) {
                    playerName(MaterialTheme.colorScheme.onPrimary)
                }
        }

//                    // Overflow menu on the right TODO re-enable when settings are fixed in MA
//                    OverflowMenuThreeDots(
//                        modifier = Modifier.align(Alignment.CenterEnd)
//                            .padding(end = 8.dp),
//                        options = listOf(
//                            OverflowMenuOption(
//                                title = "Settings",
//                                onClick = { settingsAction(player.player.id) }
//                            ),
//                            OverflowMenuOption(
//                                title = "DSP settings",
//                                onClick = { dspSettingsAction(player.player.id) }
//                            ),
//                        )
//                    )
    }
}

private fun SendspinState?.toDotColor(): Color = when (this) {
    is SendspinState.Synchronized, is SendspinState.Ready,
    is SendspinState.Buffering -> Color(0xFF4CAF50) // Green
    is SendspinState.Connecting, is SendspinState.Authenticating,
    is SendspinState.Handshaking, is SendspinState.Reconnecting -> Color(0xFFFF9800) // Orange
    is SendspinState.Error -> Color(0xFFF44336) // Red
    is SendspinState.Idle, null -> Color(0xFFBDBDBD) // Light gray
}
