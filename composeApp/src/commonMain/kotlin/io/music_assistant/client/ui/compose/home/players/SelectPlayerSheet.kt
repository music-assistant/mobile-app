// Compose layout values (sizes, alphas, animation durations) are visual design tokens.
@file:Suppress("MagicNumber")

package io.music_assistant.client.ui.compose.home.players

import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetState
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import compose.icons.TablerIcons
import compose.icons.tablericons.GripVertical
import io.music_assistant.client.data.model.client.PlayerData
import io.music_assistant.client.data.model.client.PlayerDelay
import io.music_assistant.client.data.model.client.byId
import io.music_assistant.client.ui.alphaOn
import io.music_assistant.client.ui.compose.common.OverflowMenuButton
import io.music_assistant.client.ui.compose.common.OverflowMenuOption
import io.music_assistant.client.ui.compose.common.action.PlayerAction
import io.music_assistant.client.ui.compose.common.boundedModalSheet
import io.music_assistant.client.ui.compose.common.icons.NowPlayingIcon
import io.music_assistant.client.ui.utils.SnapToHeightLayout
import musicassistantclient.composeapp.generated.resources.Res
import musicassistantclient.composeapp.generated.resources.cd_close
import musicassistantclient.composeapp.generated.resources.cd_more
import musicassistantclient.composeapp.generated.resources.player_unavailable
import musicassistantclient.composeapp.generated.resources.players_group_settings
import musicassistantclient.composeapp.generated.resources.players_hide_unavailable
import musicassistantclient.composeapp.generated.resources.players_nothing
import musicassistantclient.composeapp.generated.resources.players_title
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

/**
 * Keeps a scroll inside the sheet content from moving the sheet. The sheet collapses from
 * the leftover of a content scroll (post-scroll and post-fling), so consuming that leftover
 * here leaves the drag handle, the header, the back gesture and the close button as the
 * only ways to close it.
 */
private object SheetStaysOnContentScroll : NestedScrollConnection {
    override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource) = available
    override suspend fun onPostFling(consumed: Velocity, available: Velocity) = available
}

/** What the player sheet shows when it opens. */
sealed interface PlayerSheetStart {
    /** The list of players. */
    data object Players : PlayerSheetStart

    /** The group settings of one player. */
    data class Group(val playerId: String) : PlayerSheetStart
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SelectPlayerSheet(
    selectedPlayer: PlayerData,
    players: List<PlayerData>,
    onDismissRequest: () -> Unit = {},
    onMoveToPlayer: (String) -> Unit = {},
    onReorder: (List<String>) -> Unit = {},
    onPlayerAction: (String, PlayerAction) -> Unit = { _, _ -> },
    // The main transport button of a row, sent like the player's own button.
    onTransportAction: (PlayerData, PlayerAction) -> Unit = { _, _ -> },
    hideUnavailable: Boolean = false,
    onToggleHideUnavailable: () -> Unit = {},
    playerDelays: Map<String, PlayerDelay> = emptyMap(),
    onLoadPlayerDelays: (Collection<String>) -> Unit = {},
    onAdjustPlayerDelay: (String, Int) -> Unit = { _, _ -> },
    canLeaveGroup: Boolean = false,
    start: PlayerSheetStart = PlayerSheetStart.Players,
) {
    val sheetState = rememberBottomSheetState(
        initialValue = SheetValue.Hidden,
        enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded),
    )

