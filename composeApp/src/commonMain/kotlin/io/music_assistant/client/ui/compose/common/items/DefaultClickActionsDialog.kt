package io.music_assistant.client.ui.compose.common.items

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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

private val ACTION_COL_WIDTH = 168.dp
private val CTX_COL_WIDTH = 60.dp

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
 * Matrix picker for a single item kind: rows = play actions applicable to [itemKind],
 * columns = contexts, one default per column. Self-contained — owns its ViewModel and
 * persists this kind's table on Save (other kinds untouched).
 */
@Composable
fun DefaultClickActionsDialog(itemKind: ItemKind, onDismiss: () -> Unit) {
    val viewModel = koinViewModel<DefaultClickActionsViewModel>()
    val stored by viewModel.actions.collectAsStateWithLifecycle()

    val rows = remember(itemKind) { DefaultClickAction.entries.filter { it.appliesTo(itemKind) } }
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
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                // Header: empty corner + context labels.
                Row(verticalAlignment = Alignment.Bottom) {
                    Spacer(Modifier.width(ACTION_COL_WIDTH))
                    contexts.forEach { ctx ->
                        Text(
                            text = stringResource(ctx.label()),
                            modifier = Modifier.width(CTX_COL_WIDTH),
                            textAlign = TextAlign.Center,
                            fontSize = 11.sp,
                        )
                    }
                }
                rows.forEach { action ->
                    val itemAction = action.toItemAction()
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Row(
                            modifier = Modifier.width(ACTION_COL_WIDTH),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Icon(
                                imageVector = itemAction.icon(),
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                            )
                            Text(text = stringResource(itemAction.title()), fontSize = 13.sp)
                        }
                        contexts.forEach { ctx ->
                            RadioButton(
                                selected = selection[ctx] == action,
                                onClick = { selection[ctx] = action },
                                enabled = action.isAvailableIn(ctx, itemKind),
                                modifier = Modifier.width(CTX_COL_WIDTH),
                            )
                        }
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
