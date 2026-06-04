package io.music_assistant.client.ui.compose.common.items

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import io.music_assistant.client.ui.compose.common.OverflowMenuOption
import musicassistantclient.composeapp.generated.resources.Res
import musicassistantclient.composeapp.generated.resources.menu_default_label
import org.jetbrains.compose.resources.stringResource

/**
 * Renders [actions] as [DropdownMenuItem]s, inserting a single [HorizontalDivider]
 * between the trailing [ItemAction.Kind.PLAYBACK] entry and the first
 * [ItemAction.Kind.OTHER] entry. No divider if either group is empty.
 *
 * Must be called inside a Material 3 menu container (e.g. `DropdownMenu { ... }`).
 */
@Composable
fun ColumnScope.itemActionMenuItems(
    actions: List<ItemAction>,
    defaultAction: ItemAction? = null,
    onAction: (ItemAction) -> Unit,
) {
    val lastPlayback = actions.indexOfLast { it.kind == ItemAction.Kind.PLAYBACK }
    val firstOther = actions.indexOfFirst { it.kind == ItemAction.Kind.OTHER }
    val dividerAfter = if (lastPlayback >= 0 && firstOther > lastPlayback) lastPlayback else -1

    actions.forEachIndexed { index, action ->
        val title = stringResource(action.title())
        DropdownMenuItem(
            text = {
                if (action == defaultAction) {
                    Column {
                        Text(title)
                        Text(
                            text = stringResource(Res.string.menu_default_label),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                } else {
                    Text(title)
                }
            },
            onClick = { onAction(action) },
            leadingIcon = {
                Icon(imageVector = action.icon(), contentDescription = title)
            },
        )
        if (index == dividerAfter) HorizontalDivider()
    }
}

@Composable
fun ItemAction.toOverflowOption(onAction: (ItemAction) -> Unit): OverflowMenuOption {
    val action = this
    return OverflowMenuOption(
        title = stringResource(title()),
        icon = icon(),
        onClick = { onAction(action) },
    )
}