    ModalBottomSheet(
        modifier = Modifier.boundedModalSheet(),
        sheetState = sheetState,
        onDismissRequest = onDismissRequest,
        // The lists draw behind the bottom inset and pad their end instead (sheetListPadding),
        // so the last row does not stop above an empty band.
        contentWindowInsets = { BottomSheetDefaults.modalWindowInsets.only(WindowInsetsSides.Top) },
    ) {
        SnapToHeightLayout(thresholdRatio = 0.6) {
            var showGroupSettings by remember { mutableStateOf((start as? PlayerSheetStart.Group)?.playerId) }
            val groupPlayer = showGroupSettings?.let { players.byId(it) }

            if (groupPlayer != null) {
                GroupLayout(
                    player = groupPlayer,
                    players = players,
                    onDismissRequest = onDismissRequest,
                    onPlayerAction = onPlayerAction,
                    playerDelays = playerDelays,
                    onLoadPlayerDelays = onLoadPlayerDelays,
                    onAdjustPlayerDelay = onAdjustPlayerDelay,
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
                    onTransportAction = onTransportAction,
                    hideUnavailable = hideUnavailable,
                    onToggleHideUnavailable = onToggleHideUnavailable,
                )
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
    onTransportAction: (PlayerData, PlayerAction) -> Unit,
    hideUnavailable: Boolean,
    onToggleHideUnavailable: () -> Unit,
) {
    Column {
        SheetHeader(title = stringResource(Res.string.players_title), onClose = onDismissRequest) {
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

        Box(modifier = Modifier.sheetListPadding().nestedScroll(SheetStaysOnContentScroll)) {
            PlayerSelection(
                contentPadding = sheetListContentPadding(),
                players = shownPlayers,
                selectedPlayer = selectedPlayer,
                onDismissRequest = onDismissRequest,
                onSelectPlayer = onMoveToPlayer,
                onReorder = { reordered, movedId ->
                    onReorder(
                        mergeReorderedPlayers(
                            players.map { it.player.id },
                            reordered,
                            movedId,
                        ),
                    )
                },
                onGroupRequest = onGroupRequest,
                onTransportAction = onTransportAction,
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
    playerDelays: Map<String, PlayerDelay>,
    onLoadPlayerDelays: (Collection<String>) -> Unit,
    onAdjustPlayerDelay: (String, Int) -> Unit,
    canLeaveGroup: Boolean,
) {
    // The pivot and the bound members show controls, so their delays load; a new member loads on join.
    val memberIds = remember(player.playerId, player.childrenBinds) {
        listOf(player.playerId) + player.childrenBinds.filter { it.isBound }.map { it.id }
    }
    LaunchedEffect(memberIds) { onLoadPlayerDelays(memberIds) }
    Column {
        SheetHeader(title = stringResource(Res.string.players_group_settings), onClose = onDismissRequest)

        Box(modifier = Modifier.sheetListPadding().nestedScroll(SheetStaysOnContentScroll)) {
            GroupSettings(
                contentPadding = sheetListContentPadding(),
                player = player,
                onDismiss = onDismissRequest,
                playerAction = onPlayerAction,
                localPlayerId = players.firstOrNull { it.isLocal }?.playerId,
                playerDelays = playerDelays,
                onAdjustPlayerDelay = onAdjustPlayerDelay,
                canLeaveGroup = canLeaveGroup,
            )
        }
    }
}

/**
 * The title row of the sheet: the title, the [actions], then the close button. The close button
 * shares the row with the actions, so their touch targets sit side by side and never stack.
 */
@Composable
private fun SheetHeader(
    title: String,
    onClose: () -> Unit,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        actions()
        IconButton(onClick = onClose) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = stringResource(Res.string.cd_close),
            )
        }
    }
}

/** The list box of the sheet: no bottom padding, so the list scrolls to the sheet edge. */
private fun Modifier.sheetListPadding() = padding(start = 16.dp, top = 16.dp, end = 16.dp)

/** The list end padding: the bottom safe area, plus the gap the box no longer adds. */
@Composable
private fun sheetListContentPadding() = PaddingValues(
    bottom = WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom).asPaddingValues().calculateBottomPadding() +
        16.dp,
)

@Composable
private fun PlayerSelection(
    contentPadding: PaddingValues,
    players: List<PlayerData>,
    selectedPlayer: PlayerData,
    onDismissRequest: () -> Unit,
    onSelectPlayer: (String) -> Unit,
    onReorder: (reordered: List<String>, movedId: String) -> Unit,
    onGroupRequest: (PlayerData) -> Unit,
    onTransportAction: (PlayerData, PlayerAction) -> Unit,
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
            .selectableGroup(),
        state = listState,
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(8.dp),
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
                Row(
                    modifier = Modifier
                        .height(56.dp)
                        .fillMaxWidth()
                        .clip(shape = plateShape)
                        .background(color = backgroundColor)
                        .border(
                            1.dp,
                            color = borderColor,
                            shape = plateShape,
                        )
                        .selectable(
                            selected = selected,
                            enabled = item.player.isSelectable,
                            onClick = {
                                onDismissRequest()
                                onSelectPlayer(item.player.id)
                            },
                            role = Role.RadioButton,
                        )
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // A dormant player the app can still act on stays selectable: pinning the
                    // selection to it keeps it there when the speaker wakes up. One it cannot
                    // act on is listed but disabled.
                    val dormant = item.player.isPoweredOff
                    Icon(
                        painter = painterResource(item.iconRes()),
                        modifier = Modifier.size(20.dp).alphaOn(!dormant),
                        contentDescription = null,
                    )

                    Column(
                        modifier = Modifier
                            .padding(start = 8.dp)
                            .weight(1f)
                            .alphaOn(!dormant),
                    ) {
                        Text(
                            text = item.player.name,
                            style = MaterialTheme.typography.bodyLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        // Never blank, so the marquee always has text to scroll.
                        Text(
                            text = item.player.currentMedia?.title?.takeUnless { it.isBlank() }
                                ?: stringResource(Res.string.players_nothing),
                            modifier = Modifier.basicMarquee().alpha(0.6f),
                            style = MaterialTheme.typography.bodySmall,
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

                    item.mainTransport().takeIf { it.enabled }?.let { main ->
                        IconButton(onClick = { onTransportAction(item, main.action) }) {
                            Icon(
                                imageVector = main.button.icon,
                                contentDescription = stringResource(main.button.label),
                            )
                        }
                    }

                    if (item.offersGrouping) {
                        GroupButton(player = item, onClick = { onGroupRequest(item) })
                    }

                    if (!item.player.isSelectable) {
                        Text(
                            text = stringResource(Res.string.player_unavailable),
                            modifier = Modifier.padding(start = 8.dp).alpha(0.6f),
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                        )
                    }

                    // The local player is pinned first, so its handle is invisible and inert;
                    // it keeps the group buttons of all rows aligned.
                    val handle = if (item.isLocal) {
                        Modifier.alpha(0f)
                    } else {
                        Modifier.draggableHandle(
                            onDragStopped = {
                                dragEndIndex?.let {
                                    onReorder(
                                        internalPlayers.map { p -> p.player.id },
                                        item.player.id,
                                    )
                                }
                            },
                        )
                    }
                    Icon(
                        modifier = Modifier
                            .padding(start = 8.dp)
                            .then(handle)
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
