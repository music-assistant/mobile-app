package io.music_assistant.client.ui.compose.common.items

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.music_assistant.client.data.model.client.MediaType
import io.music_assistant.client.data.model.client.items.AppMediaItem
import io.music_assistant.client.data.model.server.SHARE_LINKS_DOMAIN
import io.music_assistant.client.data.model.server.Scope
import io.music_assistant.client.data.model.server.supportsShareLinks
import io.music_assistant.client.data.repository.ProviderRepository
import io.music_assistant.client.data.repository.ShareRepository
import io.music_assistant.client.data.share.providerLinksFromShareUrl
import io.music_assistant.client.ui.compose.common.LocalGrantedScopes
import io.music_assistant.client.ui.compose.common.OverflowMenuOption
import io.music_assistant.client.ui.compose.common.providers.ProviderIcon
import io.music_assistant.client.ui.compose.provider.ProviderViewModel
import io.music_assistant.client.utils.HasConnectionData
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import musicassistantclient.composeapp.generated.resources.Res
import musicassistantclient.composeapp.generated.resources.action_share_item
import musicassistantclient.composeapp.generated.resources.common_cancel
import musicassistantclient.composeapp.generated.resources.common_dismiss
import musicassistantclient.composeapp.generated.resources.share_link_failed
import musicassistantclient.composeapp.generated.resources.share_music_assistant_link
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

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

    val names = providers.dataOrNull.orEmpty().associate { it.domain to it.name }
    return key(session) { rememberGenerateShareLink(repository, names) }
}

private data class ShareChoices(val title: String, val url: String, val links: Map<String, String>)

@Composable
private fun rememberGenerateShareLink(
    repository: ShareRepository,
    providerNames: Map<String, String>,
): (String, String) -> Unit {
    val scope = rememberCoroutineScope()
    val share = rememberShareItem()
    var pending by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    var choices by remember { mutableStateOf<ShareChoices?>(null) }
    choices?.let { item ->
        val shareChoice: (String) -> Unit = { url ->
            choices = null
            share(url, item.title)
        }
        AlertDialog(
            onDismissRequest = { choices = null },
            title = { Text(stringResource(Res.string.action_share_item)) },
            text = {
                val destinations: List<Pair<String?, String>> = item.links.toList() + (null to item.url)
                Column(
                    modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    destinations.chunked(2).forEach { row ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            row.forEach { (domain, url) ->
                                val name = when (domain) {
                                    null -> stringResource(Res.string.share_music_assistant_link)
                                    "ytmusic" -> "YouTube Music"
                                    else -> providerNames[domain] ?: domain
                                }
                                ShareDestination(name, { shareChoice(url) }, Modifier.weight(1f)) {
                                    if (domain == null) {
                                        Icon(
                                            Icons.Default.Link,
                                            contentDescription = null,
                                            modifier = Modifier.size(26.dp),
                                        )
                                    } else {
                                        ShareProviderIcon(domain)
                                    }
                                }
                            }
                            if (row.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { choices = null }) { Text(stringResource(Res.string.common_cancel)) }
            },
        )
    }
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
                    if (url == null) {
                        failed = true
                    } else {
                        val links = providerLinksFromShareUrl(url)
                        if (links.isEmpty()) share(url, title) else choices = ShareChoices(title, url, links)
                    }
                } finally {
                    pending = false
                }
            }
        }
    }
}

@Composable
private fun ShareDestination(
    name: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: @Composable () -> Unit,
) {
    TextButton(onClick = onClick, modifier = modifier) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            icon()
            Text(name, style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun ShareProviderIcon(domain: String) {
    val providers = koinViewModel<ProviderViewModel>()
    val variant = if (MaterialTheme.colorScheme.surface.luminance() < 0.5f) "dark" else "light"
    val icon by remember(providers, domain, variant) {
        providers.getProviderIcon(domain, variant)
    }.collectAsStateWithLifecycle()
    val size = Modifier.size(26.dp)
    icon?.let { ProviderIcon(size, it, contentDescription = null) }
        ?: Icon(Icons.Default.Link, contentDescription = null, modifier = size)
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
