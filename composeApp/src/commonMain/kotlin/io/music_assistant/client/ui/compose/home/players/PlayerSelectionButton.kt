// Compose layout values (sizes, alphas, animation durations) are visual design tokens.
@file:Suppress("MagicNumber")

package io.music_assistant.client.ui.compose.home.players

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Link
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SplitButtonDefaults.LeadingButton
import androidx.compose.material3.SplitButtonDefaults.TrailingButton
import androidx.compose.material3.SplitButtonLayout
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.music_assistant.client.data.model.client.PlayerData
import io.music_assistant.client.data.model.client.PlayerDataFixtures
import io.music_assistant.client.data.model.client.PlayerType
import io.music_assistant.client.player.sendspin.SendspinState
import io.music_assistant.client.ui.contentColorByLuminance
import musicassistantclient.composeapp.generated.resources.Res
import musicassistantclient.composeapp.generated.resources.cd_current_player
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun PlayerSelectionButton(
    player: PlayerData,
    controlTint: Color = MaterialTheme.colorScheme.primary,
    sendSpinState: SendspinState?,
    onSelectPlayer: () -> Unit = {},
    onGroupButton: () -> Unit = {},
) {
    val isLocalPlayer = player.isLocal
    val dotColor = (if (isLocalPlayer) sendSpinState else null)?.toDotColor()
    val onTint = controlTint.contentColorByLuminance()
    val hasGroupChildren = player.childrenBinds.isNotEmpty()
    val hasBoundChildren = player.childrenBinds.any { it.isBound }

    val currentPlayerContentDescription =
        stringResource(Res.string.cd_current_player, player.player.name)
    val modifier = Modifier.clearAndSetSemantics {
        contentDescription = currentPlayerContentDescription
    }

    if (hasGroupChildren) {
        SplitButtonLayout(
            modifier = modifier,
            leadingButton = {
                LeadingButton(
                    onClick = onSelectPlayer,
                    colors = ButtonDefaults.outlinedButtonColors(),
                    border = ButtonDefaults.outlinedButtonBorder(),
                ) {
                    PlayerButtonContent(
                        isLocalPlayer = isLocalPlayer,
                        player = player,
                        controlTint = controlTint,
                        dotColor = dotColor,
                    )
                }
            },
            trailingButton = {
                if (hasGroupChildren) {
                    val (buttonColor, iconColor, border) = if (hasBoundChildren) {
                        Triple(
                            ButtonDefaults.buttonColors(
                                containerColor = controlTint,
                                contentColor = onTint,
                            ),
                            null,
                            null,
                        )
                    } else {
                        Triple(
                            ButtonDefaults.outlinedButtonColors(),
                            controlTint,
                            ButtonDefaults.outlinedButtonBorder(),
                        )
                    }

                    TrailingButton(
                        onClick = onGroupButton,
                        colors = buttonColor,
                        border = border,
                    ) {
                        iconColor?.let {
                            Icon(
                                modifier = Modifier.size(16.dp),
                                imageVector = Icons.Default.Link,
                                contentDescription = null,
                                tint = it,
                            )
                        } ?: Icon(
                            modifier = Modifier.size(16.dp),
                            imageVector = Icons.Default.Link,
                            contentDescription = null,
                        )
                    }
                }
            },
        )
    } else {
        OutlinedButton(
            modifier = modifier,
            onClick = onSelectPlayer,
        ) {
            PlayerButtonContent(
                isLocalPlayer = isLocalPlayer,
                player = player,
                controlTint = controlTint,
                dotColor = dotColor,
            )
        }
    }
}

@Composable
private fun PlayerButtonContent(
    isLocalPlayer: Boolean,
    player: PlayerData,
    controlTint: Color,
    dotColor: Color?,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        PlayerIcon(
            player = player.player,
            isLocal = isLocalPlayer,
            modifier = Modifier.size(16.dp),
            tint = controlTint,
        )
        dotColor?.let {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(it, CircleShape),
            )
        }

        val boundCount = player.childrenBinds.count { it.isBound }
        val playerLabel = when {
            player.player.isGroup -> "${player.player.name} (${player.player.groupMembers?.size ?: 0})"
            boundCount > 0 -> "${player.player.name} + $boundCount"
            else -> player.player.name
        }

        Text(
            text = playerLabel,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private fun SendspinState.toDotColor(): Color = when (this) {
    is SendspinState.Synchronized, is SendspinState.Ready,
    is SendspinState.Buffering,
        -> Color(0xFF4CAF50) // Green
    is SendspinState.Connecting, is SendspinState.Authenticating,
    is SendspinState.Handshaking, is SendspinState.Reconnecting,
        -> Color(0xFFFF9800) // Orange
    is SendspinState.Error -> Color(0xFFF44336) // Red
    is SendspinState.Idle -> Color(0xFFBDBDBD) // Light gray
}

@Preview
@Composable
private fun PlayerSelectionButtonNotGroupablePreview() {
    MaterialTheme {
        PlayerSelectionButton(
            player = PlayerDataFixtures.playerData(),
            sendSpinState = null,
        )
    }
}

@Preview
@Composable
private fun PlayerSelectionButtonLocalPlayerPreview() {
    MaterialTheme {
        PlayerSelectionButton(
            player = PlayerDataFixtures.playerData().copy(isLocal = true),
            sendSpinState = SendspinState.Synchronized,
        )
    }
}

@Preview
@Composable
private fun PlayerSelectionButtonPreview() {
    MaterialTheme {
        PlayerSelectionButton(
            player = PlayerDataFixtures.playerData(
                groupChildren = listOf(PlayerDataFixtures.bind().copy(isBound = false)),
            ),
            sendSpinState = null,
        )
    }
}

@Preview
@Composable
private fun PlayerSelectionButtonInGroupPreview() {
    MaterialTheme {
        PlayerSelectionButton(
            player = PlayerDataFixtures.playerData(
                groupChildren = listOf(PlayerDataFixtures.bind().copy(isBound = true)),
            ),
            sendSpinState = null,
        )
    }
}

@Preview
@Composable
private fun PlayerSelectionButtonIsGroupPreview() {
    MaterialTheme {
        PlayerSelectionButton(
            player = PlayerDataFixtures.playerData(playerType = PlayerType.GROUP),
            sendSpinState = null,
        )
    }
}
