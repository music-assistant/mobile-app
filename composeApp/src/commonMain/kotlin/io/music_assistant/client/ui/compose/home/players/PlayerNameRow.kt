package io.music_assistant.client.ui.compose.home.players

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.music_assistant.client.player.sendspin.SendspinState

@Composable
fun PlayerNameRow(
    playerName: String,
    hasNoBoundChildren: Boolean,
    isLocalPlayer: Boolean,
    sendspinState: SendspinState?,
    onShowGroup: () -> Unit
) {
    val dotColor = sendspinState.toDotColor().takeIf { isLocalPlayer }

    Box(
        modifier = Modifier.fillMaxWidth()
    ) {
        val playerName: @Composable (Color) -> Unit = { textColor ->
            Row(
                modifier = Modifier.align(Alignment.Center),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = playerName + (if (isLocalPlayer) " (local)" else ""),
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    fontWeight = FontWeight.Medium,
                    color = textColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                dotColor?.let {
                    Spacer(modifier = Modifier.width(4.dp))
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(it, CircleShape)
                    )
                }
            }
        }

        when {
            hasNoBoundChildren ->
                OutlinedButton(
                    modifier = Modifier.align(Alignment.Center),
                    enabled = true,
                    onClick = { onShowGroup() }
                ) {
                    playerName(MaterialTheme.colorScheme.onSurface)
                }

            else ->
                Button(
                    modifier = Modifier.align(Alignment.Center),
                    enabled = true,
                    onClick = { onShowGroup() }) {
                    playerName(MaterialTheme.colorScheme.onPrimary)
                }
        }

//                    // Overflow menu on the right TODO re-enable when settings are fixed in MA
//                    OverflowMenuThreeDots(
//                        modifier = Modifier.align(Alignment.CenterEnd)
//                            .padding(end = 8.dp),
//                        options = listOf(
//                            OverflowMenuOption(
//                                title = "Settings",
//                                onClick = { settingsAction(player.player.id) }
//                            ),
//                            OverflowMenuOption(
//                                title = "DSP settings",
//                                onClick = { dspSettingsAction(player.player.id) }
//                            ),
//                        )
//                    )
    }
}

private fun SendspinState?.toDotColor(): Color = when (this) {
    is SendspinState.Synchronized, is SendspinState.Ready,
    is SendspinState.Buffering -> Color(0xFF4CAF50) // Green
    is SendspinState.Connecting, is SendspinState.Authenticating,
    is SendspinState.Handshaking, is SendspinState.Reconnecting -> Color(0xFFFF9800) // Orange
    is SendspinState.Error -> Color(0xFFF44336) // Red
    is SendspinState.Idle, null -> Color(0xFFBDBDBD) // Light gray
}