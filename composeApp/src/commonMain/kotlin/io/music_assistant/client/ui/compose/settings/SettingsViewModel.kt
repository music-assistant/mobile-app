package io.music_assistant.client.ui.compose.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.music_assistant.client.api.ConnectionInfo
import io.music_assistant.client.api.ServiceClient
import io.music_assistant.client.player.sendspin.audio.Codec
import io.music_assistant.client.settings.ConnectionHistoryEntry
import io.music_assistant.client.settings.SettingsRepository
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val apiClient: ServiceClient,
    private val settings: SettingsRepository
) : ViewModel() {

    val savedConnectionInfo = settings.connectionInfo
    val sessionState = apiClient.sessionState

    fun attemptConnection(host: String, port: String, isTls: Boolean) =
        apiClient.connect(connection = ConnectionInfo(host, port.toInt(), isTls))

    fun disconnect() {
        apiClient.disconnectByUser()
    }

    fun attemptWebRTCConnection(remoteId: String) {
        val parsed = io.music_assistant.client.webrtc.model.RemoteId.parse(remoteId)
        if (parsed != null) {
            apiClient.connectWebRTC(parsed)
        }
    }

    fun logout() {
        viewModelScope.launch {
            // Logout on server and clear token locally
            // MainDataSource will handle Sendspin lifecycle based on session state
            apiClient.logout()
        }
    }

    // Sendspin settings
    val sendspinEnabled = settings.sendspinEnabled
    val sendspinDeviceName = settings.sendspinDeviceName
    val sendspinUseCustomConnection = settings.sendspinUseCustomConnection
    val sendspinPort = settings.sendspinPort
    val sendspinPath = settings.sendspinPath
    val sendspinCodecPreference = settings.sendspinCodecPreference
    val sendspinHost = settings.sendspinHost
    val sendspinUseTls = settings.sendspinUseTls

    fun setSendspinEnabled(enabled: Boolean) = settings.setSendspinEnabled(enabled)
    fun setSendspinDeviceName(name: String) = settings.setSendspinDeviceName(name)
    fun setSendspinUseCustomConnection(enabled: Boolean) =
        settings.setSendspinUseCustomConnection(enabled)

    fun setSendspinPort(port: Int) = settings.setSendspinPort(port)
    fun setSendspinPath(path: String) = settings.setSendspinPath(path)
    fun setSendspinCodecPreference(codec: Codec) = settings.setSendspinCodecPreference(codec)
    fun setSendspinHost(host: String) = settings.setSendspinHost(host)
    fun setSendspinUseTls(enabled: Boolean) = settings.setSendspinUseTls(enabled)

    // Connection method preference
    val preferredConnectionMethod = settings.preferredConnectionMethod

    fun setPreferredConnectionMethod(method: String) = settings.setPreferredConnectionMethod(method)

    // WebRTC settings
    val webrtcRemoteId = settings.webrtcRemoteId

    fun setWebrtcRemoteId(remoteId: String) = settings.setWebrtcRemoteId(remoteId)

    val connectionHistory = settings.connectionHistory

    fun hasCredentialsForDirect(host: String, port: Int, isTls: Boolean): Boolean =
        settings.getTokenForServer(settings.getDirectServerIdentifier(host, port, isTls)) != null

    fun hasCredentialsForWebRTC(remoteId: String): Boolean =
        settings.getTokenForServer(settings.getWebRTCServerIdentifier(remoteId)) != null

    fun removeFromHistory(entry: ConnectionHistoryEntry) {
        settings.removeHistoryEntry(entry.serverIdentifier)
        settings.setTokenForServer(entry.serverIdentifier, null)
    }
}