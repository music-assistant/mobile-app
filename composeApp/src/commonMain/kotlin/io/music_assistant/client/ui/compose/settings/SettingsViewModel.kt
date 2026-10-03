package io.music_assistant.client.ui.compose.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.music_assistant.client.api.ConnectionInfo
import io.music_assistant.client.api.ServiceClient
import io.music_assistant.client.logging.InMemoryLogWriter
import io.music_assistant.client.logging.LogSharer
import io.music_assistant.client.settings.ConnectionHistoryEntry
import io.music_assistant.client.settings.ConnectionType
import io.music_assistant.client.settings.SettingsRepository
import io.music_assistant.client.utils.AppVersion
import io.music_assistant.client.utils.LocalNetworkOnboardingResources
import io.music_assistant.client.utils.LocalNetworkPermissionGate
import io.music_assistant.sendspin.api.AudioCodec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.StringResource

// Grants/denials resolve immediately; users reading the prompt and mDNS-blocked
// networks are the contenders to actually hit this
private const val CONNECT_PROBE_TIMEOUT_MS = 15_000L

class SettingsViewModel(
    private val apiClient: ServiceClient,
    private val settings: SettingsRepository,
    private val logSharer: LogSharer,
    private val localNetworkPermissionGate: LocalNetworkPermissionGate,
    val appVersion: AppVersion,
) : ViewModel() {
    val savedConnectionInfo = settings.connectionInfo
    val sessionState = apiClient.sessionState
    val localNetworkOnboardingResources: LocalNetworkOnboardingResources? =
        localNetworkPermissionGate.onboardingResources

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
                if (localNetworkPermissionGate.isAvailable && !knownServer) {
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

    fun isLikelyLocalNetworkBlocked(error: Throwable): Boolean =
        localNetworkPermissionGate.isLikelyLocalNetworkBlocked(error)

    fun localNetworkErrorGuidance(
        error: Throwable?,
        probeGranted: Boolean?,
        locallyBlocked: Boolean,
    ): StringResource? = localNetworkPermissionGate.guidanceFor(
        error = error,
        probeGranted = probeGranted,
        locallyBlocked = locallyBlocked,
    )

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

    fun setSendspinEnabled(enabled: Boolean) = settings.setSendspinEnabled(enabled)

    val sendspinPlayerSettings: StateFlow<SendspinPlayerSettings> = combine(
        combine(
            settings.sendspinUseCustomConnection,
            settings.sendspinUseTls,
            settings.sendspinHost,
            settings.sendspinPort,
            settings.sendspinPath,
        ) { customConnectionEnabled, useTls, host, port, path ->
            SendspinConnectionSettings(
                enabled = customConnectionEnabled,
                tls = useTls,
                host = host,
                port = port,
                path = path,
            )
        },
        settings.sendspinDeviceName,
        settings.sendspinBufferCapacityMb,
        settings.sendspinCodecPreference,
    ) { connectionOverride, name, bufferCapacityMb, codecPreference ->
        SendspinPlayerSettings(
            name = name,
            bufferCapacityMb = bufferCapacityMb,
            codecPreference = codecPreference,
            connectionOverride = connectionOverride,
        )
    }
    .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = currentSendspinPlayerSettings(),
    )

    private val _sendspinDraftSettings = MutableStateFlow(sendspinPlayerSettings.value.deepCopy())
    val sendspinDraftSettings: StateFlow<SendspinPlayerSettings> = _sendspinDraftSettings

    val isSendspinSettingsSavable: StateFlow<Boolean> = combine(
        sendspinDraftSettings,
        sendspinPlayerSettings,
    ) { draft, persisted ->
        draft != persisted
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = false,
    )

    val isSendspinSettingsResettable: StateFlow<Boolean> = combine(
        sendspinDraftSettings,
        sendspinPlayerSettings,
    ) { draft, _ ->
        draft != defaultSendspinPlayerSettings()
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = sendspinDraftSettings.value != defaultSendspinPlayerSettings(),
    )

    init {
        viewModelScope.launch {
            sendspinPlayerSettings.collectLatest { persistedSettings ->
                _sendspinDraftSettings.value = persistedSettings.deepCopy()
            }
        }
    }

    fun setSendspinEnabledWithDraftCommit(enabled: Boolean) {
        if (enabled) {
            commitSendspinDraftSettings()
        }
        setSendspinEnabled(enabled)
    }

    fun updateSendspinDraftSettings(updateFunction: SendspinPlayerSettings.() -> Unit) {
        _sendspinDraftSettings.value = _sendspinDraftSettings.value
            .deepCopy()
            .apply(updateFunction)

        if (!sendspinEnabled.value) {
            commitSendspinDraftSettings()
        }
    }

    fun resetSendspinDraftSettings() {
        _sendspinDraftSettings.value = defaultSendspinPlayerSettings()
    }

    fun commitSendspinDraftSettings() {
        setSendspinPlayerSettings(_sendspinDraftSettings.value)
    }

    fun setSendspinPlayerSettings(playerSettings: SendspinPlayerSettings) {
        settings.setSendspinDeviceName(playerSettings.name ?: SettingsRepository.DEVICE_NAME)
        settings.setSendspinBufferCapacityMb(playerSettings.bufferCapacityMb ?: SettingsRepository.BUFFER_MB_DEFAULT)
        settings.setSendspinCodecPreference(playerSettings.codecPreference ?: AudioCodec.OPUS)
        settings.setSendspinUseCustomConnection(playerSettings.connectionOverride.enabled)
        settings.setSendspinUseTls(playerSettings.connectionOverride.tls)
        settings.setSendspinHost(playerSettings.connectionOverride.host ?: "")
        settings.setSendspinPort(playerSettings.connectionOverride.port ?: 0)
        settings.setSendspinPath(playerSettings.connectionOverride.path ?: "")
    }

    // Connection method preference
    val preferredConnectionMethod = settings.preferredConnectionMethod

    fun setPreferredConnectionMethod(method: String) = settings.setPreferredConnectionMethod(method)

    val clientCertificateAlias = settings.clientCertificateAlias

    fun setClientCertificateAlias(alias: String?) = settings.setClientCertificateAlias(alias)

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

    private fun currentSendspinPlayerSettings() = SendspinPlayerSettings(
        name = settings.sendspinDeviceName.value,
        bufferCapacityMb = settings.sendspinBufferCapacityMb.value,
        codecPreference = settings.sendspinCodecPreference.value,
        connectionOverride = SendspinConnectionSettings(
            enabled = settings.sendspinUseCustomConnection.value,
            tls = settings.sendspinUseTls.value,
            host = settings.sendspinHost.value,
            port = settings.sendspinPort.value,
            path = settings.sendspinPath.value,
        ),
    )

    private fun defaultSendspinPlayerSettings() = SendspinPlayerSettings.defaults.deepCopy()
}

data class SendspinPlayerSettings(
    var name: String?,
    var bufferCapacityMb: Int?,
    var codecPreference: AudioCodec?,
    var connectionOverride: SendspinConnectionSettings,
) {
    companion object {
        val defaults = SendspinPlayerSettings(
            name = SettingsRepository.DEVICE_NAME,
            bufferCapacityMb = SettingsRepository.BUFFER_MB_DEFAULT,
            codecPreference = AudioCodec.OPUS,
            connectionOverride = SendspinConnectionSettings(),
        )
    }
}

private fun SendspinPlayerSettings.deepCopy() = copy(
    connectionOverride = connectionOverride.copy(),
)

data class SendspinConnectionSettings(
    var enabled: Boolean = false,
    var tls: Boolean = false,
    var host: String? = null,
    var port: Int? = null,
    var path: String? = null,
) {
    companion object {
        val defaults = SendspinConnectionSettings()
    }
}
