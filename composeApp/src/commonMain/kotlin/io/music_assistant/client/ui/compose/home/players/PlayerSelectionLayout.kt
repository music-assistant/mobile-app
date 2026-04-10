package io.music_assistant.client.ui.compose.home.players

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.Speaker
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.music_assistant.client.data.model.client.PlayerData
import io.music_assistant.client.player.sendspin.SendspinState
import io.music_assistant.client.ui.compose.common.icons.SpeakerMultipleIcon

private val GROUP_BUTTON_SIZE = 36.dp

@Composable
fun PlayerSelectionLayout(
    player: PlayerData,
    sendSpinState: SendspinState?,
    onSelectPlayer: () -> Unit = {},
    onGroupButton: () -> Unit = {}
) {
    val isLocalPlayer = player.isLocal
    val dotColor = (if (isLocalPlayer) sendSpinState else null)?.toDotColor()
    val hasGroupChildren = player.childrenBinds.isNotEmpty()
    val hasBoundChildren = player.childrenBinds.any { it.isBound }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // Invisible counterweight so the player button stays centered
        if (hasGroupChildren) {
            Spacer(modifier = Modifier.size(GROUP_BUTTON_SIZE))
        }

        OutlinedButton(
            enabled = true,
            shape = RoundedCornerShape(GROUP_BUTTON_SIZE / 3),
            onClick = onSelectPlayer
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = when {
                        isLocalPlayer -> Icons.Default.Smartphone
                        player.player.isGroup -> SpeakerMultipleIcon
                        else -> Icons.Default.Speaker
                    },
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                )
                dotColor?.let {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(it, CircleShape)
                    )
                }
                Text(
                    text = player.player.name,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        if (hasGroupChildren) {
            val boundCount = player.childrenBinds.count { it.isBound }
            val groupLabel = when {
                player.player.isGroup -> "${player.player.groupMembers?.size ?: 0}"
                boundCount > 0 -> "+$boundCount"
                else -> "+"
            }

            Box(
                modifier = Modifier
                    .size(GROUP_BUTTON_SIZE)
                    .clip(RoundedCornerShape(GROUP_BUTTON_SIZE / 3))
                    .background(
                        if (hasBoundChildren) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                    )
                    .clickable(onClick = onGroupButton),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = groupLabel,
                    style = MaterialTheme.typography.labelMedium,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (hasBoundChildren)
                        MaterialTheme.colorScheme.onPrimary
                    else
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                )
            }
        }
    }
}

private fun SendspinState.toDotColor(): Color = when (this) {
    is SendspinState.Synchronized, is SendspinState.Ready,
    is SendspinState.Buffering -> Color(0xFF4CAF50) // Green
    is SendspinState.Connecting, is SendspinState.Authenticating,
    is SendspinState.Handshaking, is SendspinState.Reconnecting -> Color(0xFFFF9800) // Orange
    is SendspinState.Error -> Color(0xFFF44336) // Red
    is SendspinState.Idle -> Color(0xFFBDBDBD) // Light gray
}
