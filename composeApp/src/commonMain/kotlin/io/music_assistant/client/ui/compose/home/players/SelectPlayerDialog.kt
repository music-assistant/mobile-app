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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import compose.icons.TablerIcons
import compose.icons.tablericons.GripVertical
import io.music_assistant.client.data.model.client.PlayerData
import io.music_assistant.client.data.model.client.byId
import io.music_assistant.client.sharedicons.SharedIcons
import io.music_assistant.client.ui.MAX_DIALOG_HEIGHT
import io.music_assistant.client.ui.alphaOn
import io.music_assistant.client.ui.compose.common.OverflowMenuButton
import io.music_assistant.client.ui.compose.common.OverflowMenuOption
import io.music_assistant.client.ui.compose.common.action.PlayerAction
import io.music_assistant.client.ui.compose.common.icons.NowPlayingIcon
import musicassistantclient.composeapp.generated.resources.Res
import musicassistantclient.composeapp.generated.resources.cd_more
import musicassistantclient.composeapp.generated.resources.player_unavailable
import musicassistantclient.composeapp.generated.resources.players_group_settings
import musicassistantclient.composeapp.generated.resources.players_hide_unavailable
import musicassistantclient.composeapp.generated.resources.players_title
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.vectorResource
import sh.calvin.reorderable.ReorderableCollectionItemScope
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

@Composable
fun SelectPlayerDialog(
    selectedPlayer: PlayerData,
    players: List<PlayerData>,
    onDismissRequest: () -> Unit = {},
    onMoveToPlayer: (String) -> Unit = {},
    onReorder: (List<String>) -> Unit = {},
    onPlayerAction: (String, PlayerAction) -> Unit = { _, _ -> },
    hideUnavailable: Boolean = false,
    onToggleHideUnavailable: () -> Unit = {},
    onAdjustPlaybackDelay: (Int) -> Unit = {},
    canLeaveGroup: Boolean = false,
) {
    Dialog(onDismissRequest = onDismissRequest) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
        ) {
            var showGroupSettings by remember { mutableStateOf<String?>(null) }
            val groupPlayer = showGroupSettings?.let { players.byId(it) }

            Box(modifier = Modifier.padding(16.dp)) {
                if (groupPlayer != null) {
                    GroupLayout(
                        player = groupPlayer,
                        players = players,
                        onDismissRequest = onDismissRequest,
                        onPlayerAction = onPlayerAction,
                        onAdjustPlaybackDelay = onAdjustPlaybackDelay,
                        canLeaveGroup = canLeaveGroup,
                    )
                } else {
                    PlayerLayout(
                        players = players,
                        selectedPlayer = selectedPlayer,
                        onDismissRequest = onDismissRequest,
                        onMoveToPlayer = onMoveToPlayer,
                        onReorder = onReorder,
                        onGroupRequest = { player -> showGroupSettings = player.playerId },
                        hideUnavailable = hideUnavailable,
                        onToggleHideUnavailable = onToggleHideUnavailable,
                    )
                }
            }
        }
    }
}

@Composable
private fun PlayerLayout(
    players: List<PlayerData>,
    selectedPlayer: PlayerData,
    onDismissRequest: () -> Unit,
    onMoveToPlayer: (String) -> Unit,
    onReorder: (List<String>) -> Unit,
    onGroupRequest: (PlayerData) -> Unit,
    hideUnavailable: Boolean,
    onToggleHideUnavailable: () -> Unit,
) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(Res.string.players_title),
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.weight(1f),
            )
            OverflowMenuButton(
                options = listOf(
                    OverflowMenuOption(
                        title = stringResource(Res.string.players_hide_unavailable),
                        icon = Icons.Default.VisibilityOff,
                        trailingIcon = Icons.Default.Check.takeIf { hideUnavailable },
                        onClick = onToggleHideUnavailable,
                    ),
                ),
            ) { onClick ->
                IconButton(onClick = onClick) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = stringResource(Res.string.cd_more),
                    )
                }
            }
        }

        // The selected player stays listed even when unavailable, so the
        // selection never disappears from the list.
        val shownPlayers = remember(players, selectedPlayer, hideUnavailable) {
            if (hideUnavailable) {
                players.filter {
                    it.player.isSelectable || it.player.id == selectedPlayer.player.id
                }
            } else {
                players
            }
        }

        Box(modifier = Modifier.padding(top = 16.dp)) {
            PlayerSelection(
                players = shownPlayers,
                selectedPlayer = selectedPlayer,
                onDismissRequest = onDismissRequest,
                onSelectPlayer = onMoveToPlayer,
                onReorder =  { reordered, movedId ->
                        onReorder(mergeReorderedPlayers(players.map { it.player.id }, reordered, movedId))
                    },
                onGroupRequest = onGroupRequest,
            )
        }
    }
}

