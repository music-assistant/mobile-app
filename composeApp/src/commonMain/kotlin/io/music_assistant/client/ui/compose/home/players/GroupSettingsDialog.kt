package io.music_assistant.client.ui.compose.home.players

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import io.music_assistant.client.data.model.client.PlayerData
import io.music_assistant.client.data.model.client.PlayerDataFixtures
import io.music_assistant.client.ui.HUNDRED
import io.music_assistant.client.ui.MAX_DIALOG_HEIGHT
import io.music_assistant.client.ui.ONE
import io.music_assistant.client.ui.TEN
import io.music_assistant.client.ui.alphaOn
import io.music_assistant.client.ui.compose.common.action.PlayerAction
import io.music_assistant.client.ui.compose.common.icons.VolumeIcon
import io.music_assistant.client.ui.compose.common.icons.VolumeMutedIcon
import musicassistantclient.composeapp.generated.resources.Res
import musicassistantclient.composeapp.generated.resources.cd_add_to_group
import musicassistantclient.composeapp.generated.resources.cd_mute
import musicassistantclient.composeapp.generated.resources.cd_remove_from_group
import musicassistantclient.composeapp.generated.resources.cd_unmute
import musicassistantclient.composeapp.generated.resources.common_done
import musicassistantclient.composeapp.generated.resources.players_group_settings
import musicassistantclient.composeapp.generated.resources.players_group_volume
import org.jetbrains.compose.resources.stringResource
import kotlin.math.roundToInt

