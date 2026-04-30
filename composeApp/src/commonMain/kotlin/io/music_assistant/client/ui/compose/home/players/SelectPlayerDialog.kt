// Compose layout values (sizes, alphas, animation durations) are visual design tokens.
@file:Suppress("MagicNumber")

package io.music_assistant.client.ui.compose.home.players

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.Speaker
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import compose.icons.TablerIcons
import compose.icons.tablericons.GripVertical
import io.music_assistant.client.data.model.client.PlayerData
import io.music_assistant.client.data.model.client.PlayerDataFixtures
import io.music_assistant.client.ui.compose.common.action.PlayerAction
import io.music_assistant.client.ui.compose.common.icons.NowPlayingIcon
import io.music_assistant.client.ui.compose.common.icons.SpeakerMultipleIcon
import io.music_assistant.client.ui.compose.common.icons.VolumeIcon
import io.music_assistant.client.ui.compose.common.icons.VolumeMutedIcon
import musicassistantclient.composeapp.generated.resources.Res
import musicassistantclient.composeapp.generated.resources.cd_add_to_group
import musicassistantclient.composeapp.generated.resources.cd_mute
import musicassistantclient.composeapp.generated.resources.cd_remove_from_group
import musicassistantclient.composeapp.generated.resources.cd_unmute
import musicassistantclient.composeapp.generated.resources.common_done
import musicassistantclient.composeapp.generated.resources.players_group_settings
import musicassistantclient.composeapp.generated.resources.players_title
import org.jetbrains.compose.resources.stringResource
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SelectPlayerDialog(
    selectedPlayer: PlayerData,
    players: List<PlayerData>,
    onDismissRequest: () -> Unit = {},
    onMoveToPlayer: (String) -> Unit = {},
    onReorder: (List<String>) -> Unit = {},
) {
    Dialog(onDismissRequest = onDismissRequest) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
        ) {
            Box(modifier = Modifier.padding(vertical = 16.dp)) {
                Column {
                    Text(
                        stringResource(Res.string.players_title),
                        style = MaterialTheme.typography.headlineSmall,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )

                    Box(modifier = Modifier.padding(top = 16.dp)) {
                        PlayerSelection(
                            players,
                            selectedPlayer,
                            onDismissRequest,
                            onMoveToPlayer,
                            onReorder,
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun GroupSettingsDialog(
    player: PlayerData,
    onDismissRequest: () -> Unit = {},
    groupAction: (String, PlayerAction) -> Unit = { _, _ -> },
    localPlayerId: String? = null,
    onAdjustPlaybackDelay: ((Int) -> Unit)? = null,
) {
    Dialog(onDismissRequest = onDismissRequest) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
        ) {
            Box(modifier = Modifier.padding(vertical = 16.dp)) {
                Column {
                    Text(
                        stringResource(Res.string.players_group_settings),
                        style = MaterialTheme.typography.headlineSmall,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )

                    Box(modifier = Modifier.padding(top = 16.dp)) {
                        GroupSettings(
                            item = player,
                            onDismiss = onDismissRequest,
                            playerAction = groupAction,
                            localPlayerId = localPlayerId,
                            onAdjustPlaybackDelay = onAdjustPlaybackDelay,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PlayerSelection(
    players: List<PlayerData>,
    selectedPlayer: PlayerData,
    onDismissRequest: () -> Unit,
    onSelectPlayer: (String) -> Unit,
    onReorder: (List<String>) -> Unit,
) {
    val plateShape = RoundedCornerShape(12.dp)

    var internalPlayers by remember { mutableStateOf(players) }
    var dragEndIndex by remember { mutableStateOf<Int?>(null) }
    val listState = rememberLazyListState()
    val reorderableLazyListState =
        rememberReorderableLazyListState(listState) { from, to ->
            internalPlayers = internalPlayers.toMutableList().apply {
                add(to.index, removeAt(from.index))
            }
            dragEndIndex = to.index
        }

    // Upstream emits frequently while a player is playing. Adopting those emits
    // mid-drag wipes the user's reorder and the list jumps. Sync upstream into
    // internal state only when no drag is in progress; refresh content in place
    // when the membership matches, so play/stop never reshuffles the order.
    LaunchedEffect(players, reorderableLazyListState.isAnyItemDragging) {
        if (reorderableLazyListState.isAnyItemDragging) return@LaunchedEffect
        val byId = players.associateBy { it.player.id }
        internalPlayers = if (byId.keys == internalPlayers.mapTo(mutableSetOf()) { it.player.id }) {
            internalPlayers.map { byId.getValue(it.player.id) }
        } else {
            players
        }
    }

    LazyColumn(
        modifier = Modifier
            .testTag("PlayersList")
            .selectableGroup()
            .heightIn(max = MAX_LIST_HEIGHT),
        state = listState,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        items(
            items = internalPlayers,
            key = { item -> item.player.id },
        ) { item ->
            val selected = item.player.id == selectedPlayer.player.id
            val borderColor = if (selected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.outlineVariant
            }
            val backgroundColor = if (selected) {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
            } else {
                Color.Transparent
            }

            ReorderableItem(state = reorderableLazyListState, key = item.player.id) {
                Row(
                    modifier = Modifier
                        .padding(horizontal = 16.dp)
                        .fillMaxWidth()
                        .clip(plateShape)
                        .background(backgroundColor)
                        .border(1.dp, borderColor, plateShape)
                        .selectable(
                            selected = selected,
                            onClick = {
                                onDismissRequest()
                                onSelectPlayer(item.player.id)
                            },
                            role = Role.RadioButton,
                        )
                        .padding(horizontal = 16.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val playerIcon = when {
                        item.isLocal -> Icons.Default.Smartphone
                        item.player.isGroup -> SpeakerMultipleIcon
                        else -> Icons.Default.Speaker
                    }
                    Icon(
                        imageVector = playerIcon,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                    )
                    Text(
                        text = item.player.name,
                        modifier = Modifier
                            .padding(start = 8.dp)
                            .weight(1f),
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    item.player.suffix?.let { suffix ->
                        Text(
                            text = suffix,
                            modifier = Modifier.padding(start = 4.dp).alpha(0.6f),
                            style = MaterialTheme.typography.bodyLarge,
                            maxLines = 1,
                        )
                    }
                    if (item.player.isPlaying || item.player.isAnnouncing) {
                        NowPlayingIcon(
                            modifier = Modifier.padding(start = 8.dp),
                            size = 12.dp,
                            color = if (item.player.isAnnouncing) {
                                Color(0xFFFF9800)
                            } else {
                                Color(0xFF2196F3)
                            },
                        )
                    }
                    Icon(
                        modifier = Modifier
                            .padding(start = 8.dp)
                            .draggableHandle(
                                onDragStopped = {
                                    dragEndIndex?.let {
                                        onReorder(internalPlayers.map { p -> p.player.id })
                                    }
                                },
                            )
                            .size(16.dp),
                        imageVector = TablerIcons.GripVertical,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.secondary,
                    )
                }
            }
        }
    }
}

@Composable
fun GroupSettings(
    item: PlayerData,
    onDismiss: () -> Unit,
    playerAction: (String, PlayerAction) -> Unit,
    localPlayerId: String? = null,
    onAdjustPlaybackDelay: ((Int) -> Unit)? = null,
) {
    // Is the local (Sendspin) player joined to anything visible in this dialog?
    // True when local is the pivot of a group with at least one bound child, OR
    // when local appears as a bound child under a non-local pivot.
    val localBoundToGroup = localPlayerId != null && (
            (item.player.id == localPlayerId && item.childrenBinds.any { it.isBound }) ||
                    item.childrenBinds.any { it.id == localPlayerId && it.isBound }
            )

    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = MAX_LIST_HEIGHT)
                .weight(1f, false),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // Pivot first, then children (bound before unbound).
            item {
                GroupPlayerItem(
                    playerId = item.player.id,
                    playerName = item.player.name,
                    useGroupVolume = item.player.isGroup || item.player.isGrouped,
                    volume =
                        if (item.player.isGroup || item.player.isGrouped) {
                            item.player.groupVolume
                        } else {
                            item.player.volumeLevel
                        },
                    isVolumeEnabled = item.player.isVolumeSliderAccessible,
                    isMuted = item.player.volumeMuted.takeIf { item.player.canMute },
                    simplePlayerAction = playerAction,
                    localPlayerId = localPlayerId,
                    localBoundToGroup = localBoundToGroup,
                    onAdjustPlaybackDelay = onAdjustPlaybackDelay,
                )
            }

            val sortedChildren = item.childrenBinds.sortedByDescending { it.isBound }
            items(sortedChildren, key = { "${it.id}_${it.volume}" }) { child ->
                GroupPlayerItem(
                    playerId = child.id,
                    playerName = child.name,
                    volume = child.volume,
                    isVolumeEnabled = child.volumeSliderAccessible,
                    isMuted = child.isMuted,
                    simplePlayerAction = playerAction,
                    childBindItem = child,
                    localPlayerId = localPlayerId,
                    localBoundToGroup = localBoundToGroup,
                    onAdjustPlaybackDelay = onAdjustPlaybackDelay,
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
        ) {
            TextButton(onClick = onDismiss) {
                Text(stringResource(Res.string.common_done))
            }
        }
    }
}

/**
 * Group player item with name and (volume | playback-delay) row.
 *
 * For the local (Sendspin) player the volume/mute slider is replaced with a
 * row of playback-delay delta buttons — volume has no meaning for the
 * phone-speaker player. The row stays visible even when the local player
 * isn't joined to the group; the name dims and the delta buttons disable.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GroupPlayerItem(
    playerId: String,
    playerName: String,
    useGroupVolume: Boolean = false,
    volume: Float?,
    isVolumeEnabled: Boolean,
    isMuted: Boolean?,
    simplePlayerAction: (String, PlayerAction) -> Unit,
    childBindItem: PlayerData.ChildBind? = null,
    localPlayerId: String? = null,
    localBoundToGroup: Boolean = false,
    onAdjustPlaybackDelay: ((Int) -> Unit)? = null,
) {
    val isLocalRow = localPlayerId != null && playerId == localPlayerId
    // "In the group" means: the local row is bound to the group, or any other
    // child bind is bound, or this row is the pivot (no childBindItem).
    val inGroup = if (isLocalRow) localBoundToGroup else childBindItem?.isBound ?: true

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy((-4).dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                // Dim only the name when out-of-group. +/- icon and Material3
                // disabled states carry their own visuals and must stay opaque.
                modifier = Modifier
                    .weight(1f)
                    .alpha(if (inGroup) 1f else 0.4f),
                text = playerName,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
            )

            // Join/leave button is only shown for child-bind rows (pivot has none).
            val itemId = listOf(playerId)
            IconButton(
                modifier = Modifier.alpha(if (childBindItem == null) 0f else 1f),
                enabled = childBindItem?.isManageable == true,
                onClick = {
                    childBindItem?.let { bind ->
                        simplePlayerAction(
                            bind.parentId,
                            PlayerAction.GroupManage(
                                toAdd = itemId.takeIf { !bind.isBound },
                                toRemove = itemId.takeIf { bind.isBound },
                            ),
                        )
                    }
                },
            ) {
                childBindItem?.let { bind ->
                    Icon(
                        modifier = Modifier.alpha(if (bind.isManageable) 1f else 0.4f),
                        imageVector = if (bind.isBound) Icons.Default.Remove else Icons.Default.Add,
                        contentDescription = if (bind.isBound) {
                            stringResource(
                                Res.string.cd_remove_from_group,
                            )
                        } else {
                            stringResource(Res.string.cd_add_to_group)
                        },
                        tint = if (bind.isBound) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.primary
                        },
                    )
                }
            }
        }

        if (isLocalRow) {
            PlaybackDelayButtons(
                enabled = inGroup && onAdjustPlaybackDelay != null,
                onAdjust = onAdjustPlaybackDelay,
            )
        } else {
            VolumeRow(
                playerId = playerId,
                useGroupVolume = useGroupVolume,
                volume = volume,
                isMuted = isMuted,
                enabled = isVolumeEnabled && volume != null && inGroup,
                simplePlayerAction = simplePlayerAction,
            )
        }
    }
}

@Composable
private fun PlaybackDelayButtons(
    enabled: Boolean,
    onAdjust: ((Int) -> Unit)?,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        listOf(
            "-100ms" to -100,
            "-10ms" to -10,
            "-1ms" to -1,
            "+1ms" to 1,
            "+10ms" to 10,
            "+100ms" to 100,
        ).forEach { (label, delta) ->
            androidx.compose.material3.OutlinedButton(
                onClick = { onAdjust?.invoke(delta) },
                enabled = enabled,
                modifier = Modifier.weight(1f),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    horizontal = 0.dp,
                    vertical = 4.dp,
                ),
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VolumeRow(
    playerId: String,
    useGroupVolume: Boolean,
    volume: Float?,
    isMuted: Boolean?,
    enabled: Boolean,
    simplePlayerAction: (String, PlayerAction) -> Unit,
) {
    var currentVolume by remember(volume) { mutableStateOf(volume ?: 0f) }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        isMuted?.let {
            Icon(
                modifier = Modifier
                    .size(24.dp)
                    .alpha(if (enabled) 1F else 0.5f)
                    .clickable(enabled = enabled) {
                        simplePlayerAction(playerId, PlayerAction.ToggleMute(isMuted))
                    },
                imageVector = if (isMuted) {
                    VolumeMutedIcon
                } else {
                    VolumeIcon
                },
                contentDescription = if (isMuted) {
                    stringResource(
                        Res.string.cd_unmute,
                    )
                } else {
                    stringResource(Res.string.cd_mute)
                },
            )
        }
        Slider(
            modifier = Modifier.weight(1f).alpha(if (enabled) 1f else 0.4f),
            value = currentVolume,
            valueRange = 0f..100f,
            enabled = enabled,
            onValueChange = { currentVolume = it },
            onValueChangeFinished = {
                simplePlayerAction(
                    playerId,
                    if (useGroupVolume) {
                        PlayerAction.GroupVolumeSet(currentVolume.toDouble())
                    } else {
                        PlayerAction.VolumeSet(currentVolume.toDouble())
                    },
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
                    modifier = Modifier.height(4.dp),
                )
            },
        )
        Text(
            modifier = Modifier.width(24.dp).alpha(if (enabled) 1f else 0.4f),
            text = currentVolume.roundToInt().toString(),
            textAlign = TextAlign.End,
            style = MaterialTheme.typography.labelMedium,
        )
    }
}

@Preview
@Composable
private fun PreviewSelectPlayerDialog() {
    val selectedPlayer = PlayerDataFixtures.playerData()
    SelectPlayerDialog(
        selectedPlayer = selectedPlayer,
        players = listOf(selectedPlayer, PlayerDataFixtures.playerData()),
        onDismissRequest = {},
    )
}

@Preview
@Composable
private fun PreviewSelectPlayerDialogLongList() {
    val players = 0.until(25).map {
        PlayerDataFixtures.playerData()
    }

    SelectPlayerDialog(
        selectedPlayer = players[0],
        players = players,
        onDismissRequest = {},
    )
}

@Preview
@Composable
private fun PreviewGroupSettingDialog() {
    val selectedPlayer = PlayerDataFixtures.playerData()
    GroupSettingsDialog(
        player = selectedPlayer,
        onDismissRequest = {},
    )
}

@Preview
@Composable
private fun PreviewGroupSettingDialogLongList() {
    val selectedPlayer = PlayerDataFixtures.playerData(
        groupChildren = 0.until(25).map {
            PlayerDataFixtures.bind()
        },
    )

    GroupSettingsDialog(
        player = selectedPlayer,
        onDismissRequest = {},
    )
}

private val MAX_LIST_HEIGHT = 400.dp
