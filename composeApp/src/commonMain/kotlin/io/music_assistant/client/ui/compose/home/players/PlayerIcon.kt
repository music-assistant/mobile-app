package io.music_assistant.client.ui.compose.home.players

import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import io.music_assistant.client.data.model.client.Player
import io.music_assistant.client.sharedicons.SharedIcons
import io.music_assistant.client.ui.compose.common.providers.MdiIcon
import org.jetbrains.compose.resources.painterResource

/**
 * Canonical renderer for a player's icon across all surfaces.
 *
 * The on-device player ([isLocal]) keeps its client-role smartphone glyph. Real players
 * and groups use the server-provided icon ID ([Player.icon]) mapped to vector drawables
 * via [SharedIcons], falling back to speaker icon when the name is empty/unknown.
 */
@Composable
fun PlayerIcon(
    player: Player,
    isLocal: Boolean,
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current,
) {
    MdiIcon(
        name = player.icon,
        modifier = modifier,
        tint = tint,
        fallback = {
            val iconId = if (isLocal) SharedIcons.SMARTPHONE else player.icon
            val iconRes = SharedIcons.getResource(iconId)
            // Sized by MdiIcon's container, which owns `modifier`.
            Icon(
                painter = painterResource(iconRes),
                contentDescription = null,
                tint = tint,
            )
        },
    )
}
