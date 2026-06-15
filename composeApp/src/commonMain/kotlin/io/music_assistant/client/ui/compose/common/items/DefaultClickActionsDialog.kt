package io.music_assistant.client.ui.compose.common.items

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.music_assistant.client.data.model.client.ClickContext
import io.music_assistant.client.data.model.client.ItemKind
import io.music_assistant.client.data.model.client.appearsIn
import io.music_assistant.client.settings.DefaultClickOption
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
        mutableStateMapOf<ClickContext, DefaultClickOption>().apply {
            val saved = stored[itemKind].orEmpty()
            contexts.forEach { put(it, saved[it] ?: DefaultClickOption.PLAY_NOW) }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                stringResource(Res.string.default_click_dialog_title) +
                    " — " + stringResource(itemKind.labelRes()),
            )
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                contexts.forEach { ctx ->
                    val options = remember(itemKind, ctx) {
                        DefaultClickOption.entries.filter { it.isAvailableIn(ctx, itemKind) }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = stringResource(ctx.label()),
                            modifier = Modifier.width(CTX_LABEL_WIDTH),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        ActionDropdown(
                            context = ctx,
                            options = options,
                            selected = selection[ctx] ?: DefaultClickOption.PLAY_NOW,
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
