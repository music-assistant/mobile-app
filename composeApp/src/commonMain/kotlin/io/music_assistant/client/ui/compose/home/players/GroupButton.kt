package io.music_assistant.client.ui.compose.home.players

import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import io.music_assistant.client.data.model.client.PlayerData
import musicassistantclient.composeapp.generated.resources.Res
import musicassistantclient.composeapp.generated.resources.group
import musicassistantclient.composeapp.generated.resources.players_group_settings
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.vectorResource

/** The button that opens the group settings of [player], with the group count from [groupBadge]. */
@Composable
fun GroupButton(
    player: PlayerData,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current,
) {
    IconButton(modifier = modifier, onClick = onClick) {
        GroupIconWithCount(player = player, tint = tint)
    }
}

/** The group icon of [player], with the group count from [groupBadge] on its corner. */
@Composable
fun GroupIconWithCount(
    player: PlayerData,
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current,
) {
    BadgedBox(badge = { player.groupBadge()?.let { GroupCountBadge(it) } }) {
        Icon(
            vectorResource(Res.drawable.group),
            contentDescription = stringResource(Res.string.players_group_settings),
            modifier = modifier,
            tint = tint,
        )
    }
}
