package io.music_assistant.client.ui.compose.settings.sections

import androidx.compose.runtime.Composable
import io.music_assistant.client.api.ConnectionInfo
import io.music_assistant.client.ui.compose.settings.LoginSection
import io.music_assistant.client.ui.compose.settings.ServerInfoSection
import io.music_assistant.client.utils.SessionState

@Composable
fun ConnectionSettingsSection(
    sessionState: SessionState,
    connectionInfo: ConnectionInfo?,
    onDisconnect: () -> Unit,
) {
    if (sessionState is SessionState.Connected) {
        ServerInfoSection(
            connectionInfo = connectionInfo,
            serverInfo = sessionState.serverInfo,
            isWebRTC = sessionState is SessionState.Connected.WebRTC,
            onDisconnect = onDisconnect,
        )

        LoginSection(sessionState.user)
    }
}
