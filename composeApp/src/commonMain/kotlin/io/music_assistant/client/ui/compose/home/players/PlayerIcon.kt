package io.music_assistant.client.ui.compose.home.players

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.Speaker
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import io.music_assistant.client.data.model.client.Player
import io.music_assistant.client.ui.compose.common.icons.SpeakerMultipleIcon
import io.music_assistant.client.ui.compose.common.providers.MdiIcon

/**
 * Canonical renderer for a player's icon across all surfaces.
 *
 * The on-device player ([isLocal]) keeps its client-role smartphone glyph. Real players
 * and groups use the server-provided MDI icon ([Player.icon]), falling back to a
 * kind-appropriate built-in vector when the name is empty/unknown or the codepoint table
 * hasn't loaded yet.
 */
@Composable
fun PlayerIcon(
    player: Player,
    isLocal: Boolean,
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current,
) {
    if (isLocal) {
        Icon(Icons.Default.Smartphone, contentDescription = null, modifier = modifier, tint = tint)
        return
    }
    val fallback = if (player.isGroup) SpeakerMultipleIcon else Icons.Default.Speaker
    MdiIcon(
        name = player.icon,
        modifier = modifier,
        tint = tint,
        fallback = {
            Icon(fallback, contentDescription = null, modifier = modifier, tint = tint)
        },
    )
}
