package io.music_assistant.client.ui.compose.item

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.SplitButtonDefaults.LeadingButton
import androidx.compose.material3.SplitButtonDefaults.TrailingButton
import androidx.compose.material3.SplitButtonLayout
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.music_assistant.client.data.model.client.QueueOption
import io.music_assistant.client.data.model.client.itemKind
import io.music_assistant.client.data.model.client.items.AppMediaItem
import io.music_assistant.client.ui.compose.common.OverflowMenuButton
import io.music_assistant.client.ui.compose.common.items.DefaultClickActionsDialog
import io.music_assistant.client.ui.compose.common.items.ItemAction
import io.music_assistant.client.ui.compose.common.items.LocalClickActionConfig
import io.music_assistant.client.ui.compose.common.items.icon
import io.music_assistant.client.ui.compose.common.items.resolvePlayButtonActions
import io.music_assistant.client.ui.compose.common.items.title
import io.music_assistant.client.ui.compose.common.items.toOverflowOption
import musicassistantclient.composeapp.generated.resources.Res
import musicassistantclient.composeapp.generated.resources.cd_play_options
import org.jetbrains.compose.resources.stringResource

@Composable
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
fun ItemPlayButton(
    item: AppMediaItem,
    onPlayClick: (QueueOption, Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!item.isPlayable) return

    // The detail header is wrapped in ProvideClickActions(DETAIL), so the config resolves
    // this item's DETAIL default. effectiveActionFor is non-null here (item is playable).
    val effective = LocalClickActionConfig.current.effectiveActionFor(item)
        ?: ItemAction.Play(QueueOption.REPLACE)
    val kind = item.itemKind()

    var showCustomizeDialog by remember { mutableStateOf(false) }

    val runPlayAction: (ItemAction) -> Unit = { action ->
        when (action) {
            is ItemAction.Play -> onPlayClick(action.queueOption, false)
            ItemAction.StartRadio -> onPlayClick(QueueOption.REPLACE, true)
            else -> Unit
        }
    }

    SplitButtonLayout(
        modifier = modifier,
        leadingButton = {
            val label = stringResource(effective.title())
            LeadingButton(
                modifier = Modifier.semantics { contentDescription = label },
                onClick = { runPlayAction(effective) },
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = effective.icon(), contentDescription = null)
                    Text(modifier = Modifier.padding(start = 8.dp), text = label)
                }
            }
        },
        trailingButton = {
            PlayOverflow(
                item = item,
                default = effective,
                // Customize opens the per-kind table — meaningless for kindless items (e.g. Genre).
                onCustomize = if (kind != null) ({ showCustomizeDialog = true }) else null,
                onPlayAction = runPlayAction,
            ) { onClick ->
                TrailingButton(onClick = onClick) {
                    Icon(
                        imageVector = Icons.Default.ExpandMore,
                        contentDescription = stringResource(Res.string.cd_play_options),
                    )
                }
            }
        },
    )

    if (showCustomizeDialog && kind != null) {
        DefaultClickActionsDialog(itemKind = kind, onDismiss = { showCustomizeDialog = false })
    }
}

@Composable
private fun PlayOverflow(
    item: AppMediaItem,
    default: ItemAction,
    onCustomize: (() -> Unit)?,
    onPlayAction: (ItemAction) -> Unit,
    button: @Composable (() -> Unit) -> Unit,
) {
    val actions = resolvePlayButtonActions(item, default) +
        (onCustomize?.let { listOf(ItemAction.Customize) } ?: emptyList())
    val options = actions.map { action ->
        action.toOverflowOption {
            if (it == ItemAction.Customize) onCustomize?.invoke() else onPlayAction(it)
        }
    }
    OverflowMenuButton(
        options = options,
        buttonContent = button,
    )
}
