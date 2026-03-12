package io.music_assistant.client.ui.compose.home.players

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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.music_assistant.client.data.model.client.PlayerData
import io.music_assistant.client.data.model.client.PlayerDataFixtures

@Composable
fun PlayerSelectionButton(
    player: PlayerData,
    onSelectPlayer: () -> Unit = {}
) {
    Column(
        modifier = Modifier
            .clickable(onClick = onSelectPlayer)
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
            player.player.displayName,
            modifier = Modifier.padding(top = 4.dp),
            color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.labelSmall
        )
    }
}

@Preview
@Composable
fun PreviewPlayerSelectionButton() {
    val player = PlayerDataFixtures.playerData()
    PlayerSelectionButton(
        player = player
    )
}

@Preview
@Composable
fun PreviewPlayerSelectionButtonLongName() {
    val player = PlayerDataFixtures.playerData(name = "Very Long Speaker Name")
    PlayerSelectionButton(
        player = player
    )
}