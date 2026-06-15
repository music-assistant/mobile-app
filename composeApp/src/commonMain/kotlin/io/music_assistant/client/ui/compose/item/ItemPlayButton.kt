package io.music_assistant.client.ui.compose.item

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.music_assistant.client.data.model.client.ClickContext
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
import io.music_assistant.client.ui.contentColorByLuminance
import musicassistantclient.composeapp.generated.resources.Res
import musicassistantclient.composeapp.generated.resources.cd_play_options
import org.jetbrains.compose.resources.stringResource

@Composable
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
fun ItemPlayButton(
    item: AppMediaItem,
    onPlayClick: (QueueOption, Boolean) -> Unit,
    tint: Color = MaterialTheme.colorScheme.primary,
    modifier: Modifier = Modifier,
) {
    if (!item.isPlayable) return

    // Art-derived control tint as the button fill; black/white content per its luminance.
    val onTint = tint.contentColorByLuminance()
    val buttonColors = ButtonDefaults.buttonColors(
        containerColor = tint,
        contentColor = onTint,
    )

    // The detail header is wrapped in ProvideClickActions(DETAIL), so the config resolves
    // this item's DETAIL default. effectiveActionFor is non-null here (item is playable).
    val clickActionConfig = LocalClickActionConfig.current
    val effective = clickActionConfig.effectiveActionFor(item)
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
                colors = buttonColors,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = effective.icon(clickActionConfig.context), contentDescription = null)
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
                context = clickActionConfig.context,
            ) { onClick ->
                TrailingButton(onClick = onClick, colors = buttonColors) {
                    Icon(
                        imageVector = Icons.Default.ExpandMore,
                        contentDescription = stringResource(Res.string.cd_play_options),
                    )
                }
            }
        },
    )

    if (showCustomizeDialog && kind != null) {
        DefaultClickActionsDialog(
            itemKind = kind,
            onDismiss = { showCustomizeDialog = false },
        )
    }
}

@Composable
private fun PlayOverflow(
    item: AppMediaItem,
    default: ItemAction,
    onCustomize: (() -> Unit)?,
    onPlayAction: (ItemAction) -> Unit,
    context: ClickContext?,
    button: @Composable (() -> Unit) -> Unit,
) {
    val actions = resolvePlayButtonActions(item, default, onCustomize != null)
    val options = actions.map { action ->
        action.toOverflowOption(context) {
            if (it == ItemAction.Customize) onCustomize?.invoke() else onPlayAction(it)
        }
    }
    OverflowMenuButton(
        options = options,
        buttonContent = button,
    )
}
