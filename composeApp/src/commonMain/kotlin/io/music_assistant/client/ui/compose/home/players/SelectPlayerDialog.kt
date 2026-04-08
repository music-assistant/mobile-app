package io.music_assistant.client.ui.compose.home.players

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import io.music_assistant.client.data.model.client.PlayerData
import io.music_assistant.client.data.model.client.PlayerDataFixtures
import io.music_assistant.client.ui.compose.common.action.PlayerAction
import io.music_assistant.client.ui.compose.common.icons.VolumeIcon
import io.music_assistant.client.ui.compose.common.icons.VolumeMutedIcon

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SelectPlayerDialog(
    selectedPlayer: PlayerData,
    players: List<PlayerData>,
    onDismissRequest: () -> Unit = {},
    onMoveToPlayer: (String) -> Unit = {},
) {
    Dialog(onDismissRequest = onDismissRequest) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp)
        ) {
            Box(modifier = Modifier.padding(vertical = 16.dp)) {
                Column {
                    Text(
                        "Players",
                        style = MaterialTheme.typography.headlineSmall,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )

                    Box(modifier = Modifier.padding(top = 16.dp)) {
                        PlayerSelection(
                            players,
                            selectedPlayer,
                            onDismissRequest,
                            onMoveToPlayer
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
) {
    Dialog(onDismissRequest = onDismissRequest) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp)
        ) {
            Box(modifier = Modifier.padding(vertical = 16.dp)) {
                Column {
                    Text(
                        "Group settings",
                        style = MaterialTheme.typography.headlineSmall,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )

                    Box(modifier = Modifier.padding(top = 16.dp)) {
                        GroupSettings(
                            item = player,
                            onDismiss = onDismissRequest,
                            playerAction = groupAction
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
) {
    LazyColumn(
        modifier = Modifier
            .testTag("PlayersList")
            .selectableGroup()
            .heightIn(max = MAX_LIST_HEIGHT)
    ) {
        players.forEach {
            item {
                val selected = it.player.id == selectedPlayer.player.id

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .selectable(
                            selected = selected,
                            onClick = {
                                onDismissRequest()
                                onSelectPlayer(it.player.id)
                            },
                            role = Role.RadioButton
                        ).padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = selected,
                        onClick = null
                    )

                    Text(
                        text = it.player.name,
                        modifier = Modifier.padding(start = 16.dp),
                        style = MaterialTheme.typography.bodyLarge
                    )
                    it.player.suffix?.let { suffix ->
                        Text(
                            text = suffix,
                            modifier = Modifier.padding(start = 4.dp).alpha(0.6f),
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun GroupSettings(
    item: PlayerData,
    onDismiss: () -> Unit,
    playerAction: (String, PlayerAction) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
    ) {
        // Scrollable list of players
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = MAX_LIST_HEIGHT)
                .weight(1f, false),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Current player at the very top
            item {
                GroupPlayerItem(
                    playerId = item.player.id,
                    playerName = item.player.name,
                    useGroupVolume = item.player.isGroup || item.player.isGrouped,
                    volume =
                        if (item.player.isGroup || item.player.isGrouped) item.player.groupVolume
                        else item.player.volumeLevel,
                    isVolumeEnabled = item.player.isVolumeSliderAccessible,
                    isMuted = item.player.volumeMuted.takeIf { item.player.canMute },
                    simplePlayerAction = playerAction,
                )
            }

            // Bound players
            val boundChildren = item.groupChildren.filter { it.isBound }
            items(boundChildren, key = { "${it.id}_${it.volume}" }) { child ->
                GroupPlayerItem(
                    playerId = child.id,
                    playerName = child.name,
                    volume = child.volume,
                    isVolumeEnabled = child.volumeSliderAccessible,
                    isMuted = child.isMuted,
                    simplePlayerAction = playerAction,
                    bindItem = child,
                )
            }

            // Unbound players
            val unboundChildren = item.groupChildren.filter { !it.isBound }
            items(unboundChildren, key = { "${it.id}_${it.volume}" }) { child ->
                GroupPlayerItem(
                    playerId = child.id,
                    playerName = child.name,
                    volume = child.volume,
                    isVolumeEnabled = child.volumeSliderAccessible,
                    isMuted = child.isMuted,
                    simplePlayerAction = playerAction,
                    bindItem = child,
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            TextButton(onClick = onDismiss) {
                Text("Done")
            }
        }
    }
}

/**
 * Group player item with name and volume
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
                    enabled = bindItem.isManageable,
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
                        modifier = Modifier.alpha(if (bindItem.isManageable) 1f else 0.4f),
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

        val volumeEnabled = isVolumeEnabled && volume != null && bindItem?.isBound != false
        Row {
            isMuted?.let {
                IconButton(onClick = {
                    simplePlayerAction(
                        playerId,
                        PlayerAction.ToggleMute(isMuted)
                    )
                }, enabled = volumeEnabled) {
                    Icon(
                        imageVector = if (isMuted) VolumeMutedIcon else VolumeIcon,
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
                        if (useGroupVolume) PlayerAction.GroupVolumeSet(currentVolume.toDouble())
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

@Preview
@Composable
private fun PreviewSelectPlayerDialog() {
    val selectedPlayer = PlayerDataFixtures.playerData()
    SelectPlayerDialog(
        selectedPlayer = selectedPlayer,
        players = listOf(selectedPlayer, PlayerDataFixtures.playerData()),
        onDismissRequest = {}
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
        onDismissRequest = {}
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
        }
    )

    GroupSettingsDialog(
        player = selectedPlayer,
        onDismissRequest = {},
    )
}

private val MAX_LIST_HEIGHT = 400.dp