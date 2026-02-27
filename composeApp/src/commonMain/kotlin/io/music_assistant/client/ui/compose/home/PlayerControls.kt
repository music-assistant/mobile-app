package io.music_assistant.client.ui.compose.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOn
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.ShuffleOn
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import compose.icons.FontAwesomeIcons
import compose.icons.fontawesomeicons.Solid
import compose.icons.fontawesomeicons.solid.VolumeDown
import compose.icons.fontawesomeicons.solid.VolumeUp
import io.music_assistant.client.data.model.client.PlayerData
import io.music_assistant.client.data.model.server.RepeatMode
import io.music_assistant.client.ui.compose.common.action.PlayerAction

@Composable
fun PlayerControls(
    modifier: Modifier = Modifier,
    playerData: PlayerData,
    playerAction: (PlayerData, PlayerAction) -> Unit,
    showVolumeButtons: Boolean = true,
    showAdditionalButtons: Boolean = true,
    mainButtonSize: Dp = 48.dp
) {
    val player = playerData.player
    val queue = playerData.queueInfo
    val playerEnabled = player.canPlay && !player.isAnnouncing
    val buttonsEnabled = queue?.currentItem?.isPlayable == true
    val smallButtonSize = (mainButtonSize.value * 0.6).dp
    val additionalButtonSize = (mainButtonSize.value * 0.4).dp
    Row(
        modifier = modifier
            .wrapContentSize(),
        horizontalArrangement = Arrangement.spacedBy(
            16.dp,
            alignment = Alignment.CenterHorizontally
        ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (player.canSetVolume && showVolumeButtons) {
            ActionButton(
                icon = FontAwesomeIcons.Solid.VolumeDown,
                tint = MaterialTheme.colorScheme.primary,
                size = smallButtonSize,
                enabled = playerEnabled,
            ) { playerAction(playerData, PlayerAction.VolumeDown) }
        }

        if (showAdditionalButtons) {
            queue?.let {
                ActionButton(
                    icon = if (it.shuffleEnabled)
                        Icons.Default.ShuffleOn
                    else
                        Icons.Default.Shuffle,
                    tint = MaterialTheme.colorScheme.primary,
                    size = additionalButtonSize,
                    enabled = playerEnabled && buttonsEnabled,
                ) {
                    playerAction(
                        playerData,
                        PlayerAction.ToggleShuffle(current = it.shuffleEnabled)
                    )
                }
            }
        }

        ActionButton(
            icon = Icons.Default.SkipPrevious,
            tint = MaterialTheme.colorScheme.primary,
            size = smallButtonSize,
            enabled = playerEnabled && buttonsEnabled,
        ) { playerAction(playerData, PlayerAction.Previous) }

        if (playerData.pendingPlay && player.isPlaying) {
            IconButton(
                modifier = Modifier
                    .size(mainButtonSize),
                onClick = { playerAction(playerData, PlayerAction.TogglePlayPause) },
                enabled = playerEnabled && buttonsEnabled,
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size((mainButtonSize.value * 0.6).dp),
                    color = MaterialTheme.colorScheme.primary,
                    strokeWidth = 2.dp,
                )
            }
        } else {
            ActionButton(
                icon = when (player.isPlaying) {
                    true -> Icons.Default.Pause
                    false -> Icons.Default.PlayArrow
                },
                tint = MaterialTheme.colorScheme.primary,
                size = mainButtonSize,
                enabled = playerEnabled && buttonsEnabled,
            ) { playerAction(playerData, PlayerAction.TogglePlayPause) }
        }

        ActionButton(
            icon = Icons.Default.SkipNext,
            tint = MaterialTheme.colorScheme.primary,
            size = smallButtonSize,
            enabled = playerEnabled && buttonsEnabled,
        ) { playerAction(playerData, PlayerAction.Next) }

        if (showAdditionalButtons) {
            queue?.let {
                val repeatMode = it.repeatMode
                ActionButton(
                    icon = when (repeatMode) {
                        RepeatMode.ONE -> Icons.Default.RepeatOne
                        RepeatMode.ALL -> Icons.Default.RepeatOn
                        RepeatMode.OFF,
                        null -> Icons.Default.Repeat
                    },
                    tint = MaterialTheme.colorScheme.primary,
                    size = additionalButtonSize,
                    enabled = playerEnabled && buttonsEnabled && repeatMode != null,
                ) {
                    repeatMode?.let {
                        playerAction(
                            playerData,
                            PlayerAction.ToggleRepeatMode(current = repeatMode)
                        )
                    }
                }
            }
        }

        if (player.canSetVolume && showVolumeButtons) {
            ActionButton(
                icon = FontAwesomeIcons.Solid.VolumeUp,
                tint = MaterialTheme.colorScheme.primary,
                size = smallButtonSize,
                enabled = playerEnabled,
            ) { playerAction(playerData, PlayerAction.VolumeUp) }
        }
    }
}

@Composable
private fun ActionButton(
    icon: ImageVector,
    size: Dp = 24.dp,
    tint: Color = MaterialTheme.colorScheme.primary,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    IconButton(
        modifier = Modifier
            .alpha(if (enabled) 1F else 0.5f)
            .size(size),
        onClick = onClick,
        enabled = enabled,
    ) {
        Icon(
            modifier = Modifier.size(size),
            imageVector = icon,
            contentDescription = null,
            tint = tint,
        )
    }
}