@Composable
fun GroupSettingsDialog(
    player: PlayerData,
    onDismissRequest: () -> Unit = {},
    groupAction: (String, PlayerAction) -> Unit = { _, _ -> },
    localPlayerId: String? = null,
    onAdjustPlaybackDelay: ((Int) -> Unit)? = null,
) {
    // TODO generalize Dialogs across the app
    Dialog(onDismissRequest = onDismissRequest) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
        ) {
            Box(modifier = Modifier.padding(all = 8.dp)) {
                Column {
                    Text(
                        modifier = Modifier.padding(all = 8.dp),
                        text = stringResource(Res.string.players_group_settings),
                        style = MaterialTheme.typography.headlineSmall,
                    )

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

@Composable
private fun GroupSettings(
    item: PlayerData,
    onDismiss: () -> Unit,
    playerAction: (String, PlayerAction) -> Unit,
    localPlayerId: String? = null,
    onAdjustPlaybackDelay: ((Int) -> Unit)? = null,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = MAX_DIALOG_HEIGHT)
                .weight(1f, false),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (item.player.isGrouped) {
                item(key = item.player.groupVolume) {
                    GroupPlayerItemCard {
                        GroupItemTitle(
                            isEnabled = item.player.groupVolume != null,
                            name = stringResource(Res.string.players_group_volume, item.player.name),
                        )
                        VolumeRow(
                            volume = item.player.groupVolume,
                            isMuted = item.player.groupVolumeMuted,
                            enabled = item.player.groupVolume != null,
                            onMuteToggle = {
                                playerAction(
                                    item.player.id,
                                    PlayerAction.GroupToggleMute(isMutedNow = item.player.groupVolumeMuted),
                                )
                            },
                            onVolumeSet = { level ->
                                playerAction(
                                    item.player.id,
                                    PlayerAction.GroupVolumeSet(level.toDouble()),
                                )
                            },
                        )
                    }
                }
            }
            // Pivot first, then children (bound before unbound).
            item {
                GroupPlayerItemCard(
                    background = MaterialTheme.colorScheme.surfaceContainerLow,
                ) {
                    GroupItemTitle(
                        isEnabled = true,
                        name = item.player.name,
                        childBindItem = null,
                        onGroupActionClick = null,
                    )
                    if (item.player.isGroup) {
                        item.player.groupVolume?.let { playerVolumeLevel ->
                            VolumeRow(
                                volume = playerVolumeLevel,
                                isMuted = item.player.groupVolumeMuted.takeIf { item.player.canMute },
                                enabled = item.player.isVolumeSliderAccessible,
                                onMuteToggle = {
                                    playerAction(
                                        item.player.id,
                                        PlayerAction.GroupToggleMute(isMutedNow = item.player.groupVolumeMuted),
                                    )
                                },
                                onVolumeSet = { level ->
                                    playerAction(
                                        item.player.id,
                                        PlayerAction.GroupVolumeSet(level.toDouble()),
                                    )
                                },
                            )
                        }
                    } else {
                        item.player.volumeLevel?.let { playerVolumeLevel ->
                            VolumeRow(
                                volume = playerVolumeLevel,
                                isMuted = item.player.volumeMuted.takeIf { item.player.canMute },
                                enabled = item.player.isVolumeSliderAccessible,
                                onMuteToggle = {
                                    playerAction(
                                        item.player.id,
                                        PlayerAction.ToggleMute(isMutedNow = item.player.volumeMuted),
                                    )
                                },
                                onVolumeSet = { level ->
                                    playerAction(
                                        item.player.id,
                                        PlayerAction.VolumeSet(level.toDouble()),
                                    )
                                },
                            )
                        }
                    }
                }
            }

            val sortedChildren = item.childrenBinds.sortedByDescending { it.isBound }
            items(sortedChildren, key = { "${it.id}_${it.volume}" }) { bindInfo ->
                GroupPlayerItemCard(
                    background = if (bindInfo.isBound) MaterialTheme.colorScheme.surfaceContainerHigh else Color.Transparent,
                ) {
                    GroupItemTitle(
                        isEnabled = bindInfo.isBound,
                        name = bindInfo.name,
                        childBindItem = bindInfo,
                        onGroupActionClick = {
                            val playerIdList = listOf(bindInfo.id)
                            playerAction(
                                bindInfo.parentId,
                                PlayerAction.GroupManage(
                                    toAdd = playerIdList.takeIf { !bindInfo.isBound },
                                    toRemove = playerIdList.takeIf { bindInfo.isBound },
                                ),
                            )
                        },
                    )

                    if (bindInfo.id == localPlayerId) {
                        PlaybackDelayButtons(
                            isEnabled = bindInfo.isBound && onAdjustPlaybackDelay != null,
                            onAdjust = onAdjustPlaybackDelay,
                        )
                    } else {
                        VolumeRow(
                            volume = bindInfo.volume,
                            isMuted = bindInfo.isMuted,
                            enabled = bindInfo.volumeSliderAccessible && bindInfo.volume != null && bindInfo.isBound,
                            onMuteToggle = {
                                playerAction(
                                    bindInfo.id,
                                    PlayerAction.ToggleMute(isMutedNow = bindInfo.isMuted == true),
                                )
                            },
                            onVolumeSet = { level ->
                                playerAction(
                                    bindInfo.id,
                                    PlayerAction.VolumeSet(level.toDouble()),
                                )
                            },
                        )
                    }
                }
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
 * isn't joined to the group; the name and management buttons disabled.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GroupPlayerItemCard(
    background: Color = Color.Transparent,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(background, RoundedCornerShape(16.dp))
            .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
    ) {
        content()
    }
}

@Composable
private fun GroupItemTitle(
    isEnabled: Boolean,
    name: String,
    childBindItem: PlayerData.ChildBind? = null,
    onGroupActionClick: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier.fillMaxWidth().height(48.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            modifier = Modifier
                .weight(1f)
                .alphaOn(isEnabled),
            text = name,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
        )

        // Join/leave button is only shown for child-bind rows.
        childBindItem?.let { item ->
            onGroupActionClick?.let { action ->
                IconButton(
                    enabled = item.isManageable,
                    onClick = { action() },
                ) {
                    Icon(
                        modifier = Modifier.alphaOn(item.isManageable),
                        imageVector = if (item.isBound) Icons.Default.Remove else Icons.Default.Add,
                        contentDescription = if (item.isBound) {
                            stringResource(
                                Res.string.cd_remove_from_group,
                            )
                        } else {
                            stringResource(Res.string.cd_add_to_group)
                        },
                        tint = if (item.isBound) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.primary
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun PlaybackDelayButtons(
    isEnabled: Boolean,
    onAdjust: ((Int) -> Unit)?,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        listOf(
            "-100ms" to -HUNDRED,
            "-10ms" to -TEN,
            "-1ms" to -ONE,
            "+1ms" to ONE,
            "+10ms" to TEN,
            "+100ms" to HUNDRED,
        ).forEach { (label, delta) ->
            androidx.compose.material3.OutlinedButton(
                onClick = { onAdjust?.invoke(delta) },
                enabled = isEnabled,
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
    volume: Float?,
    isMuted: Boolean?,
    enabled: Boolean,
    onMuteToggle: () -> Unit,
    onVolumeSet: (Float) -> Unit,
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
                    .alphaOn(enabled)
                    .clickable(enabled = enabled) { onMuteToggle() },
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
            modifier = Modifier.weight(1f).alphaOn(enabled),
            value = currentVolume,
            valueRange = 0f..100f,
            enabled = enabled,
            onValueChange = { currentVolume = it },
            onValueChangeFinished = { onVolumeSet(currentVolume) },
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
        VolumeValue(
            modifier = Modifier.alphaOn(enabled),
            volume = currentVolume.roundToInt(),
            style = MaterialTheme.typography.labelMedium,
        )
    }
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
