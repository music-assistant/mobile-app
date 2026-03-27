package io.music_assistant.client.ui.compose.home.players

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.music_assistant.client.data.model.client.PlayerData
import io.music_assistant.client.player.sendspin.SendspinState
import io.music_assistant.client.ui.compose.home.HomeScreenViewModel

@Composable
fun PlayerSelectionButton(
    player: PlayerData,
    playersState: HomeScreenViewModel.PlayersState.Data,
    onSelectPlayer: () -> Unit = {}
) {
    val isLocalPlayer = player.playerId == playersState.localPlayerId
    val dotColor = (if (isLocalPlayer) playersState.sendspinState else null)?.toDotColor()

    Box {
        val playerName: @Composable (Color) -> Unit = { textColor ->
            Row(
                modifier = Modifier.align(Alignment.Center),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                dotColor?.let {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(it, CircleShape)
                    )
                }
                Text(
                    text = player.player.displayName,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    fontWeight = FontWeight.Medium,
                    color = textColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        when {
            player.groupChildren.none { it.isBound } ->
                OutlinedButton(
                    modifier = Modifier.align(Alignment.Center),
                    enabled = true,
                    onClick = onSelectPlayer
                ) {
                    playerName(MaterialTheme.colorScheme.onSurface)
                }

            else ->
                Button(
                    modifier = Modifier.align(Alignment.Center),
                    enabled = true,
                    onClick = onSelectPlayer
                ) {
                    playerName(MaterialTheme.colorScheme.onPrimary)
                }
        }
    }
}

private fun SendspinState.toDotColor(): Color = when (this) {
    is SendspinState.Synchronized, is SendspinState.Ready,
    is SendspinState.Buffering -> Color(0xFF4CAF50) // Green
    is SendspinState.Connecting, is SendspinState.Authenticating,
    is SendspinState.Handshaking, is SendspinState.Reconnecting -> Color(0xFFFF9800) // Orange
    is SendspinState.Error -> Color(0xFFF44336) // Red
    is SendspinState.Idle -> Color(0xFFBDBDBD) // Light gray
}