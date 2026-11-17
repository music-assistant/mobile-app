package io.music_assistant.client.ui.compose.home.players

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import io.music_assistant.client.data.model.client.PlayerData
import musicassistantclient.composeapp.generated.resources.Res
import musicassistantclient.composeapp.generated.resources.group
import musicassistantclient.composeapp.generated.resources.players_group_settings
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.vectorResource

private val ContainerSize = 40.dp

/**
 * The button that opens the group settings of [player], with the group count from [groupBadge].
 *
 * Not an `IconButton`: it clips its content to its 40 dp circle, which cuts a "+N" badge.
 */
@Composable
fun GroupButton(
    player: PlayerData,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current,
) {
    Box(
        modifier = modifier
            .minimumInteractiveComponentSize()
            .size(ContainerSize)
            .clickable(
                onClick = onClick,
                role = Role.Button,
                interactionSource = null,
                indication = ripple(bounded = false, radius = ContainerSize / 2),
            ),
        contentAlignment = Alignment.Center,
    ) {
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
