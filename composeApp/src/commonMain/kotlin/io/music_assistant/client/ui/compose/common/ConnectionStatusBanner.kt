package io.music_assistant.client.ui.compose.common

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.music_assistant.client.api.ServiceClient
import io.music_assistant.client.utils.SessionState
import org.koin.compose.koinInject

/**
 * Global banner that shows reconnection status indicator.
 */
@Composable
fun ConnectionStatusBanner(
    modifier: Modifier = Modifier
) {
    val serviceClient: ServiceClient = koinInject()
    val sessionState by serviceClient.sessionState.collectAsStateWithLifecycle()

    // Determine banner state
    // Only show banner during reconnection attempts
    // On max attempts reached, navigate to Settings instead (handled in AppNavigation)
    val bannerState = when (sessionState) {
        is SessionState.Reconnecting -> {
            val attempt = (sessionState as SessionState.Reconnecting).attempt
            BannerState.Reconnecting(attempt)
        }

        else -> null
    }

    AnimatedVisibility(
        visible = bannerState != null,
        enter = expandVertically(),
        exit = shrinkVertically()
    ) {
        bannerState?.let { state ->
            ReconnectingBanner(
                attempt = state.attempt,
                onCancel = { serviceClient.disconnectByUser() },
                modifier = modifier
            )
        }
    }
}

private sealed interface BannerState {
    data class Reconnecting(val attempt: Int) : BannerState
}

@Composable
private fun ReconnectingBanner(
    attempt: Int,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.secondaryContainer)
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.padding(end = 8.dp).size(16.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
                Text(
                    text = "Reconnecting... (attempt $attempt)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
            androidx.compose.material3.TextButton(
                onClick = onCancel
            ) {
                Text(
                    text = "Cancel",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
        }
    }
}