@Composable
private fun GroupLayout(
    player: PlayerData,
    players: List<PlayerData>,
    onDismissRequest: () -> Unit,
    onPlayerAction: (String, PlayerAction) -> Unit,
    onAdjustPlaybackDelay: (Int) -> Unit,
    canLeaveGroup: Boolean,
) {
    Column {
        Text(
            text = stringResource(Res.string.players_group_settings),
            style = MaterialTheme.typography.headlineSmall,
        )

        Box(modifier = Modifier.padding(top = 16.dp)) {
            GroupSettings(
                item = player,
                onDismiss = onDismissRequest,
                playerAction = onPlayerAction,
                localPlayerId = players.firstOrNull { it.isLocal }?.playerId,
                onAdjustPlaybackDelay = onAdjustPlaybackDelay,
                canLeaveGroup = canLeaveGroup,
            )
        }
    }
}

@Composable
private fun PlayerSelection(
    players: List<PlayerData>,
    selectedPlayer: PlayerData,
    onDismissRequest: () -> Unit,
    onSelectPlayer: (String) -> Unit,
    onReorder: (reordered: List<String>, movedId: String) -> Unit,
    onGroupRequest: (PlayerData) -> Unit,
) {
    val plateShape = RoundedCornerShape(12.dp)

    var internalPlayers by remember { mutableStateOf(players) }
    var dragEndIndex by remember { mutableStateOf<Int?>(null) }
    val listState = rememberLazyListState()
    val reorderableLazyListState =
        rememberReorderableLazyListState(listState) { from, to ->
            // The local player is pinned first (see MainDataSource.buildPlayerDataList):
            // nothing moves onto its slot, so nothing can land above it.
            if (internalPlayers.getOrNull(to.index)?.isLocal == true) return@rememberReorderableLazyListState
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

            ReorderableItem(
                state = reorderableLazyListState,
                key = item.player.id,
                enabled = !item.isLocal,
            ) {
                PlayerItem(
                    plateShape = plateShape,
                    backgroundColor = backgroundColor,
                    borderColor = borderColor,
                    selected = selected,
                    item = item,
                    onDismissRequest = onDismissRequest,
                    onSelectPlayer = onSelectPlayer,
                    dragEndIndex = dragEndIndex,
                    onReorder = onReorder,
                    internalPlayers = internalPlayers,
                    onGroupRequest = { onGroupRequest(item) },
                )
            }
        }
    }
}

@Composable
private fun ReorderableCollectionItemScope.PlayerItem(
    plateShape: RoundedCornerShape,
    backgroundColor: Color,
    borderColor: Color,
    selected: Boolean,
    item: PlayerData,
    onDismissRequest: () -> Unit,
    onSelectPlayer: (String) -> Unit,
    dragEndIndex: Int?,
    onReorder: (reordered: List<String>, movedId: String) -> Unit,
    internalPlayers: List<PlayerData>,
    onGroupRequest: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(plateShape)
            .background(backgroundColor)
            .border(1.dp, borderColor, plateShape)
            .selectable(
                selected = selected,
                enabled = item.player.isSelectable,
                onClick = {
                    onDismissRequest()
                    onSelectPlayer(item.player.id)
                },
                role = Role.RadioButton,
            )
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // A dormant player the app can still act on stays selectable: pinning the
        // selection to it keeps it there when the speaker wakes up. One it cannot
        // act on is listed but disabled.
        val dormant = item.player.isPoweredOff
        PlayerIcon(
            player = item.player,
            isLocal = item.isLocal,
            modifier = Modifier.size(20.dp).alphaOn(!dormant),
        )

        Text(
            text = item.player.name,
            modifier = Modifier
                .padding(start = 8.dp)
                .weight(1f)
                .alphaOn(!dormant),
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

        IconButton(onClick = onGroupRequest) {
            Icon(
                vectorResource(SharedIcons.getResource(SharedIcons.SPEAKER)),
                contentDescription = null,
            )
        }

        if (!item.player.isSelectable) {
            Text(
                text = stringResource(Res.string.player_unavailable),
                modifier = Modifier.padding(start = 8.dp).alpha(0.6f),
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
            )
        }

        if (!item.isLocal) {
            Icon(
                modifier = Modifier
                    .padding(start = 8.dp)
                    .draggableHandle(
                        onDragStopped = {
                            dragEndIndex?.let {
                                onReorder(internalPlayers.map { p -> p.player.id }, item.player.id)
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
