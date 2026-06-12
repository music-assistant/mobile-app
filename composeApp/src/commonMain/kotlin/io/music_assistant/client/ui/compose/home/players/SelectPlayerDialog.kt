// Compose layout values (sizes, alphas, animation durations) are visual design tokens.
@file:Suppress("MagicNumber")

package io.music_assistant.client.ui.compose.home.players

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import compose.icons.TablerIcons
import compose.icons.tablericons.GripVertical
import io.music_assistant.client.data.model.client.PlayerData
import io.music_assistant.client.data.model.client.PlayerDataFixtures
import io.music_assistant.client.ui.MAX_DIALOG_HEIGHT
import io.music_assistant.client.ui.compose.common.icons.NowPlayingIcon
import musicassistantclient.composeapp.generated.resources.Res
import musicassistantclient.composeapp.generated.resources.players_title
import org.jetbrains.compose.resources.stringResource
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SelectPlayerDialog(
    selectedPlayer: PlayerData,
    players: List<PlayerData>,
    onDismissRequest: () -> Unit = {},
    onMoveToPlayer: (String) -> Unit = {},
    onReorder: (List<String>) -> Unit = {},
) {
    Dialog(onDismissRequest = onDismissRequest) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
        ) {
            Box(modifier = Modifier.padding(vertical = 16.dp)) {
                Column {
                    Text(
                        stringResource(Res.string.players_title),
                        style = MaterialTheme.typography.headlineSmall,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )

                    Box(modifier = Modifier.padding(top = 16.dp)) {
                        PlayerSelection(
                            players,
                            selectedPlayer,
                            onDismissRequest,
                            onMoveToPlayer,
                            onReorder,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PlayerSelection(
    players: List<PlayerData>,
    selectedPlayer: PlayerData,
    onDismissRequest: () -> Unit,
    onSelectPlayer: (String) -> Unit,
    onReorder: (List<String>) -> Unit,
) {
    val plateShape = RoundedCornerShape(12.dp)

    var internalPlayers by remember { mutableStateOf(players) }
    var dragEndIndex by remember { mutableStateOf<Int?>(null) }
    val listState = rememberLazyListState()
    val reorderableLazyListState =
        rememberReorderableLazyListState(listState) { from, to ->
            internalPlayers = internalPlayers.toMutableList().apply {
                add(to.index, removeAt(from.index))
            }
            dragEndIndex = to.index
        }

    // Upstream emits frequently while a player is playing. Adopting them emits
    // mid-drag wipes the user's reorder and the list jumps. Sync upstream into
    // internal state only when no drag is in progress; refresh content in place
    // when the membership matches, so play/stop never reshuffles the order.
    LaunchedEffect(players, reorderableLazyListState.isAnyItemDragging) {
        if (reorderableLazyListState.isAnyItemDragging) return@LaunchedEffect
        val byId = players.associateBy { it.player.id }
        internalPlayers = if (byId.keys == internalPlayers.mapTo(mutableSetOf()) { it.player.id }) {
            internalPlayers.map { byId.getValue(it.player.id) }
        } else {
            players
        }
    }

    LazyColumn(
        modifier = Modifier
            .testTag("PlayersList")
            .selectableGroup()
            .heightIn(max = MAX_DIALOG_HEIGHT),
        state = listState,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        items(
            items = internalPlayers,
            key = { item -> item.player.id },
        ) { item ->
            val selected = item.player.id == selectedPlayer.player.id
            val borderColor = if (selected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.outlineVariant
            }
            val backgroundColor = if (selected) {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
            } else {
                Color.Transparent
            }

            ReorderableItem(state = reorderableLazyListState, key = item.player.id) {
                Row(
                    modifier = Modifier
                        .padding(horizontal = 16.dp)
                        .fillMaxWidth()
                        .clip(plateShape)
                        .background(backgroundColor)
                        .border(1.dp, borderColor, plateShape)
                        .selectable(
                            selected = selected,
                            onClick = {
                                onDismissRequest()
                                onSelectPlayer(item.player.id)
                            },
                            role = Role.RadioButton,
                        )
                        .padding(horizontal = 16.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    PlayerIcon(
                        player = item.player,
                        isLocal = item.isLocal,
                        modifier = Modifier.size(20.dp),
                    )
                    Text(
                        text = item.player.name,
                        modifier = Modifier
                            .padding(start = 8.dp)
                            .weight(1f),
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    item.player.suffix?.let { suffix ->
                        Text(
                            text = suffix,
                            modifier = Modifier.padding(start = 4.dp).alpha(0.6f),
                            style = MaterialTheme.typography.bodyLarge,
                            maxLines = 1,
                        )
                    }
                    if (item.player.isPlaying || item.player.isAnnouncing) {
                        NowPlayingIcon(
                            modifier = Modifier.padding(start = 8.dp),
                            size = 12.dp,
                            color = if (item.player.isAnnouncing) {
                                Color(0xFFFF9800)
                            } else {
                                Color(0xFF2196F3)
                            },
                        )
                    }
                    Icon(
                        modifier = Modifier
                            .padding(start = 8.dp)
                            .draggableHandle(
                                onDragStopped = {
                                    dragEndIndex?.let {
                                        onReorder(internalPlayers.map { p -> p.player.id })
                                    }
                                },
                            )
                            .size(16.dp),
                        imageVector = TablerIcons.GripVertical,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.secondary,
                    )
                }
            }
        }
    }
}

@Preview
@Composable
private fun PreviewSelectPlayerDialog() {
    val selectedPlayer = PlayerDataFixtures.playerData()
    SelectPlayerDialog(
        selectedPlayer = selectedPlayer,
        players = listOf(selectedPlayer, PlayerDataFixtures.playerData()),
        onDismissRequest = {},
    )
}

@Preview
@Composable
private fun PreviewSelectPlayerDialogLongList() {
    val players = 0.until(25).map {
        PlayerDataFixtures.playerData()
    }

    SelectPlayerDialog(
        selectedPlayer = players[0],
        players = players,
        onDismissRequest = {},
    )
}
