package io.music_assistant.client.ui.compose.common

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.music_assistant.client.api.ServiceClient
import io.music_assistant.client.data.MainDataSource
import io.music_assistant.client.ui.Timings
import io.music_assistant.client.ui.compose.common.BannerState.NoNetwork
import io.music_assistant.client.ui.compose.common.BannerState.Reconnecting
import io.music_assistant.client.utils.SessionState
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import musicassistantclient.composeapp.generated.resources.Res
import musicassistantclient.composeapp.generated.resources.banner_no_network
import musicassistantclient.composeapp.generated.resources.banner_reconnecting
import musicassistantclient.composeapp.generated.resources.common_cancel
import musicassistantclient.composeapp.generated.resources.reconnect_dialog_message
import musicassistantclient.composeapp.generated.resources.reconnect_dialog_stop
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

/**
 * Global reconnection indicator. While the local player still plays its buffer, a banner keeps
 * the screen usable, so the local pause still works. Otherwise an uncancelable dialog covers
 * every screen until the session is back or the user stops the retries.
 */
@Composable
fun ConnectionStatusOverlay(
    modifier: Modifier = Modifier,
    delay: Long = Timings.UI_RETRY_DEBOUNCE,
) {
    val serviceClient: ServiceClient = koinInject()
    val dataSource: MainDataSource = koinInject()
    val sessionState by serviceClient.sessionState.collectAsStateWithLifecycle()
    val isLocalPlaying by remember(dataSource) {
        dataSource.localPlayer.map { it?.player?.isPlaying == true }.distinctUntilChanged()
    }.collectAsStateWithLifecycle(initialValue = false)

    val bannerState = reconnectionBannerState(sessionState)

    // Delay visibility to not spam in cases reconnecting is fast
    var isVisible by remember { mutableStateOf(false) }
    if (bannerState != null) {
        LaunchedEffect(Unit) {
            delay(delay)
            isVisible = true
        }
    } else {
        isVisible = false
    }

    val statusText = bannerState?.let { banner ->
        when (banner) {
            is BannerState.Reconnecting -> stringResource(Res.string.banner_reconnecting, banner.attempt)
            BannerState.NoNetwork -> stringResource(Res.string.banner_no_network)
        }
    }

    AnimatedVisibility(
        visible = isVisible && isLocalPlaying,
        enter = expandVertically(),
        exit = shrinkVertically(),
    ) {
        statusText?.let { text ->
            StatusBanner(
                text = text,
                onCancel = { serviceClient.disconnectByUser() },
                modifier = modifier,
            )
        }
    }

    if (isVisible && !isLocalPlaying) {
        statusText?.let { text ->
            ReconnectingDialog(
                status = text,
                onStop = { serviceClient.disconnectByUser() },
            )
        }
    }
}

internal sealed interface BannerState {
    data class Reconnecting(val attempt: Int) : BannerState
    data object NoNetwork : BannerState
}

// While reconnecting, offline means the loop is parked waiting for network, not retrying.
internal fun reconnectionBannerState(sessionState: SessionState): BannerState? =
    when (sessionState) {
        is SessionState.Reconnecting -> if (sessionState.isOnline) {
            Reconnecting(sessionState.attempt)
        } else {
            NoNetwork
        }

        else -> null
    }

@Composable
private fun StatusBanner(
    text: String,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.secondaryContainer)
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.padding(end = 8.dp).size(16.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
            TextButton(
                onClick = onCancel,
            ) {
                Text(
                    text = stringResource(Res.string.common_cancel),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
        }
    }
}

// No dismiss path: the session ends this dialog, or the user stops the retries.
@Composable
private fun ReconnectingDialog(
    status: String,
    onStop: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = {},
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
        icon = { CircularProgressIndicator() },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = stringResource(Res.string.reconnect_dialog_message),
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = status,
                    modifier = Modifier.padding(top = 8.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onStop) {
                Text(stringResource(Res.string.reconnect_dialog_stop))
            }
        },
    )
}
