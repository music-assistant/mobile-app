package io.music_assistant.client.ui.compose.home

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.pager.PagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Speaker
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.music_assistant.client.data.model.client.PlayerData
import io.music_assistant.client.ui.compose.common.HorizontalPagerIndicator
import io.music_assistant.client.ui.compose.common.OverflowMenu
import io.music_assistant.client.ui.compose.common.OverflowMenuOption

@Composable
fun PlayersTopBar(
    playerDataList: List<PlayerData>,
    playersState: HomeScreenViewModel.PlayersState.Data,
    playerPagerState: PagerState,
    onPlayersRefreshClick: () -> Unit,
    onItemMoved: ((Int) -> Unit)?,
    onMoveToPlayer: (String) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .wrapContentHeight(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(
            modifier = Modifier.size(32.dp),
            onClick = onPlayersRefreshClick
        ) {
            Icon(
                modifier = Modifier.size(20.dp),
                imageVector = Icons.Default.Refresh,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
        }

        HorizontalPagerIndicator(
            modifier = Modifier.weight(1f),
            pagerState = playerPagerState,
            onItemMoved = onItemMoved,
        )

        OverflowMenu(
            modifier = Modifier,
            buttonContent = { onClick ->
                IconButton(
                    modifier = Modifier.size(32.dp),
                    onClick = onClick
                ) {
                    Icon(
                        modifier = Modifier.size(20.dp),
                        imageVector = Icons.Filled.Speaker,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            },
            options = playerDataList.map { data ->
                val isLocalPlayer = data.playerId == playersState.localPlayerId
                OverflowMenuOption(
                    title = data.player.displayName + (if (isLocalPlayer) " (local)" else "")
                ) {
                    onMoveToPlayer(data.player.id)
                }
            }
        )
    }
}
