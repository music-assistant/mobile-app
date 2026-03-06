package io.music_assistant.client.ui.compose.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Speaker
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import io.music_assistant.client.data.model.client.PlayerData
import io.music_assistant.client.data.model.client.PlayerDataFixtures

@Composable
fun PlayerSelectionButton(
    selectedPlayer: Int,
    players: List<PlayerData>,
    onMoveToPlayer: (String) -> Unit = {}
) {
    var showSelectDialog by remember { mutableStateOf(false) }
    val currentPlayer = players[selectedPlayer]

    Column(
        modifier = Modifier
            .clickable(onClick = { showSelectDialog = true })
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

    if (showSelectDialog) {
        SelectPlayerDialog(
            selectedPlayer = currentPlayer,
            players = players,
            onDismissRequest = { showSelectDialog = false },
            onMoveToPlayer = onMoveToPlayer
        )
    }
}

@Composable
private fun SelectPlayerDialog(
    selectedPlayer: PlayerData,
    players: List<PlayerData>,
    onDismissRequest: () -> Unit,
    onMoveToPlayer: (String) -> Unit = {}
) {
    Dialog(onDismissRequest = onDismissRequest) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(Modifier.padding(16.dp)) {
                players.forEach {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(
                            selected = it.player.id == selectedPlayer.player.id,
                            onClick = {
                                onMoveToPlayer(it.player.id)
                                onDismissRequest()
                            }
                        )

                        Text(it.player.displayName)
                    }
                }
            }
        }
    }
}

@Preview
@Composable
fun Preview() {
    PlayerSelectionButton(
        selectedPlayer = 0,
        players = listOf(PlayerDataFixtures.playerData()),
    )
}

@Preview
@Composable
fun PreviewSelectPlayerDialog() {
    val selectedPlayer = PlayerDataFixtures.playerData()
    SelectPlayerDialog(
        selectedPlayer = selectedPlayer,
        players = listOf(selectedPlayer, PlayerDataFixtures.playerData()),
        onDismissRequest = {},
    )
}