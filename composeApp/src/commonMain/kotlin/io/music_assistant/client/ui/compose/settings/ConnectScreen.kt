package io.music_assistant.client.ui.compose.settings

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.music_assistant.client.api.Defaults
import io.music_assistant.client.ui.compose.ScreenSemantics
import io.music_assistant.client.ui.compose.settings.sections.ConnectionSettingsSection
import io.music_assistant.client.ui.compose.settings.sections.LogsSection
import io.music_assistant.client.utils.SessionState
import io.music_assistant.client.utils.isIpPort
import io.music_assistant.client.utils.isValidBasePath
import io.music_assistant.client.utils.isValidHost
import musicassistantclient.composeapp.generated.resources.Res
import musicassistantclient.composeapp.generated.resources.app_name
import musicassistantclient.composeapp.generated.resources.mass
import musicassistantclient.composeapp.generated.resources.settings_exit_app
import musicassistantclient.composeapp.generated.resources.settings_share_crash_logs
import musicassistantclient.composeapp.generated.resources.settings_share_logs
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun ConnectScreen(onExitApp: () -> Unit) {
    val viewModel = koinViewModel<SettingsViewModel>()
    val savedConnectionInfo by viewModel.savedConnectionInfo.collectAsStateWithLifecycle()
    val sessionState by viewModel.sessionState.collectAsStateWithLifecycle()
    val connectionHistory by viewModel.connectionHistory.collectAsStateWithLifecycle()
    val localNetworkOnboardingShown by viewModel.localNetworkOnboardingShown
        .collectAsStateWithLifecycle()
    val localNetworkBlocked by viewModel.localNetworkBlocked.collectAsStateWithLifecycle()
    val probeGranted by viewModel.lastLocalNetworkProbeGranted.collectAsStateWithLifecycle()
    val connectAttemptInFlight by viewModel.connectAttemptInFlight
        .collectAsStateWithLifecycle()
    val hasCrashLog by viewModel.hasCrashLog.collectAsStateWithLifecycle()
    val isPreparingShare by viewModel.isPreparingShare.collectAsStateWithLifecycle()

    var ipAddress by remember { mutableStateOf("") }
    var port by remember { mutableStateOf(Defaults.PORT.toString()) }
    var isTls by remember { mutableStateOf(false) }
    var basePath by remember { mutableStateOf("") }

    LaunchedEffect(savedConnectionInfo) {
        savedConnectionInfo?.let {
            ipAddress = it.host
            port = it.port.toString()
            isTls = it.isTls
            basePath = it.basePath
        }
    }

    // Track if we've already attempted auto-reconnect
    // Sticky for the screen's lifetime: once the iOS permission is determined,
    // retries no longer wait on the prompt, so suppressing repeats is intended.
    var autoReconnectAttempted by remember { mutableStateOf(false) }

    // Auto-reconnect on error ONLY if user hasn't changed the connection info
    // This prevents auto-reconnect to old server when user is trying to connect to new server
    // Does NOT auto-reconnect when user is using WebRTC (different failure mode)
    val preferredMethod by viewModel.preferredConnectionMethod.collectAsStateWithLifecycle()
    LaunchedEffect(sessionState) {
        val errorState = sessionState as? SessionState.Disconnected.Error
        val connInfo = savedConnectionInfo
        if (errorState != null &&
            !autoReconnectAttempted &&
            preferredMethod != "webrtc"
        ) {
            if (connInfo != null) {
                // Only auto-reconnect if text fields match saved connection info
                // (i.e., user hasn't changed anything)
                val userChangedConnectionInfo =
                    ipAddress != connInfo.host ||
                            port != connInfo.port.toString() ||
                            isTls != connInfo.isTls ||
                            basePath != connInfo.basePath

                if (!userChangedConnectionInfo) {
                    // User is trying to reconnect to same server - auto-retry
                    autoReconnectAttempted = true
                    viewModel.attemptConnection(
                        connInfo.host,
                        connInfo.port.toString(),
                        connInfo.isTls,
                        connInfo.basePath,
                    )
                }
                // If user changed connection info, don't auto-retry - let them manually retry
            } else if (errorState.reason?.let(viewModel::isLikelyLocalNetworkBlocked) == true) {
                // Fresh install (no saved connection): retry once — the first
                // attempt is consumed by the platform permission prompt.
                autoReconnectAttempted = true
                viewModel.attemptConnection(ipAddress, port, isTls, basePath)
            }
        }
    }

    Scaffold { contentPadding ->
        Column(
            modifier = Modifier.padding(contentPadding).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            IconButton(modifier = Modifier.align(Alignment.Start), onClick = onExitApp) {
                Icon(
                    Icons.Default.Close,
                    stringResource(Res.string.settings_exit_app),
                )
            }

            Column(
                modifier = Modifier.align(Alignment.CenterHorizontally),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Image(painter = painterResource(Res.drawable.mass), contentDescription = null)
                Text(
                    stringResource(Res.string.app_name),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.testTag(ScreenSemantics.TITLE_TAG),
                )
            }

            when (sessionState) {
                is SessionState.Disconnected -> {
                    LocalNetworkOnboardingCard(
                        resources = viewModel.localNetworkOnboardingResources,
                        visible = viewModel.isLocalNetworkOnboardingVisible(
                            connectionHistory,
                            localNetworkOnboardingShown,
                        ),
                        onGotIt = viewModel::dismissLocalNetworkOnboarding,
                    )
                    ConnectionMethodTabs(
                        viewModel = viewModel,
                        ipAddress = ipAddress,
                        port = port,
                        isTls = isTls,
                        onIpAddressChange = { ipAddress = it },
                        onPortChange = { port = it },
                        onTlsChange = { isTls = it },
                        basePath = basePath,
                        onBasePathChange = { basePath = it },
                        onServerSelected = { connection ->
                            ipAddress = connection.host
                            port = connection.port.toString()
                            isTls = connection.isTls
                            basePath = connection.basePath
                            viewModel.attemptConnection(
                                connection.host,
                                connection.port.toString(),
                                connection.isTls,
                                connection.basePath,
                            )
                        },
                        onDirectConnect = {
                            viewModel.attemptConnection(
                                ipAddress.ifBlank { Defaults.URI },
                                port,
                                isTls,
                                basePath,
                            )
                        },
                        directConnectEnabled = ipAddress.ifBlank { Defaults.URI }.isValidHost() &&
                                port.isIpPort() &&
                                basePath.isValidBasePath() &&
                                !connectAttemptInFlight,
                        sessionState = sessionState,
                        connectionHistory = connectionHistory,
                        localNetworkBlocked = localNetworkBlocked,
                        probeGranted = probeGranted,
                    )
                }

                SessionState.Connecting -> {
                    ConnectingSection(
                        ipAddress = ipAddress.ifBlank { Defaults.URI },
                        port = port,
                        preferredMethod = preferredMethod,
                        onCancel = { viewModel.disconnect() },
                    )
                }

                is SessionState.Reconnecting -> {
                    ConnectingSection(
                        ipAddress = ipAddress.ifBlank { Defaults.URI },
                        port = port,
                        preferredMethod = preferredMethod,
                        onCancel = { viewModel.disconnect() },
                    )
                }

                is SessionState.Connected -> {
                    ConnectionSettingsSection(
                        sessionState = sessionState,
                        connectionInfo = savedConnectionInfo,
                        onDisconnect = { viewModel.disconnect() },
                    )
                }
            }

            val shareLogsTitle = stringResource(Res.string.settings_share_logs)
            val shareCrashLogsTitle = stringResource(Res.string.settings_share_crash_logs)
            LogsSection(
                onShareLogs = { viewModel.shareLogs(shareLogsTitle) },
                hasCrashLog = hasCrashLog,
                isPreparingShare = isPreparingShare,
                onShareCrashLog = { viewModel.shareCrashLog(shareCrashLogsTitle) },
                onDeleteCrashLog = { viewModel.deleteCrashLog() },
            )
        }
    }
}
