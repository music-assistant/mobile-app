package io.music_assistant.client.ui.compose.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.music_assistant.client.api.ConnectionInfo
import io.music_assistant.client.api.ServiceClient
import io.music_assistant.client.player.sendspin.audio.Codec
import io.music_assistant.client.settings.SettingsRepository
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val apiClient: ServiceClient,
    private val settings: SettingsRepository
) : ViewModel() {

    val savedConnectionInfo = settings.connectionInfo
    val savedToken = settings.token
    val sessionState = apiClient.sessionState

    fun attemptConnection(host: String, port: String, isTls: Boolean) =
        apiClient.connect(connection = ConnectionInfo(host, port.toInt(), isTls))

    fun disconnect() {
        apiClient.disconnectByUser()
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
    val sendspinPort = settings.sendspinPort
    val sendspinPath = settings.sendspinPath
    val sendspinCodecPreference = settings.sendspinCodecPreference
    val sendspinHost = settings.sendspinHost
    val sendspinUseTls = settings.sendspinUseTls

    fun setSendspinEnabled(enabled: Boolean) = settings.setSendspinEnabled(enabled)
    fun setSendspinDeviceName(name: String) = settings.setSendspinDeviceName(name)
    fun setSendspinPort(port: Int) = settings.setSendspinPort(port)
    fun setSendspinPath(path: String) = settings.setSendspinPath(path)
    fun setSendspinCodecPreference(codec: Codec) = settings.setSendspinCodecPreference(codec)
    fun setSendspinHost(host: String) = settings.setSendspinHost(host)
    fun setSendspinUseTls(enabled: Boolean) = settings.setSendspinUseTls(enabled)
}