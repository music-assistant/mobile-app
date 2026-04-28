// Compose layout values (sizes, alphas, animation durations) are visual design tokens.
@file:Suppress("MagicNumber")

package io.music_assistant.client.ui.compose.home.players

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.music_assistant.client.data.model.client.PlayerData
import io.music_assistant.client.data.model.client.PlayerDataFixtures
import io.music_assistant.client.data.model.server.RepeatMode
import io.music_assistant.client.ui.compose.common.action.PlayerAction
import io.music_assistant.client.ui.compose.common.icons.PauseIcon
import io.music_assistant.client.ui.compose.common.icons.PlayIcon
import io.music_assistant.client.ui.compose.common.icons.RepeatOffIcon
import io.music_assistant.client.ui.compose.common.icons.RepeatOnIcon
import io.music_assistant.client.ui.compose.common.icons.RepeatOneIcon
import io.music_assistant.client.ui.compose.common.icons.ShuffleOffIcon
import io.music_assistant.client.ui.compose.common.icons.ShuffleOnIcon
import io.music_assistant.client.ui.compose.common.icons.SkipBackIcon
import io.music_assistant.client.ui.compose.common.icons.SkipForwardIcon

@Composable
fun PlayerControls(
    modifier: Modifier = Modifier,
    playerData: PlayerData,
    playerAction: (PlayerData, PlayerAction) -> Unit,
    showAdditionalButtons: Boolean = true,
    mainButtonSize: Dp,
    showSkip: Boolean = true,
    tint: Color = MaterialTheme.colorScheme.primary,
) {
    val player = playerData.player
    val queue = playerData.queueInfo
    val playerEnabled = player.canPlay && !player.isAnnouncing
    val buttonsEnabled = queue?.currentItem?.isPlayable == true
    val smallButtonSize = (mainButtonSize.value * 0.6).dp
    Row(
        modifier = modifier
            .wrapContentSize(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (showAdditionalButtons) {
            queue?.let {
                ActionButton(
                    icon = if (it.shuffleEnabled) {
                        ShuffleOnIcon
                    } else {
                        ShuffleOffIcon
                    },
                    tint = tint,
                    size = smallButtonSize,
                    enabled = playerEnabled && buttonsEnabled,
                ) {
                    playerAction(
                        playerData,
                        PlayerAction.ToggleShuffle(current = it.shuffleEnabled),
                    )
                }
            }
        }

        if (showSkip) {
            ActionButton(
                icon = SkipBackIcon,
                tint = tint,
                size = smallButtonSize,
                enabled = playerEnabled && buttonsEnabled,
            ) { playerAction(playerData, PlayerAction.Previous) }
        }

        if (playerData.pendingPlay && player.isPlaying) {
            IconButton(
                modifier = Modifier
                    .size(mainButtonSize),
                onClick = { playerAction(playerData, PlayerAction.TogglePlayPause) },
                enabled = playerEnabled && buttonsEnabled,
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size((mainButtonSize.value * 0.6).dp),
                    color = tint,
                    strokeWidth = 2.dp,
                )
            }
        } else {
            ActionButton(
                icon = when (player.isPlaying) {
                    true -> PauseIcon
                    false -> PlayIcon
                },
                tint = tint,
                size = mainButtonSize,
                enabled = playerEnabled && buttonsEnabled,
            ) { playerAction(playerData, PlayerAction.TogglePlayPause) }
        }

        if (showSkip) {
            ActionButton(
                icon = SkipForwardIcon,
                tint = tint,
                size = smallButtonSize,
                enabled = playerEnabled && buttonsEnabled,
            ) { playerAction(playerData, PlayerAction.Next) }
        }

        if (showAdditionalButtons) {
            queue?.let {
                val repeatMode = it.repeatMode
                ActionButton(
                    icon = when (repeatMode) {
                        RepeatMode.ONE -> RepeatOneIcon
                        RepeatMode.ALL -> RepeatOnIcon
                        RepeatMode.OFF,
                        null,
                        -> RepeatOffIcon
                    },
                    tint = tint,
                    size = smallButtonSize,
                    enabled = playerEnabled && buttonsEnabled && repeatMode != null,
                ) {
                    repeatMode?.let {
                        playerAction(
                            playerData,
                            PlayerAction.ToggleRepeatMode(current = repeatMode),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ActionButton(
    icon: ImageVector,
    size: Dp,
    tint: Color = MaterialTheme.colorScheme.primary,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    IconButton(
        modifier = Modifier
            .alpha(if (enabled) 1F else 0.5f)
            .size(size),
        onClick = onClick,
        enabled = enabled,
    ) {
        Icon(
            modifier = Modifier.size(size - 12.dp),
            imageVector = icon,
            contentDescription = null,
            tint = tint,
        )
    }
}

@Preview
@Composable
private fun Preview(showAdditionButtons: Boolean = true, showSkip: Boolean = true) {
    MaterialTheme {
        PlayerControls(
            playerData = PlayerDataFixtures.playerData(),
            playerAction = { _, _ -> },
            showSkip = showSkip,
            mainButtonSize = 60.dp,
            showAdditionalButtons = showAdditionButtons,
        )
    }
}

@Preview
@Composable
private fun PreviewNoAdditional() {
    Preview(showAdditionButtons = false)
}

@Preview
@Composable
private fun PreviewNoSkipNoAdditional() {
    Preview(showSkip = false, showAdditionButtons = false)
}
