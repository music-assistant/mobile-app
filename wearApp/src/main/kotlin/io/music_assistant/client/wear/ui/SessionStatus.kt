package io.music_assistant.client.wear.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import io.music_assistant.client.wear.R
import io.music_assistant.client.wear.data.SessionState

/** What to show in place of a track title while there is nothing to play; null once connected. */
@Composable
fun SessionState.statusLabel(): String? = when (this) {
    is SessionState.Connected -> null
    SessionState.Idle, SessionState.Connecting -> stringResource(R.string.connecting)
    is SessionState.Reconnecting -> stringResource(R.string.reconnecting)
    is SessionState.Unauthorized -> stringResource(R.string.unauthorized)
    SessionState.NotConfigured -> stringResource(R.string.not_configured)
    SessionState.RemoteOnly -> stringResource(R.string.remote_only)
}

/** The second line under [statusLabel]: only states the user can act on explain themselves. */
@Composable
fun SessionState.statusDetail(): String? = when (this) {
    SessionState.NotConfigured -> stringResource(R.string.phone_sign_in_hint)
    SessionState.RemoteOnly -> stringResource(R.string.remote_only_hint)
    is SessionState.Unauthorized -> message
    else -> null
}
