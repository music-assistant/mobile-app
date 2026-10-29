package io.music_assistant.client.ui.compose.home.players

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import io.music_assistant.client.data.model.client.PlayerData
import io.music_assistant.client.data.model.client.PlayerDataFixtures
import io.music_assistant.client.ui.HUNDRED
import io.music_assistant.client.ui.ONE
import io.music_assistant.client.ui.TEN
import io.music_assistant.client.ui.alphaOn
import io.music_assistant.client.ui.compose.common.action.PlayerAction
import io.music_assistant.client.ui.compose.common.icons.VolumeIcon
import io.music_assistant.client.ui.compose.common.icons.VolumeMutedIcon
import musicassistantclient.composeapp.generated.resources.Res
import musicassistantclient.composeapp.generated.resources.cd_add_to_group
import musicassistantclient.composeapp.generated.resources.cd_leave_group
import musicassistantclient.composeapp.generated.resources.cd_mute
import musicassistantclient.composeapp.generated.resources.cd_remove_from_group
import musicassistantclient.composeapp.generated.resources.cd_unmute
import musicassistantclient.composeapp.generated.resources.players_group_volume
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import kotlin.math.roundToInt

@Composable
fun GroupSettings(
    player: PlayerData,
    onDismiss: () -> Unit = {},
    playerAction: (String, PlayerAction) -> Unit = { _, _ -> },
    localPlayerId: String? = null,
    onAdjustPlaybackDelay: ((Int) -> Unit) = {},
    canLeaveGroup: Boolean = false,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f, false),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (player.player.isGrouped) {
                item(key = player.player.groupVolume) {
                    GroupPlayerItemCard {
                        GroupItemTitle(
                            isEnabled = player.player.groupVolume != null,
                            name = stringResource(Res.string.players_group_volume, player.player.name),
                        )
                        VolumeRow(
                            volume = player.player.groupVolume,
                            isMuted = player.player.groupVolumeMuted,
                            enabled = player.player.groupVolume != null,
                            onMuteToggle = {
                                playerAction(
                                    player.player.id,
                                    PlayerAction.GroupToggleMute(isMutedNow = player.player.groupVolumeMuted),
                                )
                            },
                            onVolumeSet = { level ->
                                playerAction(
                                    player.player.id,
                                    PlayerAction.GroupVolumeSet(level.toDouble()),
                                )
                            },
                            onStepDown = { playerAction(player.player.id, PlayerAction.GroupVolumeDown) },
                            onStepUp = { playerAction(player.player.id, PlayerAction.GroupVolumeUp) },
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
                        name = player.player.name,
                        // The leader leaves via `ungroup`, not `set_members`: the server
                        // owns the choice of successor and moves the queue there.
                        toggle = GroupToggle(
                            isBound = true,
                            isManageable = true,
                            contentDescription = Res.string.cd_leave_group,
                            onClick = {
                                playerAction(player.player.id, PlayerAction.LeaveGroup)
                                onDismiss()
                            },
                        ).takeIf { canLeaveGroup && player.player.canLeaveOwnGroup },
                    )
                    if (player.player.isGroup) {
                        player.player.groupVolume?.let { playerVolumeLevel ->
                            VolumeRow(
                                volume = playerVolumeLevel,
                                isMuted = player.player.groupVolumeMuted.takeIf { player.player.canMute },
                                enabled = player.player.isVolumeSliderAccessible,
                                onMuteToggle = {
                                    playerAction(
                                        player.player.id,
                                        PlayerAction.GroupToggleMute(isMutedNow = player.player.groupVolumeMuted),
                                    )
                                },
                                onVolumeSet = { level ->
                                    playerAction(
                                        player.player.id,
                                        PlayerAction.GroupVolumeSet(level.toDouble()),
                                    )
                                },
                                onStepDown = { playerAction(player.player.id, PlayerAction.GroupVolumeDown) },
                                onStepUp = { playerAction(player.player.id, PlayerAction.GroupVolumeUp) },
                            )
                        }
                    } else {
                        player.player.volumeLevel?.let { playerVolumeLevel ->
                            VolumeRow(
                                volume = playerVolumeLevel,
                                isMuted = player.player.volumeMuted.takeIf { player.player.canMute },
                                enabled = player.player.isVolumeSliderAccessible,
                                onMuteToggle = {
                                    playerAction(
                                        player.player.id,
                                        PlayerAction.ToggleMute(isMutedNow = player.player.volumeMuted),
                                    )
                                },
                                onVolumeSet = { level ->
                                    playerAction(
                                        player.player.id,
                                        PlayerAction.VolumeSet(level.toDouble()),
                                    )
                                },
                                onStepDown = { playerAction(player.player.id, PlayerAction.VolumeDown) },
                                onStepUp = { playerAction(player.player.id, PlayerAction.VolumeUp) },
                            )
                        }
                    }
                }
            }

            val sortedChildren = player.childrenBinds.sortedByDescending { it.isBound }
            items(sortedChildren, key = { "${it.id}_${it.volume}" }) { bindInfo ->
                GroupPlayerItemCard(
                    background = if (bindInfo.isBound) MaterialTheme.colorScheme.surfaceContainerHigh else Color.Transparent,
                ) {
                    GroupItemTitle(
                        isEnabled = bindInfo.isBound,
                        name = bindInfo.name,
                        toggle = GroupToggle(
                            isBound = bindInfo.isBound,
                            isManageable = bindInfo.isManageable,
                            contentDescription = if (bindInfo.isBound) {
                                Res.string.cd_remove_from_group
                            } else {
                                Res.string.cd_add_to_group
                            },
                            onClick = {
                                val playerIdList = listOf(bindInfo.id)
                                playerAction(
                                    bindInfo.parentId,
                                    PlayerAction.GroupManage(
                                        toAdd = playerIdList.takeIf { !bindInfo.isBound },
                                        toRemove = playerIdList.takeIf { bindInfo.isBound },
                                    ),
                                )
                            },
                        ),
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
                            onStepDown = { playerAction(bindInfo.id, PlayerAction.VolumeDown) },
                            onStepUp = { playerAction(bindInfo.id, PlayerAction.VolumeUp) },
                        )
                    }
                }
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

/**
 * The join/leave affordance of one row, decoupled from [PlayerData.ChildBind] so the
 * pivot player can carry one too — its button leaves its own group rather than
 * adding or removing a member.
 */
private data class GroupToggle(
    val isBound: Boolean,
    val isManageable: Boolean,
    val contentDescription: StringResource,
    val onClick: () -> Unit,
)

@Composable
private fun GroupItemTitle(
    isEnabled: Boolean,
    name: String,
    toggle: GroupToggle? = null,
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

        // Rows with no grouping gesture (a pivot that cannot leave) show no button.
        toggle?.let { item ->
            IconButton(
                enabled = item.isManageable,
                onClick = item.onClick,
            ) {
                Icon(
                    modifier = Modifier.alphaOn(item.isManageable),
                    imageVector = if (item.isBound) Icons.Default.Remove else Icons.Default.Add,
                    contentDescription = stringResource(item.contentDescription),
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
    onStepDown: () -> Unit,
    onStepUp: () -> Unit,
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
        VolumeSliderBox(
            modifier = Modifier.weight(1f),
            enabled = enabled,
            volume = { currentVolume },
            onStepDown = onStepDown,
            onStepUp = onStepUp,
        ) {
            Slider(
                modifier = Modifier.fillMaxWidth().alphaOn(enabled),
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
                        // Square inside corners leave 0 % and 100 % with a square end.
                        modifier = Modifier.height(4.dp).clip(CircleShape),
                    )
                },
            )
        }
        VolumeValue(
            modifier = Modifier.alphaOn(enabled),
            volume = currentVolume.roundToInt(),
            style = MaterialTheme.typography.labelMedium,
        )
    }
}

@Preview
@Composable
private fun PreviewGroupSettings() {
    val selectedPlayer = PlayerDataFixtures.playerData()
    GroupSettings(player = selectedPlayer)
}

/** The sync leader's own card carries a leave button once the server supports handoff. */
@Preview
@Composable
private fun PreviewGroupSettingDialogLeaderCanLeave() {
    val leader = PlayerDataFixtures.playerData(
        groupChildren = List(2) { PlayerDataFixtures.bind().copy(isBound = true) },
    )
    GroupSettings(
        player = leader.copy(
            player = leader.player.copy(groupMembers = setOf("member-a", "member-b")),
        ),
        canLeaveGroup = true,
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
    GroupSettings(player = selectedPlayer)
}
