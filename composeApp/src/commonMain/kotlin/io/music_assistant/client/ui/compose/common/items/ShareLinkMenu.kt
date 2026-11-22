package io.music_assistant.client.ui.compose.common.items

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.music_assistant.client.data.model.client.MediaType
import io.music_assistant.client.data.model.client.items.AppMediaItem
import io.music_assistant.client.data.model.server.SHARE_LINKS_DOMAIN
import io.music_assistant.client.data.model.server.Scope
import io.music_assistant.client.data.model.server.supportsShareLinks
import io.music_assistant.client.data.repository.ProviderRepository
import io.music_assistant.client.data.repository.ShareRepository
import io.music_assistant.client.ui.compose.common.LocalGrantedScopes
import io.music_assistant.client.ui.compose.common.OverflowMenuOption
import io.music_assistant.client.utils.HasConnectionData
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import musicassistantclient.composeapp.generated.resources.Res
import musicassistantclient.composeapp.generated.resources.action_share_item
import musicassistantclient.composeapp.generated.resources.common_dismiss
import musicassistantclient.composeapp.generated.resources.share_link_failed
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

@Composable
expect fun rememberShareItem(): (String, String) -> Unit

@Composable
fun rememberShareLinkLauncher(): ((String, String) -> Unit)? {
    if (LocalInspectionMode.current || !LocalGrantedScopes.current.has(Scope.LIBRARY_READ)) return null

    val repository = koinInject<ShareRepository>()
    val session by repository.sessionState.collectAsStateWithLifecycle()
    val ready by repository.isReady.collectAsStateWithLifecycle()
    val schema = (session as? HasConnectionData)?.serverInfo?.schemaVersion
    if (!ready || !supportsShareLinks(schema)) return null

    val providers by koinInject<ProviderRepository>().providers.collectAsStateWithLifecycle()
    if (providers.dataOrNull.orEmpty().none { it.domain == SHARE_LINKS_DOMAIN && it.available }) return null

    return key(session) { rememberGenerateShareLink(repository) }
}

@Composable
private fun rememberGenerateShareLink(repository: ShareRepository): (String, String) -> Unit {
    val scope = rememberCoroutineScope()
    val share = rememberShareItem()
    var pending by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    if (failed) {
        AlertDialog(
            onDismissRequest = { failed = false },
            text = { Text(stringResource(Res.string.share_link_failed)) },
            confirmButton = {
                TextButton(onClick = { failed = false }) { Text(stringResource(Res.string.common_dismiss)) }
            },
        )
    }
    return { uri, title ->
        if (!pending) {
            pending = true
            scope.launch {
                try {
                    val url = repository.generateLink(uri)
                    ensureActive()
                    if (url == null) failed = true else share(url, title)
                } finally {
                    pending = false
                }
            }
        }
    }
}

@Composable
fun shareLinkMenuOption(item: AppMediaItem?, launchShare: (String, String) -> Unit): OverflowMenuOption? {
    val uri = item?.uri?.takeIf { it.isNotBlank() } ?: return null
    when (item.mediaType) {
        MediaType.ARTIST, MediaType.ALBUM, MediaType.TRACK -> Unit
        else -> return null
    }
    return OverflowMenuOption(
        title = stringResource(Res.string.action_share_item),
        icon = Icons.Default.Share,
        onClick = { launchShare(uri, item.name) },
    )
}
