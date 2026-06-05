package io.music_assistant.client.ui.compose.common.items

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.music_assistant.client.data.model.client.ClickContext
import io.music_assistant.client.data.model.client.ItemKind
import io.music_assistant.client.data.model.client.appearsIn
import io.music_assistant.client.settings.DefaultClickAction
import io.music_assistant.client.ui.compose.settings.DefaultClickActionsViewModel
import musicassistantclient.composeapp.generated.resources.Res
import musicassistantclient.composeapp.generated.resources.clickctx_album
import musicassistantclient.composeapp.generated.resources.clickctx_artist
import musicassistantclient.composeapp.generated.resources.clickctx_detail
import musicassistantclient.composeapp.generated.resources.clickctx_home
import musicassistantclient.composeapp.generated.resources.clickctx_library
import musicassistantclient.composeapp.generated.resources.clickctx_playlist
import musicassistantclient.composeapp.generated.resources.clickctx_search
import musicassistantclient.composeapp.generated.resources.common_cancel
import musicassistantclient.composeapp.generated.resources.default_click_dialog_save
import musicassistantclient.composeapp.generated.resources.default_click_dialog_title
import musicassistantclient.composeapp.generated.resources.kind_album
import musicassistantclient.composeapp.generated.resources.kind_artist
import musicassistantclient.composeapp.generated.resources.kind_audiobook
import musicassistantclient.composeapp.generated.resources.kind_playlist
import musicassistantclient.composeapp.generated.resources.kind_podcast
import musicassistantclient.composeapp.generated.resources.kind_podcast_episode
import musicassistantclient.composeapp.generated.resources.kind_radio
import musicassistantclient.composeapp.generated.resources.kind_track
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

private val CTX_LABEL_WIDTH = 64.dp

private fun ClickContext.label(): StringResource = when (this) {
    ClickContext.HOME -> Res.string.clickctx_home
    ClickContext.LIBRARY -> Res.string.clickctx_library
    ClickContext.ALBUM -> Res.string.clickctx_album
    ClickContext.PLAYLIST -> Res.string.clickctx_playlist
    ClickContext.ARTIST -> Res.string.clickctx_artist
    ClickContext.SEARCH -> Res.string.clickctx_search
    ClickContext.DETAIL -> Res.string.clickctx_detail
}

private fun ItemKind.label(): StringResource = when (this) {
    ItemKind.TRACK -> Res.string.kind_track
    ItemKind.RADIO -> Res.string.kind_radio
    ItemKind.PODCAST_EPISODE -> Res.string.kind_podcast_episode
    ItemKind.ALBUM -> Res.string.kind_album
    ItemKind.ARTIST -> Res.string.kind_artist
    ItemKind.PLAYLIST -> Res.string.kind_playlist
    ItemKind.PODCAST -> Res.string.kind_podcast
    ItemKind.AUDIOBOOK -> Res.string.kind_audiobook
}

/**
 * Customize dialog for a single item kind: one labelled action dropdown per context the
 * kind appears in. Self-contained — owns its ViewModel and persists this kind's table on
 * Save (other kinds untouched).
 */
@Composable
fun DefaultClickActionsDialog(itemKind: ItemKind, onDismiss: () -> Unit) {
    val viewModel = koinViewModel<DefaultClickActionsViewModel>()
    val stored by viewModel.actions.collectAsStateWithLifecycle()

    val contexts = remember(itemKind) { ClickContext.entries.filter { itemKind.appearsIn(it) } }

    // Local working copy; missing keys default to PLAY_NOW (the historic behavior).
    val selection = remember(itemKind) {
        mutableStateMapOf<ClickContext, DefaultClickAction>().apply {
            val saved = stored[itemKind].orEmpty()
            contexts.forEach { put(it, saved[it] ?: DefaultClickAction.PLAY_NOW) }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                stringResource(Res.string.default_click_dialog_title) +
                    " — " + stringResource(itemKind.label()),
            )
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                contexts.forEach { ctx ->
                    val options = remember(itemKind, ctx) {
                        DefaultClickAction.entries.filter { it.isAvailableIn(ctx, itemKind) }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = stringResource(ctx.label()),
                            modifier = Modifier.width(CTX_LABEL_WIDTH),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        ActionDropdown(
                            options = options,
                            selected = selection[ctx] ?: DefaultClickAction.PLAY_NOW,
                            onSelect = { selection[ctx] = it },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                viewModel.save(itemKind, selection.toMap())
                onDismiss()
            }) { Text(stringResource(Res.string.default_click_dialog_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(Res.string.common_cancel)) }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ActionDropdown(
    options: List<DefaultClickAction>,
    selected: DefaultClickAction,
    onSelect: (DefaultClickAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedAction = selected.toItemAction()
    val shape = RoundedCornerShape(4.dp)
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = modifier,
    ) {
        // Custom anchor (not OutlinedTextField): a read-only text field scrolls instead of
        // ellipsizing, so we build the outlined row ourselves to get true single-line ellipsis.
        Row(
            modifier = Modifier
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth()
                .clip(shape)
                .border(1.dp, MaterialTheme.colorScheme.outline, shape)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(selectedAction.icon(), contentDescription = null, modifier = Modifier.size(20.dp))
            Text(
                text = stringResource(selectedAction.title()),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f),
            )
            ExposedDropdownMenuDefaults.TrailingIcon(expanded)
        }
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { action ->
                val itemAction = action.toItemAction()
                DropdownMenuItem(
                    text = {
                        Text(
                            stringResource(itemAction.title()),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    leadingIcon = {
                        Icon(
                            itemAction.icon(),
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                        )
                    },
                    onClick = {
                        onSelect(action)
                        expanded = false
                    },
                )
            }
        }
    }
}
