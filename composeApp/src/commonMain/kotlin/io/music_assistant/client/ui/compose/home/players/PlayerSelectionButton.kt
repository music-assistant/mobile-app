package io.music_assistant.client.ui.compose.home.players

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Speaker
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import io.music_assistant.client.data.model.client.PlayerData
import io.music_assistant.client.data.model.client.PlayerDataFixtures
import io.music_assistant.client.ui.compose.common.action.PlayerAction

@Composable
fun PlayerSelectionButton(
    selectedPlayer: Int,
    players: List<PlayerData>,
    onMoveToPlayer: (String) -> Unit = {},
    groupAction: (String, PlayerAction) -> Unit = { _, _ -> }
) {
    val currentPlayer = players[selectedPlayer]

    var showSelectDialog by remember { mutableStateOf(false) }
    if (showSelectDialog) {
        SelectPlayerDialog(
            selectedPlayer = currentPlayer,
            players = players,
            onDismissRequest = { showSelectDialog = false },
            onMoveToPlayer = onMoveToPlayer,
            groupAction = groupAction
        )
    }

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
            modifier = Modifier.padding(top = 4.dp),
            color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.labelSmall
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SelectPlayerDialog(
    selectedPlayer: PlayerData,
    players: List<PlayerData>,
    onDismissRequest: () -> Unit,
    onMoveToPlayer: (String) -> Unit = {},
    groupAction: (String, PlayerAction) -> Unit = { _, _ -> }
) {
    Dialog(onDismissRequest = onDismissRequest) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp)
        ) {
            Box(modifier = Modifier.padding(16.dp)) {
                var showGroupSettings by remember { mutableStateOf(false) }

                if (showGroupSettings) {
                    GroupSettings(
                        item = selectedPlayer,
                        onDismissRequest,
                        groupAction
                    )
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectableGroup()
                    ) {
                        players.forEach {
                            val selected = it.player.id == selectedPlayer.player.id

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(56.dp)
                                    .selectable(
                                        selected = selected,
                                        onClick = {
                                            onDismissRequest()
                                            onMoveToPlayer(it.player.id)
                                        },
                                        role = Role.RadioButton
                                    ),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = selected,
                                    onClick = null
                                )

                                Text(
                                    it.player.displayName,
                                    style = MaterialTheme.typography.bodyLarge,
                                    modifier = Modifier.padding(start = 16.dp)
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            TextButton(onClick = { showGroupSettings = true }) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Link,
                                        contentDescription = null
                                    )

                                    Text(
                                        "Group",
                                        modifier = Modifier.padding(start = 4.dp)
                                    )
                                }
                            }
                        }
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
        players = listOf(PlayerDataFixtures.playerData())
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