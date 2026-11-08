package io.music_assistant.client.ui.compose.home.players

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import io.music_assistant.client.data.model.client.Player
import io.music_assistant.client.data.model.client.PlayerData
import io.music_assistant.client.data.model.client.PlayerDataFixtures
import io.music_assistant.client.data.model.client.PlayerDelay
import io.music_assistant.client.ui.HUNDRED
import io.music_assistant.client.ui.ONE
import io.music_assistant.client.ui.TEN
import io.music_assistant.client.ui.alphaOn
import io.music_assistant.client.ui.compose.common.action.PlayerAction
import io.music_assistant.client.ui.compose.common.icons.VolumeIcon
import io.music_assistant.client.ui.compose.common.icons.VolumeMutedIcon
import musicassistantclient.composeapp.generated.resources.Res
import musicassistantclient.composeapp.generated.resources.cd_add_to_group
import musicassistantclient.composeapp.generated.resources.cd_leave_group
import musicassistantclient.composeapp.generated.resources.cd_mute
import musicassistantclient.composeapp.generated.resources.cd_player_delay
import musicassistantclient.composeapp.generated.resources.cd_player_volume
import musicassistantclient.composeapp.generated.resources.cd_remove_from_group
import musicassistantclient.composeapp.generated.resources.cd_unmute
import musicassistantclient.composeapp.generated.resources.players_delay_slow_down
import musicassistantclient.composeapp.generated.resources.players_delay_speed_up
import musicassistantclient.composeapp.generated.resources.players_delay_step
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import kotlin.math.roundToInt

/** The height of each row. Below the 48 dp default, so a long group fits the sheet. */
private val ROW_HEIGHT = 40.dp

/** The gap between a row and the panel that floats above it. */
private val PANEL_GAP = 4.dp

/** The delay steps of one panel row, in ms. */
private val DELAY_STEPS_MS = listOf(ONE, TEN, HUNDRED)

/**
 * The group sheet: the pivot player first, then its members, bound before unbound. A row shows
 * the player icon, the name, a volume button, a delay button for a player in [playerDelays], and
 * the join or leave checkbox. A button opens its panel above the row; a tap outside closes it.
 * Only one panel is open at a time. A row outside the group shows no buttons, because they would
 * act on a player that does not follow the group.
 */
@Composable
fun GroupSettings(
    player: PlayerData,
    onDismiss: () -> Unit = {},
    playerAction: (String, PlayerAction) -> Unit = { _, _ -> },
    localPlayerId: String? = null,
    playerDelays: Map<String, PlayerDelay> = emptyMap(),
    onAdjustPlayerDelay: (String, Int) -> Unit = { _, _ -> },
    canLeaveGroup: Boolean = false,
) {
    var openPanel by remember { mutableStateOf<OpenPanel?>(null) }

    Column(
        modifier = Modifier.fillMaxWidth(),
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f, false),
        ) {
            // Pivot first, then children (bound before unbound).
            item {
                val pivot = player.player
                GroupPlayerItemCard(
                    background = MaterialTheme.colorScheme.surfaceContainerLow,
                ) {
                    GroupPlayerRow(
                        icon = player.iconRes(),
                        isEnabled = true,
                        name = pivot.name,
                        volume = if (pivot.isGroup) {
                            pivot.groupVolume?.let { groupVolumeControl(pivot, playerAction) }
                        } else {
                            pivot.volumeLevel?.let {
                                playerVolumeControl(
                                    playerId = pivot.id,
                                    volume = it,
                                    isMuted = pivot.volumeMuted.takeIf { pivot.canMute },
                                    enabled = pivot.isVolumeSliderAccessible,
                                    playerAction = playerAction,
                                )
                            }
                        },
                        delay = playerDelays[pivot.id],
                        onAdjustDelay = { onAdjustPlayerDelay(pivot.id, it) },
                        openPanel = openPanel?.kindFor(pivot.id),
                        onOpenPanel = { kind -> openPanel = kind?.let { OpenPanel(pivot.id, it) } },
                        // The leader leaves via `ungroup`, not `set_members`: the server
                        // owns the choice of successor and moves the queue there.
                        toggle = GroupToggle(
                            isBound = true,
                            isManageable = true,
                            contentDescription = Res.string.cd_leave_group,
                            onClick = {
                                playerAction(pivot.id, PlayerAction.LeaveGroup)
                                onDismiss()
                            },
                        ).takeIf { canLeaveGroup && pivot.canLeaveOwnGroup },
                    )
                }
            }

            val sortedChildren = player.childrenBinds.sortedByDescending { it.isBound }
            items(sortedChildren, key = { it.id }) { bindInfo ->
                GroupPlayerItemCard {
                    GroupPlayerRow(
                        icon = playerIconRes(bindInfo.icon, isLocal = bindInfo.id == localPlayerId),
                        isEnabled = bindInfo.isBound,
                        name = bindInfo.name,
                        volume = playerVolumeControl(
                            playerId = bindInfo.id,
                            volume = bindInfo.volume,
                            isMuted = bindInfo.isMuted,
                            enabled = bindInfo.volumeSliderAccessible && bindInfo.volume != null,
                            playerAction = playerAction,
                        ).takeIf { bindInfo.isBound },
                        delay = playerDelays[bindInfo.id].takeIf { bindInfo.isBound },
                        onAdjustDelay = { onAdjustPlayerDelay(bindInfo.id, it) },
                        openPanel = openPanel?.kindFor(bindInfo.id),
                        onOpenPanel = { kind -> openPanel = kind?.let { OpenPanel(bindInfo.id, it) } },
                        toggle = GroupToggle(
                            isBound = bindInfo.isBound,
                            isManageable = bindInfo.isManageable,
                            contentDescription = if (bindInfo.isBound) {
                                Res.string.cd_remove_from_group
                            } else {
                                Res.string.cd_add_to_group
                            },
                            onClick = {
                                val playerIdList = listOf(bindInfo.id)
                                playerAction(
                                    bindInfo.parentId,
                                    PlayerAction.GroupManage(
                                        toAdd = playerIdList.takeIf { !bindInfo.isBound },
                                        toRemove = playerIdList.takeIf { bindInfo.isBound },
                                    ),
                                )
                            },
                        ),
                    )
                }
            }
        }
    }
}

