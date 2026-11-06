package io.music_assistant.client.ui.compose.home.players

import androidx.compose.runtime.Composable
import io.music_assistant.client.data.model.client.PlayerData
import io.music_assistant.client.sharedicons.SharedIcons
import musicassistantclient.composeapp.generated.resources.Res
import musicassistantclient.composeapp.generated.resources.cd_current_player
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.stringResource

@Composable
fun PlayerData.contentDescription(): String {
    val currentPlayerContentDescription =
        stringResource(Res.string.cd_current_player, player.name)
    return currentPlayerContentDescription
}

/**
 * The text of the group badge on the player icon: the member count of a group, "+N" for the
 * players synced to a leader, or null for a player that plays alone.
 */
fun PlayerData.groupBadge(): String? {
    val boundCount = childrenBinds.count { it.isBound }
    return when {
        player.isGroup -> (player.groupMembers?.size ?: 0).toString()
        boundCount > 0 -> "+$boundCount"
        else -> null
    }
}

fun PlayerData.iconRes(): DrawableResource {
    val iconId = if (this.isLocal) SharedIcons.SMARTPHONE else this.player.icon
    return SharedIcons.getResource(iconId)
}
