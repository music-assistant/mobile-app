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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeMute
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import io.music_assistant.client.ui.compose.common.action.PlayerAction
import io.music_assistant.client.ui.compose.common.action.QueueAction
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
    var groupDialogPlayerId by remember { mutableStateOf<String?>(null) }

    fun moveToPlayer(playerId: String) {
        val targetIndex =
            playerDataList.indexOfFirst { it.player.id == playerId }
        if (targetIndex != -1) {
            coroutineScope.launch {
                playerPagerState.animateScrollToPage(targetIndex)
            }
        }
    }

    Column(modifier = modifier) {
        PlayersTopBar(
            playerDataList = playerDataList,
            playersState = playersState,
            playerPagerState = playerPagerState,
            onPlayersRefreshClick = onPlayersRefreshClick,
            onItemMoved = onItemMoved
        ) { moveToPlayer(it) }

        HorizontalPager(
            modifier = Modifier.wrapContentHeight(),
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

                PlayerNameRow(
                    playerName = player.player.displayName,
                    hasNoChildren = player.groupChildren.isEmpty(),
                    hasNoBoundChildren = player.groupChildren.none { it.isBound },
                    isLocalPlayer = isLocalPlayer,
                    onShowGroup = { groupDialogPlayerId = player.player.id }
                )

                AnimatedVisibility(
                    visible = isQueueExpanded.takeIf { showQueue } != false,
                    enter = fadeIn(tween(300)) + expandVertically(tween(300)),
                    exit = fadeOut(tween(200)) + shrinkVertically(tween(300))
                ) {

                    Box(
                        modifier = Modifier
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

        groupDialogPlayerId?.let { playerId ->
            playerDataList.find { it.player.id == playerId }?.let { player ->
                GroupDialog(
                    item = player,
                    simplePlayerAction = simplePlayerAction,
                    onDismiss = { groupDialogPlayerId = null }
                )
            }
        }
    }
}

@Composable
private fun GroupDialog(
    item: PlayerData,
    simplePlayerAction: (String, PlayerAction) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Group settings") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth()
            ) {
                // Non-scrollable Done button at top
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                ) {
                    Text("Done")
                }

                // Scrollable list of players
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 400.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Current player at the very top
                    item {
                        GroupPlayerItem(
                            playerId = item.player.id,
                            playerName = item.player.name,
                            isGroup = item.player.isGroup,
                            volume = if (item.player.isGroup) item.player.groupVolume else item.player.volumeLevel,
                            isMuted = item.player.volumeMuted.takeIf { item.player.canMute },
                            simplePlayerAction = simplePlayerAction,
                        )
                    }

                    // Bound players
                    val boundChildren = item.groupChildren.filter { it.isBound }
                    items(boundChildren, key = { "${it.id}_${it.volume}" }) { child ->
                        GroupPlayerItem(
                            playerId = child.id,
                            playerName = child.name,
                            volume = child.volume,
                            isMuted = child.isMuted,
                            simplePlayerAction = simplePlayerAction,
                            bindItem = child,
                        )
                    }

                    // Unbound players
                    val unboundChildren = item.groupChildren.filter { !it.isBound }
                    items(unboundChildren, key = { it.id }) { child ->
                        GroupPlayerItem(
                            playerId = child.id,
                            playerName = child.name,
                            volume = child.volume,
                            isMuted = child.isMuted,
                            simplePlayerAction = simplePlayerAction,
                            bindItem = child,
                        )
                    }
                }
            }
        },
        confirmButton = {}
    )
}

/**
 * Group player item with name and volume
 */
@Composable
private fun GroupPlayerItem(
    playerId: String,
    playerName: String,
    isGroup: Boolean = false,
    volume: Float?,
    isMuted: Boolean?,
    simplePlayerAction: (String, PlayerAction) -> Unit,
    bindItem: PlayerData.Bind? = null,
) {
    var currentVolume by remember(volume) {
        mutableStateOf(volume ?: 0f)
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy((-4).dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                modifier = Modifier.alpha(if (bindItem?.isBound != false) 1f else 0.4f).weight(1f),
                text = playerName,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )

            // Show button only for non-current players (when bindItem is provided)
            bindItem?.let { bind ->
                val itemId = listOf(playerId)
                IconButton(
                    onClick = {
                        simplePlayerAction(
                            bind.parentId,
                            PlayerAction.GroupManage(
                                toAdd = itemId.takeIf { !bind.isBound },
                                toRemove = itemId.takeIf { bind.isBound }
                            )
                        )
                    }
                ) {
                    Icon(
                        imageVector = if (bindItem.isBound) Icons.Default.Remove else Icons.Default.Add,
                        contentDescription = if (bindItem.isBound) "Remove from group" else "Add to group",
                        tint = if (bindItem.isBound)
                            MaterialTheme.colorScheme.error
                        else
                            MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        val volumeEnabled = volume != null && bindItem?.isBound != false
        Row {
            isMuted?.let {
                IconButton(onClick = {
                    simplePlayerAction(
                        playerId,
                        PlayerAction.ToggleMute(isMuted)
                    )
                }, enabled = volumeEnabled) {
                    Icon(
                        imageVector = if (isMuted) Icons.AutoMirrored.Filled.VolumeMute else Icons.AutoMirrored.Filled.VolumeUp,
                        contentDescription = if (isMuted) "Unmute" else "Mute"
                    )
                }
            }
            Slider(
                modifier = Modifier.fillMaxWidth().alpha(if (volumeEnabled) 1f else 0.4f),
                value = currentVolume,
                valueRange = 0f..100f,
                enabled = volumeEnabled,
                onValueChange = {
                    currentVolume = it
                },
                onValueChangeFinished = {
                    simplePlayerAction(
                        playerId,
                        if (isGroup) PlayerAction.GroupVolumeSet(currentVolume.toDouble())
                        else PlayerAction.VolumeSet(currentVolume.toDouble())
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
    }
}

@Composable
private fun PlayerNameRow(
    playerName: String,
    hasNoChildren: Boolean,
    hasNoBoundChildren: Boolean,
    isLocalPlayer: Boolean,
    onShowGroup: () -> Unit
) {
    Box(
        modifier = Modifier.fillMaxWidth()
    ) {
        val playerName: @Composable (Color) -> Unit = { textColor ->
            Text(
                modifier = Modifier.align(Alignment.Center),
                text = playerName + (if (isLocalPlayer) " (local)" else ""),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                fontWeight = FontWeight.Medium,
                color = textColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
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