/** The card of one row. Every control in it gets the [ROW_HEIGHT] touch height. */
@Composable
private fun GroupPlayerItemCard(
    background: Color = Color.Transparent,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(background, RoundedCornerShape(16.dp))
            .padding(horizontal = 16.dp),
    ) {
        CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides ROW_HEIGHT) {
            content()
        }
    }
}

/** The panels a row can open. */
private enum class PanelKind { VOLUME, DELAY }

/** The one open panel of the sheet. */
private data class OpenPanel(val playerId: String, val kind: PanelKind) {
    fun kindFor(playerId: String): PanelKind? = kind.takeIf { this.playerId == playerId }
}

/**
 * The join/leave affordance of one row, decoupled from [PlayerData.ChildBind] so the
 * pivot player can carry one too — its button leaves its own group rather than
 * adding or removing a member.
 */
private data class GroupToggle(
    val isBound: Boolean,
    val isManageable: Boolean,
    val contentDescription: StringResource,
    val onClick: () -> Unit,
)

/** The volume panel of one row. [isMuted] null hides the mute toggle. */
private data class VolumeControl(
    val volume: Float?,
    val isMuted: Boolean?,
    val enabled: Boolean,
    val onMuteToggle: () -> Unit,
    val onVolumeSet: (Float) -> Unit,
    val onStepDown: () -> Unit,
    val onStepUp: () -> Unit,
)

private fun playerVolumeControl(
    playerId: String,
    volume: Float?,
    isMuted: Boolean?,
    enabled: Boolean,
    playerAction: (String, PlayerAction) -> Unit,
) = VolumeControl(
    volume = volume,
    isMuted = isMuted,
    enabled = enabled,
    onMuteToggle = { playerAction(playerId, PlayerAction.ToggleMute(isMutedNow = isMuted == true)) },
    onVolumeSet = { level -> playerAction(playerId, PlayerAction.VolumeSet(level.toDouble())) },
    onStepDown = { playerAction(playerId, PlayerAction.VolumeDown) },
    onStepUp = { playerAction(playerId, PlayerAction.VolumeUp) },
)

