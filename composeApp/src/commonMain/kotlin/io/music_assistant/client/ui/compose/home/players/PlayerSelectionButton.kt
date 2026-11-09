// Compose layout values (sizes, alphas, animation durations) are visual design tokens.
@file:Suppress("MagicNumber")

package io.music_assistant.client.ui.compose.home.players

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SplitButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.music_assistant.client.data.model.client.PlayerData
import io.music_assistant.client.data.model.client.PlayerDataFixtures
import io.music_assistant.sendspin.api.PlayerState
import org.jetbrains.compose.resources.painterResource

/**
 * The player chip. With [onGroup] and a player that offers grouping, it has two sections: the main
 * section selects a player, and the additional section opens grouping and shows the group count.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun PlayerSelectionButton(
    player: PlayerData,
    controlTint: Color = MaterialTheme.colorScheme.primary,
    sendSpinState: PlayerState?,
    onSelectPlayer: () -> Unit = {},
    onGroup: (() -> Unit)? = null,
) {
    val dotColor = player.statusDotColor(sendSpinState)

    val playerContentDescription = player.contentDescription()
    val mainModifier = Modifier.clearAndSetSemantics {
        contentDescription = playerContentDescription
    }

    onGroup?.takeIf { player.offersGrouping }?.let { group ->
        // A Row, not SplitButtonLayout: that layout gives the main section the full width
        // first, so a long name would push the group section out.
        Row(horizontalArrangement = Arrangement.spacedBy(SplitButtonDefaults.Spacing)) {
            SplitButtonDefaults.OutlinedLeadingButton(
                modifier = mainModifier.weight(1f, fill = false),
                onClick = onSelectPlayer,
            ) {
                PlayerButtonContent(player = player, controlTint = controlTint, dotColor = dotColor)
            }
            SplitButtonDefaults.TrailingButton(
                onClick = group,
                colors = ButtonDefaults.outlinedButtonColors(),
                elevation = null,
                border = ButtonDefaults.outlinedButtonBorder(),
            ) {
                // A 24 dp box keeps the badge on its corner, off the 18 dp glyph.
                GroupIconWithCount(
                    player = player,
                    modifier = Modifier.size(24.dp).padding((24.dp - ButtonDefaults.IconSize) / 2),
                    tint = controlTint,
                )
            }
        }
    } ?: OutlinedButton(
        modifier = mainModifier,
        onClick = onSelectPlayer,
    ) {
        PlayerButtonContent(player = player, controlTint = controlTint, dotColor = dotColor)
    }
}

@Composable
private fun PlayerButtonContent(
    player: PlayerData,
    controlTint: Color,
    dotColor: Color?,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(
            painter = painterResource(player.iconRes()),
            modifier = Modifier.size(ButtonDefaults.IconSize),
            tint = controlTint,
            contentDescription = null,
        )

        dotColor?.let {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(it, CircleShape),
            )
        }

        Text(
            text = player.player.name,
            modifier = Modifier.weight(1f, fill = false),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** The Sendspin link colour for the local player, or null for a server player. */
internal fun PlayerData.statusDotColor(sendspinState: PlayerState?): Color? =
    sendspinState?.takeIf { isLocal }?.toDotColor()

private fun PlayerState.toDotColor(): Color = when (this) {
    is PlayerState.Connected -> Color(0xFF4CAF50) // Green
    is PlayerState.Connecting, is PlayerState.Reconnecting -> Color(0xFFFF9800) // Orange
    is PlayerState.Failed -> Color(0xFFF44336) // Red
    PlayerState.Disabled -> Color(0xFFBDBDBD) // Light gray
}

@Preview
@Composable
private fun PlayerSelectionButtonNotGroupablePreview() {
    MaterialTheme {
        PlayerSelectionButton(
            player = PlayerDataFixtures.playerData(),
            sendSpinState = null,
        )
    }
}

@Preview
@Composable
private fun PlayerSelectionButtonLocalPlayerPreview() {
    MaterialTheme {
        PlayerSelectionButton(
            player = PlayerDataFixtures.playerData().copy(isLocal = true),
            sendSpinState = PlayerState.Connecting(0),
        )
    }
}

@Preview
@Composable
private fun PlayerSelectionButtonPreview() {
    MaterialTheme {
        PlayerSelectionButton(
            player = PlayerDataFixtures.playerData(
                groupChildren = listOf(PlayerDataFixtures.bind().copy(isBound = false)),
            ),
            sendSpinState = null,
            onGroup = {},
        )
    }
}
