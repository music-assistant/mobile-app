// Compose layout values (sizes, alphas, animation durations) are visual design tokens.
@file:Suppress("MagicNumber")

package io.music_assistant.client.ui.compose.home.players

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AllInclusive
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.rounded.Forward30
import androidx.compose.material.icons.rounded.Replay10
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.music_assistant.client.data.model.client.PlayerData
import io.music_assistant.client.data.model.client.PlayerDataFixtures
import io.music_assistant.client.data.model.client.RepeatMode
import io.music_assistant.client.data.model.client.items.Audiobook
import io.music_assistant.client.data.model.client.items.LongFormSeekDefaults
import io.music_assistant.client.data.model.client.items.isLongFormSpokenContent
import io.music_assistant.client.settings.SettingsRepository
import io.music_assistant.client.ui.alphaOn
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
import musicassistantclient.composeapp.generated.resources.Res
import musicassistantclient.composeapp.generated.resources.action_pause
import musicassistantclient.composeapp.generated.resources.action_play
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

@Composable
fun PlayerControls(
    modifier: Modifier = Modifier,
    playerData: PlayerData,
    playerAction: (PlayerData, PlayerAction) -> Unit,
    showAdditionalButtons: Boolean = true,
    mainButtonSize: Dp,
    showSkip: Boolean = true,
    showSkipBack: Boolean = true,
    tint: Color = MaterialTheme.colorScheme.primary,
) {
    val settingsRepository: SettingsRepository = koinInject()
    val randomPlaybackMode by
        settingsRepository.randomPlaybackMode.collectAsStateWithLifecycle()

    val player = playerData.player
    val queue = playerData.queueInfo
    val playerEnabled = player.canPlay && !player.isAnnouncing
    val buttonsEnabled = queue?.currentItem?.isPlayable == true
    // Audiobooks / podcast episodes swap shuffle & repeat for skip-back / skip-forward seek.
    val isLongForm = queue?.currentItem?.track.isLongFormSpokenContent
    // Do not gate Next on playerData.queueItems.
    // The server queue may contain following items even when the client has
    // not loaded the full queue-item list yet.
    val skipForwardEnabled =
        buttonsEnabled &&
            (
                !isLongForm ||
                    (queue?.currentItem?.track as? Audiobook)?.fullyPlayed == false
            )
    val smallButtonSize = (mainButtonSize.value * 0.6).dp
    Row(
        modifier = modifier
            .wrapContentSize(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (showAdditionalButtons) {
            queue?.let {
                if (isLongForm) {
                    ActionButton(
                        icon = Icons.Rounded.Replay10,
                        tint = tint,
                        size = smallButtonSize,
                        enabled = playerEnabled && buttonsEnabled,
                    ) { playerAction(playerData, PlayerAction.SeekBy(-LongFormSeekDefaults.BACK_SECONDS)) }
                } else {
                    ActionButton(
                        icon =
                            when (randomPlaybackMode) {
                                SettingsRepository.RandomPlaybackMode.OFF ->
                                    ShuffleOffIcon

                                SettingsRepository.RandomPlaybackMode.RANDOM_SONGS ->
                                    ShuffleOnIcon

                                SettingsRepository.RandomPlaybackMode.RANDOM_FOLDERS ->
                                    Icons.Default.Folder
                            },
                        tint =
                            if (
                                randomPlaybackMode ==
                                SettingsRepository.RandomPlaybackMode.OFF
                            ) {
                                tint.copy(alpha = 0.45f)
                            } else {
                                tint
                            },
                        size = smallButtonSize,
                        enabled =
                            playerEnabled &&
                                buttonsEnabled &&
                                !it.isDynamicPlaylist,
                    ) {
                        settingsRepository.cycleRandomPlaybackMode()

                        // The custom Random Folder system owns randomisation.
                        // Disable Music Assistant queue shuffle if it happened
                        // to be enabled, otherwise the two systems would fight.
                        if (it.shuffleEnabled) {
                            playerAction(
                                playerData,
                                PlayerAction.ToggleShuffle(
                                    current = true,
                                ),
                            )
                        }
                    }

                    if (
                        it.autoPlayEnabled != null &&
                        !it.isDynamicPlaylist
                    ) {
                        ActionButton(
                            icon = Icons.Default.AllInclusive,
                            tint =
                                if (it.autoPlayEnabled == true) {
                                    tint
                                } else {
                                    tint.copy(alpha = 0.45f)
                                },
                            size = smallButtonSize,
                            enabled = playerEnabled && buttonsEnabled,
                        ) {
                            playerAction(
                                playerData,
                                PlayerAction.ToggleDontStopTheMusic(
                                    current = it.autoPlayEnabled == true,
                                ),
                            )
                        }
                    }
                }
            }
        }

        if (showSkipBack || showAdditionalButtons) {
            ActionButton(
                icon = SkipBackIcon,
                tint = tint,
                size = smallButtonSize,
                enabled = playerEnabled && buttonsEnabled,
            ) { playerAction(playerData, PlayerAction.Previous) }
        }

        if (playerData.pendingPlay && !player.isPlaying) {
            IconButton(
                modifier = Modifier
                    .size(mainButtonSize),
                onClick = { playerAction(playerData, PlayerAction.Pause) },
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
                contentDescription = when (player.isPlaying) {
                    true -> stringResource(Res.string.action_pause)
                    false -> stringResource(Res.string.action_play)
                },
            ) { playerAction(playerData, PlayerAction.TogglePlayPause) }
        }

        if (showSkip) {
            ActionButton(
                icon = SkipForwardIcon,
                tint = tint,
                size = smallButtonSize,
                enabled = playerEnabled && buttonsEnabled && skipForwardEnabled,
            ) { playerAction(playerData, PlayerAction.Next) }
        }

        if (showAdditionalButtons) {
            queue?.let {
                if (isLongForm) {
                    ActionButton(
                        icon = Icons.Rounded.Forward30,
                        tint = tint,
                        size = smallButtonSize,
                        enabled = playerEnabled && buttonsEnabled,
                    ) { playerAction(playerData, PlayerAction.SeekBy(LongFormSeekDefaults.FORWARD_SECONDS)) }
                } else {
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
                        enabled = playerEnabled && buttonsEnabled && repeatMode != null && !it.isDynamicPlaylist,
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
}

@Composable
private fun ActionButton(
    icon: ImageVector,
    size: Dp,
    tint: Color = MaterialTheme.colorScheme.primary,
    enabled: Boolean = true,
    contentDescription: String? = null,
    onClick: () -> Unit,
) {
    IconButton(
        modifier = Modifier
            .alphaOn(enabled)
            .size(size),
        onClick = onClick,
        enabled = enabled,
    ) {
        Icon(
            modifier = Modifier.size(size - 12.dp),
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tint,
        )
    }
}

@Preview
@Composable
private fun Preview(
    showAdditionButtons: Boolean = true,
    showSkip: Boolean = true,
    showSkipBack: Boolean = true,
) {
    MaterialTheme {
        PlayerControls(
            playerData = PlayerDataFixtures.playerData(),
            playerAction = { _, _ -> },
            showSkip = showSkip,
            showSkipBack = showSkipBack,
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
    Preview(showSkip = false, showAdditionButtons = false, showSkipBack = false)
}
