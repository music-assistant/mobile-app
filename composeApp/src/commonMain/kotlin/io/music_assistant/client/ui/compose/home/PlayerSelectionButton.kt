package io.music_assistant.client.ui.compose.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Speaker
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.music_assistant.client.ui.compose.common.OverflowMenu
import io.music_assistant.client.ui.compose.common.OverflowMenuOption

@Composable
fun PlayerSelectionButton(
    playersData: HomeScreenViewModel.PlayersState.Data,
    onMoveToPlayer: (String) -> Unit
) {
    val selectedIndex = playersData.selectedPlayerIndex
    if (selectedIndex != null) {
        val currentPlayer = playersData.playerData[selectedIndex]

        OverflowMenu(
            options = playersData.playerData.map { data ->
                val isLocalPlayer = data.playerId == playersData.localPlayerId
                OverflowMenuOption(
                    title = data.player.displayName + (if (isLocalPlayer) " (local)" else ""),
                    icon = Icons.Filled.Speaker,
                ) {
                    onMoveToPlayer(data.player.id)
                }
            },
            buttonContent = { onClick ->
                Column(
                    modifier = Modifier
                        .clickable(onClick = onClick)
                        .defaultMinSize(
                            minWidth = ButtonDefaults.MinWidth,
                            minHeight = ButtonDefaults.MinHeight
                        ).padding(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        modifier = Modifier.padding(top = 8.dp),
                        imageVector = Icons.Default.Speaker,
                        tint = MaterialTheme.colorScheme.primary,
                        contentDescription = null
                    )

                    Text(
                        currentPlayer.player.displayName,
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
        )
    }
}