package io.music_assistant.client.ui.compose.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.music_assistant.client.api.ConnectionInfo
import io.music_assistant.client.api.ServiceClient
import io.music_assistant.client.logging.InMemoryLogWriter
import io.music_assistant.client.logging.LogSharer
import io.music_assistant.client.player.sendspin.audio.Codec
import io.music_assistant.client.settings.ConnectionHistoryEntry
import io.music_assistant.client.settings.ConnectionType
import io.music_assistant.client.settings.SettingsRepository
import io.music_assistant.client.utils.LocalNetworkPermissionGate
import io.music_assistant.client.utils.localNetworkPermissionGateExists
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// Grants/denials resolve immediately; users reading the prompt and mDNS-blocked
// networks are the contenders to actually hit this
private const val CONNECT_PROBE_TIMEOUT_MS = 15_000L

class SettingsViewModel(
    private val apiClient: ServiceClient,
    private val settings: SettingsRepository,
    private val logSharer: LogSharer,
    private val localNetworkPermissionGate: LocalNetworkPermissionGate,
) : ViewModel() {
    val savedConnectionInfo = settings.connectionInfo
    val sessionState = apiClient.sessionState

    private val _hasCrashLog = MutableStateFlow(logSharer.hasCrashLog())
    val hasCrashLog: StateFlow<Boolean> = _hasCrashLog

    private val _isPreparingShare = MutableStateFlow(false)
    val isPreparingShare: StateFlow<Boolean> = _isPreparingShare

    // Local Network preflight: true when the last probe reported the permission denied.
    private val _localNetworkBlocked = MutableStateFlow(false)
    val localNetworkBlocked: StateFlow<Boolean> = _localNetworkBlocked

    // Outcome of the last preflight probe; null when it never ran or was inconclusive.
    private val _lastLocalNetworkProbeGranted = MutableStateFlow<Boolean?>(null)
    val lastLocalNetworkProbeGranted: StateFlow<Boolean?> = _lastLocalNetworkProbeGranted

    private var attemptJob: Job? = null

    // True while a connect attempt (probe + connect) is in flight; gates the Connect button.
    private val _connectAttemptInFlight = MutableStateFlow(false)
    val connectAttemptInFlight: StateFlow<Boolean> = _connectAttemptInFlight

    fun shareLogs(chooserTitle: String) {
        if (_isPreparingShare.value) return
        viewModelScope.launch {
            _isPreparingShare.value = true
            try {
                val path = withContext(Dispatchers.Default) {
                    logSharer.prepareLogShareFile(InMemoryLogWriter.getLogText())
                }
                logSharer.presentShareFile(path, chooserTitle)
            } finally {
                _isPreparingShare.value = false
            }
        }
    }

    fun shareCrashLog(chooserTitle: String) {
        if (_isPreparingShare.value) return
        viewModelScope.launch {
            _isPreparingShare.value = true
            try {
                val path = withContext(Dispatchers.Default) {
                    logSharer.prepareCrashLogShareFile()
                }
                if (path != null) logSharer.presentShareFile(path, chooserTitle)
            } finally {
                _isPreparingShare.value = false
            }
        }
    }

    fun deleteCrashLog() {
        logSharer.deleteCrashLog()
        _hasCrashLog.value = false
    }

    fun attemptConnection(host: String, port: String, isTls: Boolean, basePath: String) {
        // Single-flight: a second tap (or the auto-retry) cancels any in-flight probe
        // instead of racing it for the blocked/granted state flows.
        attemptJob?.cancel()
        attemptJob = viewModelScope.launch {
            _connectAttemptInFlight.value = true
            try {
                _localNetworkBlocked.value = false
                _lastLocalNetworkProbeGranted.value = null
                val portNum = port.toIntOrNull() ?: return@launch
                // Credentials for this address prove a prior direct connect succeeded, which
                // requires the permission — probe only when they are absent.
                val knownServer = hasCredentialsForDirect(host, portNum, isTls, basePath)
                if (localNetworkPermissionGateExists && !knownServer) {
                    // Raises the permission prompt when not yet determined and waits out
                    // the answer; denied is reported distinctly from "offline".
                    val granted = localNetworkPermissionGate.probe(CONNECT_PROBE_TIMEOUT_MS)
                    _lastLocalNetworkProbeGranted.value = granted
                    if (granted == false) {
                        _localNetworkBlocked.value = true
                        return@launch
                    }
                } else if (knownServer) {
                    _lastLocalNetworkProbeGranted.value = true
                }
                apiClient.connect(
                    connection = ConnectionInfo(
                        host = host,
                        port = portNum,
                        isTls = isTls,
                        basePath = ConnectionInfo.normalizeBasePath(basePath),
                    ),
                )
            } finally {
                _connectAttemptInFlight.value = false
            }
        }
    }

    fun disconnect() {
        attemptJob?.cancel()
        attemptJob = null
        apiClient.disconnectByUser()
    }

    fun attemptWebRTCConnection(remoteId: String) {
        // Clear direct-connect probe state so a stale blocked message can't render
        // under a WebRTC failure.
        _localNetworkBlocked.value = false
        _lastLocalNetworkProbeGranted.value = null
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

    // Misc settings
    val allowLandscapeOnAllDevices = settings.allowLandscapeOnAllDevices

    fun setAllowLandscapeOnAllDevices(enabled: Boolean) =
        settings.setAllowLandscapeOnAllDevices(enabled)

    // Sendspin settings
    val sendspinEnabled = settings.sendspinEnabled
    val sendspinRequireEncryption = settings.sendspinRequireEncryption
    val sendspinDeviceName = settings.sendspinDeviceName
    val sendspinUseCustomConnection = settings.sendspinUseCustomConnection
    val sendspinPort = settings.sendspinPort
    val sendspinPath = settings.sendspinPath
    val sendspinCodecPreference = settings.sendspinCodecPreference
    val sendspinBufferCapacityMb = settings.sendspinBufferCapacityMb
    val sendspinHost = settings.sendspinHost
    val sendspinUseTls = settings.sendspinUseTls

    fun setSendspinRequireEncryption(enabled: Boolean) =
        settings.setSendspinRequireEncryption(enabled)
    fun setSendspinEnabled(enabled: Boolean) = settings.setSendspinEnabled(enabled)
    fun setSendspinDeviceName(name: String) = settings.setSendspinDeviceName(name)
    fun setSendspinUseCustomConnection(enabled: Boolean) =
        settings.setSendspinUseCustomConnection(enabled)

    fun setSendspinPort(port: Int) = settings.setSendspinPort(port)
    fun setSendspinPath(path: String) = settings.setSendspinPath(path)
    fun setSendspinCodecPreference(codec: Codec) = settings.setSendspinCodecPreference(codec)
    fun setSendspinBufferCapacityMb(mb: Int) = settings.setSendspinBufferCapacityMb(mb)
    fun setSendspinHost(host: String) = settings.setSendspinHost(host)
    fun setSendspinUseTls(enabled: Boolean) = settings.setSendspinUseTls(enabled)

    // Connection method preference
    val preferredConnectionMethod = settings.preferredConnectionMethod

    fun setPreferredConnectionMethod(method: String) = settings.setPreferredConnectionMethod(method)

    // WebRTC settings
    val webrtcRemoteId = settings.webrtcRemoteId

    fun setWebrtcRemoteId(remoteId: String) = settings.setWebrtcRemoteId(remoteId)

    val connectionHistory = settings.connectionHistory

    fun hasCredentialsForDirect(host: String, port: Int, isTls: Boolean, basePath: String): Boolean =
        settings.hasCredentialsForAddress(
            ConnectionHistoryEntry(
                type = ConnectionType.DIRECT,
                host = host,
                port = port,
                isTls = isTls,
                basePath = ConnectionInfo.normalizeBasePath(basePath),
            ).serverIdentifier,
        )

    fun hasCredentialsForWebRTC(remoteId: String): Boolean =
        settings.hasCredentialsForAddress(
            ConnectionHistoryEntry(type = ConnectionType.WEBRTC, remoteId = remoteId).serverIdentifier,
        )

    fun removeFromHistory(entry: ConnectionHistoryEntry) {
        settings.removeHistoryEntry(entry.historyKey)
        entry.serverId?.let { settings.setTokenForServer(it, null) }
    }

    // Local Network onboarding
    val localNetworkOnboardingShown = settings.localNetworkOnboardingShown

    fun dismissLocalNetworkOnboarding() = settings.setLocalNetworkOnboardingShown()
}
