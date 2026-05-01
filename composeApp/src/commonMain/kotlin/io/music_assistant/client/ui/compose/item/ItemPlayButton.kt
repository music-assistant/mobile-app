package io.music_assistant.client.ui.compose.item

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddToQueue
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.PlaylistAddCircle
import androidx.compose.material.icons.filled.QueuePlayNext
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.SplitButtonDefaults.LeadingButton
import androidx.compose.material3.SplitButtonDefaults.TrailingButton
import androidx.compose.material3.SplitButtonLayout
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.music_assistant.client.data.model.client.AppMediaItem
import io.music_assistant.client.data.model.server.QueueOption
import io.music_assistant.client.ui.compose.common.OverflowMenuButton
import io.music_assistant.client.ui.compose.common.OverflowMenuOption
import io.music_assistant.client.ui.compose.common.icons.PlayIcon
import musicassistantclient.composeapp.generated.resources.Res
import musicassistantclient.composeapp.generated.resources.action_add_to_queue
import musicassistantclient.composeapp.generated.resources.action_play
import musicassistantclient.composeapp.generated.resources.action_play_next
import musicassistantclient.composeapp.generated.resources.action_play_now
import musicassistantclient.composeapp.generated.resources.action_start_radio
import musicassistantclient.composeapp.generated.resources.cd_play_now
import musicassistantclient.composeapp.generated.resources.cd_play_options
import org.jetbrains.compose.resources.stringResource

@Composable
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
fun ItemPlayButton(
    item: AppMediaItem,
    onPlayClick: (QueueOption, Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    SplitButtonLayout(
        modifier = modifier,
        leadingButton = {
            val playNowText = stringResource(Res.string.cd_play_now)
            LeadingButton(
                modifier = Modifier.semantics { contentDescription = playNowText },
                onClick = { onPlayClick(QueueOption.REPLACE, false) },
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = PlayIcon,
                        contentDescription = null,
                    )
                    Text(modifier = Modifier.padding(start = 8.dp), text = stringResource(Res.string.action_play))
                }
            }
        },
        trailingButton = {
            PlayOverflow(
                item = item,
                onPlayClick = onPlayClick,
            ) { onClick ->
                TrailingButton(
                    onClick = onClick,
                ) {
                    Icon(
                        imageVector = Icons.Default.ExpandMore,
                        contentDescription = stringResource(Res.string.cd_play_options),
                    )
                }
            }
        },
    )
}

@Composable
private fun PlayOverflow(
    item: AppMediaItem,
    onPlayClick: (QueueOption, Boolean) -> Unit,
    button: @Composable (() -> Unit) -> Unit,
) {
    OverflowMenuButton(
        options = buildList {
            add(
                OverflowMenuOption(
                    title = stringResource(Res.string.action_play_now),
                    icon = Icons.Default.PlaylistAddCircle,
                ) { onPlayClick(QueueOption.PLAY, false) },
            )
            add(
                OverflowMenuOption(
                    title = stringResource(Res.string.action_play_next),
                    icon = Icons.Default.QueuePlayNext,
                ) {
                    onPlayClick(QueueOption.NEXT, false)
                },
            )
            add(
                OverflowMenuOption(
                    title = stringResource(Res.string.action_add_to_queue),
                    icon = Icons.Default.AddToQueue,
                ) {
                    onPlayClick(
                        QueueOption.ADD,
                        false,
                    )
                },
            )
            if (item.canStartRadio) {
                add(
                    OverflowMenuOption(
                        title = stringResource(Res.string.action_start_radio),
                        icon = Icons.Default.Radio,
                    ) {
                        onPlayClick(QueueOption.REPLACE, true)
                    },
                )
            }
        },
        buttonContent = button,
    )
}