/** The volume of a group player is its group volume: the group has no other. */
private fun groupVolumeControl(
    group: Player,
    playerAction: (String, PlayerAction) -> Unit,
) = VolumeControl(
    volume = group.groupVolume,
    isMuted = group.groupVolumeMuted.takeIf { group.canMute },
    enabled = group.isVolumeSliderAccessible,
    onMuteToggle = {
        playerAction(group.id, PlayerAction.GroupToggleMute(isMutedNow = group.groupVolumeMuted))
    },
    onVolumeSet = { level -> playerAction(group.id, PlayerAction.GroupVolumeSet(level.toDouble())) },
    onStepDown = { playerAction(group.id, PlayerAction.GroupVolumeDown) },
    onStepUp = { playerAction(group.id, PlayerAction.GroupVolumeUp) },
)

@Composable
private fun GroupPlayerRow(
    icon: DrawableResource,
    isEnabled: Boolean,
    name: String,
    volume: VolumeControl?,
    delay: PlayerDelay?,
    onAdjustDelay: (Int) -> Unit,
    openPanel: PanelKind?,
    onOpenPanel: (PanelKind?) -> Unit,
    toggle: GroupToggle?,
) {
    // The panel anchors to this box and takes its width.
    var rowWidthPx by remember { mutableIntStateOf(0) }
    Box(modifier = Modifier.fillMaxWidth().onSizeChanged { rowWidthPx = it.width }) {
        Row(
            modifier = Modifier.fillMaxWidth().height(ROW_HEIGHT),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                modifier = Modifier.size(24.dp).alphaOn(isEnabled),
                painter = painterResource(icon),
                contentDescription = null,
            )
            Text(
                modifier = Modifier
                    .weight(1f)
                    .alphaOn(isEnabled),
                text = name,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            volume?.let {
                PanelButton(
                    imageVector = if (it.isMuted == true) VolumeMutedIcon else VolumeIcon,
                    contentDescription = stringResource(Res.string.cd_player_volume, name),
                    enabled = it.enabled,
                    selected = openPanel == PanelKind.VOLUME,
                    onClick = { onOpenPanel(PanelKind.VOLUME) },
                )
            }
            delay?.let {
                PanelButton(
                    imageVector = Icons.Outlined.Timer,
                    contentDescription = stringResource(Res.string.cd_player_delay, name),
                    enabled = true,
                    selected = openPanel == PanelKind.DELAY,
                    onClick = { onOpenPanel(PanelKind.DELAY) },
                )
            }
            // Rows with no grouping gesture (a pivot that cannot leave) show no checkbox.
            toggle?.let { item ->
                val description = stringResource(item.contentDescription)
                Checkbox(
                    modifier = Modifier.semantics { contentDescription = description },
                    checked = item.isBound,
                    enabled = item.isManageable,
                    onCheckedChange = { item.onClick() },
                )
            }
        }
        // A row that lost its control while its panel was open shows no empty panel.
        val panel: (@Composable () -> Unit)? = when (openPanel) {
            null -> null
            PanelKind.VOLUME -> volume?.let { control -> { VolumeRow(control) } }
            PanelKind.DELAY -> delay?.let { value -> { DelayButtons(value, onAdjustDelay) } }
        }
        panel?.let { content ->
            RowPanel(widthPx = rowWidthPx, onDismiss = { onOpenPanel(null) }, content = content)
        }
    }
}

@Composable
private fun PanelButton(
    imageVector: ImageVector,
    contentDescription: String,
    enabled: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
) {
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.size(ROW_HEIGHT),
        colors = IconButtonDefaults.iconButtonColors(
            contentColor = if (selected) MaterialTheme.colorScheme.primary else LocalContentColor.current,
        ),
    ) {
        Icon(
            modifier = Modifier.size(24.dp),
            imageVector = imageVector,
            contentDescription = contentDescription,
        )
    }
}

/**
 * A panel that floats above its row, as wide as the row. It takes the focus, so a tap outside
 * it, or the back gesture, closes it.
 */
@Composable
private fun RowPanel(
    widthPx: Int,
    onDismiss: () -> Unit,
    content: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    val gapPx = with(density) { PANEL_GAP.roundToPx() }
    Popup(
        popupPositionProvider = remember(gapPx) { AboveAnchor(gapPx) },
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true),
    ) {
        Surface(
            modifier = Modifier.width(with(density) { widthPx.toDp() }),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            shadowElevation = 4.dp,
        ) {
            Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                content()
            }
        }
    }
}

