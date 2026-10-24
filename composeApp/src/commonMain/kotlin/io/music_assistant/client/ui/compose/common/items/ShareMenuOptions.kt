package io.music_assistant.client.ui.compose.common.items

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.music_assistant.client.data.model.server.Scope
import io.music_assistant.client.data.model.server.ServerShareItem
import io.music_assistant.client.data.repository.ShareRepository
import io.music_assistant.client.data.repository.supportsSharing
import io.music_assistant.client.ui.compose.common.LocalGrantedScopes
import io.music_assistant.client.ui.compose.common.OverflowMenuOption
import io.music_assistant.client.ui.compose.provider.ProviderViewModel
import kotlinx.coroutines.CancellationException
import musicassistantclient.composeapp.generated.resources.Res
import musicassistantclient.composeapp.generated.resources.action_share_item
import musicassistantclient.composeapp.generated.resources.action_share_provider_link
import musicassistantclient.composeapp.generated.resources.share_cancel
import musicassistantclient.composeapp.generated.resources.share_failed
import musicassistantclient.composeapp.generated.resources.share_loading
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

/** URL and text are distinct so native platforms preserve the intended activity type. */
data class SharePayload(val value: String, val title: String, val isUrl: Boolean)

fun ServerShareItem.singlePayload(): SharePayload? = when {
    links.size == 1 -> SharePayload(links.single().url, title, true)
    links.isEmpty() -> text?.takeIf { it.isNotBlank() }?.let { SharePayload(it, title, false) }
    else -> null
}

@Composable
expect fun rememberShareItem(): (SharePayload) -> Unit

@Composable
fun shareMenuOptions(
    uri: String?,
    active: Boolean,
): List<OverflowMenuOption> {
    if (LocalInspectionMode.current || !LocalGrantedScopes.current.has(Scope.LIBRARY_READ)) return emptyList()
    val repository = koinInject<ShareRepository>()
    val providers = koinViewModel<ProviderViewModel>()
    val session by repository.sessionState.collectAsStateWithLifecycle()
    if (!session.supportsSharing()) return emptyList()
    return key(session) {
        shareMenuOptions(uri, active, repository::shareItem) { instance, domain ->
            providers.getProviderDetails(instance)?.name ?: providers.getProviderDetails(domain)?.name ?: domain
        }
    }
}

@Composable
fun shareMenuOptions(
    uri: String?,
    active: Boolean,
    load: suspend (String) -> ServerShareItem?,
    providerName: (String, String) -> String,
): List<OverflowMenuOption> {
    var response by remember(uri, active) { mutableStateOf<ServerShareItem?>(null) }
    var failed by remember(uri, active) { mutableStateOf(false) }
    var chooser by remember(uri) { mutableStateOf<ServerShareItem?>(null) }
    val share = rememberShareItem()
    LaunchedEffect(uri, active) {
        if (active && !uri.isNullOrBlank()) {
            response = try {
                load(uri)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                null
            }
            failed = response == null || (response?.links.isNullOrEmpty() && response?.singlePayload() == null)
        }
    }
    chooser?.let { item ->
        AlertDialog(
            onDismissRequest = { chooser = null },
            title = { Text(stringResource(Res.string.action_share_item)) },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    item.links.forEach { link ->
                        val name = providerName(link.providerInstance, link.providerDomain)
                        TextButton(onClick = {
                            share(SharePayload(link.url, item.title, true))
                            chooser = null
                        }) { Text(stringResource(Res.string.action_share_provider_link, name)) }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { chooser = null }) { Text(stringResource(Res.string.share_cancel)) }
            },
        )
    }
    if (!active || uri.isNullOrBlank()) return emptyList()
    val item = response
    return listOf(
        OverflowMenuOption(
            title = stringResource(
                when {
                    failed -> Res.string.share_failed
                    item == null -> Res.string.share_loading
                    else -> Res.string.action_share_item
                },
            ),
            icon = Icons.Default.Share,
            enabled = item != null && !failed,
            onClick = {
                item?.singlePayload()?.let(share) ?: run { chooser = item }
            },
        ),
    )
}