/** Places the panel above its row, or below it when the row is at the top of the window. */
private class AboveAnchor(private val gapPx: Int) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        val above = anchorBounds.top - popupContentSize.height - gapPx
        val y = if (above >= 0) above else anchorBounds.bottom + gapPx
        return IntOffset(anchorBounds.left, y)
    }
}

@Composable
private fun DelayButtons(delay: PlayerDelay, onAdjust: (Int) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        DelayStepRow(
            label = stringResource(Res.string.players_delay_speed_up),
            enabled = delay.canSpeedUp,
            direction = 1,
            onAdjust = onAdjust,
        )
        DelayStepRow(
            label = stringResource(Res.string.players_delay_slow_down),
            enabled = delay.canSlowDown,
            direction = -1,
            onAdjust = onAdjust,
        )
    }
}

@Composable
private fun DelayStepRow(
    label: String,
    enabled: Boolean,
    direction: Int,
    onAdjust: (Int) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            modifier = Modifier.weight(1f),
            text = label,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        DELAY_STEPS_MS.forEach { step ->
            OutlinedButton(
                onClick = { onAdjust(direction * step) },
                enabled = enabled,
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 0.dp, vertical = 4.dp),
            ) {
                Text(
                    text = stringResource(Res.string.players_delay_step, step),
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VolumeRow(control: VolumeControl) {
    var currentVolume by remember(control.volume) { mutableStateOf(control.volume ?: 0f) }
    val enabled = control.enabled

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        control.isMuted?.let { isMuted ->
            Icon(
                modifier = Modifier
                    .size(24.dp)
                    .alphaOn(enabled)
                    .clickable(enabled = enabled) { control.onMuteToggle() },
                imageVector = if (isMuted) {
                    VolumeMutedIcon
                } else {
                    VolumeIcon
                },
                contentDescription = if (isMuted) {
                    stringResource(
                        Res.string.cd_unmute,
                    )
                } else {
                    stringResource(Res.string.cd_mute)
                },
            )
        }
        VolumeSliderBox(
            modifier = Modifier.weight(1f),
            enabled = enabled,
            volume = { currentVolume },
            onStepDown = control.onStepDown,
            onStepUp = control.onStepUp,
        ) {
            Slider(
                modifier = Modifier.fillMaxWidth().alphaOn(enabled),
                value = currentVolume,
                valueRange = 0f..100f,
                enabled = enabled,
                onValueChange = { currentVolume = it },
                onValueChangeFinished = { control.onVolumeSet(currentVolume) },
                thumb = {
                    SliderDefaults.Thumb(
                        interactionSource = remember { MutableInteractionSource() },
                        thumbSize = DpSize(16.dp, 16.dp),
                        colors = SliderDefaults.colors()
                            .copy(thumbColor = MaterialTheme.colorScheme.secondary),
                    )
                },
                track = { sliderState ->
                    SliderDefaults.Track(
                        sliderState = sliderState,
                        thumbTrackGapSize = 0.dp,
                        trackInsideCornerSize = 0.dp,
                        drawStopIndicator = null,
                        // Square inside corners leave 0 % and 100 % with a square end.
                        modifier = Modifier.height(4.dp).clip(CircleShape),
                    )
                },
            )
        }
        VolumeValue(
            modifier = Modifier.alphaOn(enabled),
            volume = currentVolume.roundToInt(),
            style = MaterialTheme.typography.labelMedium,
        )
    }
}

@Preview
@Composable
private fun PreviewGroupSettings() {
    val selectedPlayer = PlayerDataFixtures.playerData()
    GroupSettings(player = selectedPlayer)
}

/** The sync leader's own card carries a leave button once the server supports handoff. */
@Preview
@Composable
private fun PreviewGroupSettingDialogLeaderCanLeave() {
    val leader = PlayerDataFixtures.playerData(
        groupChildren = List(2) { PlayerDataFixtures.bind().copy(isBound = true) },
    )
    GroupSettings(
        player = leader.copy(
            player = leader.player.copy(groupMembers = setOf("member-a", "member-b")),
        ),
        canLeaveGroup = true,
    )
}

@Preview
@Composable
private fun PreviewGroupSettingDialogLongList() {
    val selectedPlayer = PlayerDataFixtures.playerData(
        groupChildren = 0.until(25).map {
            PlayerDataFixtures.bind()
        },
    )
    GroupSettings(player = selectedPlayer)
}
