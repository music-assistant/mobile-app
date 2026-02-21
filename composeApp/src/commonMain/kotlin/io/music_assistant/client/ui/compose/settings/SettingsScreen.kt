package io.music_assistant.client.ui.compose.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.music_assistant.client.api.ConnectionInfo
import io.music_assistant.client.api.Defaults
import io.music_assistant.client.settings.ConnectionHistoryEntry
import io.music_assistant.client.settings.ConnectionType
import io.music_assistant.client.data.model.server.ServerInfo
import io.music_assistant.client.data.model.server.User
import io.music_assistant.client.player.sendspin.audio.Codecs
import io.music_assistant.client.ui.compose.auth.AuthenticationPanel
import io.music_assistant.client.ui.compose.common.OverflowMenu
import io.music_assistant.client.ui.compose.common.OverflowMenuOption
import io.music_assistant.client.ui.compose.nav.BackHandler
import io.music_assistant.client.ui.theme.ThemeSetting
import io.music_assistant.client.ui.theme.ThemeViewModel
import io.music_assistant.client.utils.DataConnectionState
import io.music_assistant.client.utils.SessionState
import io.music_assistant.client.utils.isIpPort
import io.music_assistant.client.utils.isValidHost
import io.music_assistant.client.webrtc.model.RemoteId
import org.koin.compose.viewmodel.koinViewModel
import org.publicvalue.multiplatform.qrcode.CameraPosition
import org.publicvalue.multiplatform.qrcode.CodeType
import org.publicvalue.multiplatform.qrcode.ScannerWithPermissions

@Composable
fun SettingsScreen(goHome: () -> Unit, exitApp: () -> Unit) {
    val themeViewModel = koinViewModel<ThemeViewModel>()
    val theme = themeViewModel.theme.collectAsStateWithLifecycle(ThemeSetting.FollowSystem)
    val viewModel = koinViewModel<SettingsViewModel>()
    val savedConnectionInfo by viewModel.savedConnectionInfo.collectAsStateWithLifecycle()
    val sessionState by viewModel.sessionState.collectAsStateWithLifecycle()
    val connectionHistory by viewModel.connectionHistory.collectAsStateWithLifecycle()
    val dataConnection = (sessionState as? SessionState.Connected)?.dataConnectionState
    val isAuthenticated = dataConnection == DataConnectionState.Authenticated

    // Only allow back navigation when authenticated
    BackHandler(enabled = true) {
        if (isAuthenticated) {
            goHome()
        } else {
            exitApp()
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) { scaffoldPadding ->
        Column(
            modifier = Modifier
                .background(color = MaterialTheme.colorScheme.background)
                .fillMaxSize()
                .padding(scaffoldPadding)
                .consumeWindowInsets(scaffoldPadding)
                .systemBarsPadding(),
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(all = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Settings",
                    style = MaterialTheme.typography.titleLarge,
                )
                ThemeChooser(currentTheme = theme.value) { changedTheme ->
                    themeViewModel.switchTheme(changedTheme)
                }
            }

            // Content
            val scrollState = rememberScrollState()

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                var ipAddress by remember { mutableStateOf(Defaults.URI) }
                var port by remember { mutableStateOf(Defaults.PORT.toString()) }
                var isTls by remember { mutableStateOf(false) }

                LaunchedEffect(savedConnectionInfo) {
                    savedConnectionInfo?.let {
                        ipAddress = it.host
                        port = it.port.toString()
                        isTls = it.isTls
                    }
                }

                // Track if we've already attempted auto-reconnect
                var autoReconnectAttempted by remember { mutableStateOf(false) }

                // Auto-reconnect on error ONLY if user hasn't changed the connection info
                // This prevents auto-reconnect to old server when user is trying to connect to new server
                // Does NOT auto-reconnect when user is using WebRTC (different failure mode)
                val preferredMethod by viewModel.preferredConnectionMethod.collectAsStateWithLifecycle()
                LaunchedEffect(sessionState) {
                    val connInfo = savedConnectionInfo
                    if (sessionState is SessionState.Disconnected.Error &&
                        connInfo != null &&
                        !autoReconnectAttempted &&
                        preferredMethod != "webrtc"
                    ) {
                        // Only auto-reconnect if text fields match saved connection info
                        // (i.e., user hasn't changed anything)
                        val userChangedConnectionInfo =
                            ipAddress != connInfo.host ||
                                    port != connInfo.port.toString() ||
                                    isTls != connInfo.isTls

                        if (!userChangedConnectionInfo) {
                            // User is trying to reconnect to same server - auto-retry
                            autoReconnectAttempted = true
                            viewModel.attemptConnection(
                                connInfo.host,
                                connInfo.port.toString(),
                                connInfo.isTls
                            )
                        }
                        // If user changed connection info, don't auto-retry - let them manually retry
                    } else if (sessionState is SessionState.Connected) {
                        // Reset flag on successful connection
                        autoReconnectAttempted = false
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    if (isAuthenticated) {
                        Button(onClick = goHome) { Text("GO HOME") }
                    } else {
                        OutlinedButton(onClick = exitApp) { Text("EXIT APP") }
                    }
                }

                when (sessionState) {
                    is SessionState.Disconnected -> {
                        // State 1 & 2: Disconnected (with or without token)
                        // Show connection method tabs
                        ConnectionMethodTabs(
                            viewModel = viewModel,
                            ipAddress = ipAddress,
                            port = port,
                            isTls = isTls,
                            onIpAddressChange = { ipAddress = it },
                            onPortChange = { port = it },
                            onTlsChange = { isTls = it },
                            onDirectConnect = {
                                viewModel.attemptConnection(
                                    ipAddress,
                                    port,
                                    isTls
                                )
                            },
                            directConnectEnabled = ipAddress.isValidHost() && port.isIpPort(),
                            sessionState = sessionState,
                            connectionHistory = connectionHistory,
                        )
                    }

                    SessionState.Connecting -> {
                        ConnectingSection(
                            ipAddress = ipAddress,
                            port = port,
                            preferredMethod = preferredMethod,
                            onCancel = { viewModel.disconnect() }
                        )
                    }

                    is SessionState.Reconnecting -> {
                        ConnectingSection(
                            ipAddress = ipAddress,
                            port = port,
                            preferredMethod = preferredMethod,
                            onCancel = { viewModel.disconnect() }
                        )
                    }

                    is SessionState.Connected -> {
                        val connectedState = sessionState as SessionState.Connected

                        // Server Info Section (always shown when connected)
                        ServerInfoSection(
                            connectionInfo = savedConnectionInfo,
                            serverInfo = connectedState.serverInfo,
                            isWebRTC = connectedState is SessionState.Connected.WebRTC,
                            onDisconnect = { viewModel.disconnect() }
                        )

                        LoginSection(connectedState.user)

                        when (dataConnection) {
                            DataConnectionState.Authenticated -> {
                                // State 4: Connected and authenticated

                                // Local Player Section
                                SendspinSection(
                                    viewModel = viewModel,
                                )
                            }

                            else -> Unit
                        }
                    }
                }

                Spacer(modifier = Modifier.size(16.dp))
            }
        }
    }
}

// Section Composables

@Composable
private fun SectionCard(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            content()
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onBackground,
        modifier = Modifier.padding(bottom = 12.dp)
    )
}

@Composable
private fun ConnectionMethodTabs(
    viewModel: SettingsViewModel,
    ipAddress: String,
    port: String,
    isTls: Boolean,
    onIpAddressChange: (String) -> Unit,
    onPortChange: (String) -> Unit,
    onTlsChange: (Boolean) -> Unit,
    onDirectConnect: () -> Unit,
    directConnectEnabled: Boolean,
    sessionState: SessionState,
    connectionHistory: List<ConnectionHistoryEntry>,
) {
    val preferredMethod by viewModel.preferredConnectionMethod.collectAsStateWithLifecycle()
    val selectedTab = if (preferredMethod == "webrtc") 1 else 0
    val webrtcRemoteId by viewModel.webrtcRemoteId.collectAsStateWithLifecycle()
    var showHistoryDialog by remember { mutableStateOf(false) }

    val directHasToken = port.toIntOrNull()
        ?.let { viewModel.hasCredentialsForDirect(ipAddress, it, isTls) } ?: false
    val webrtcHasToken = webrtcRemoteId.isNotBlank() &&
        viewModel.hasCredentialsForWebRTC(webrtcRemoteId)

    SectionCard {
        SectionTitle("Connection Method")

        // Tabs
        PrimaryTabRow(
            selectedTabIndex = selectedTab,
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface
        ) {
            Tab(
                selected = selectedTab == 0,
                onClick = { viewModel.setPreferredConnectionMethod("direct") },
                text = { Text("Direct") }
            )
            Tab(
                selected = selectedTab == 1,
                onClick = { viewModel.setPreferredConnectionMethod("webrtc") },
                text = { Text("WebRTC") }
            )
        }

        Spacer(modifier = Modifier.size(16.dp))

        // Tab content
        when (selectedTab) {
            0 -> {
                // Direct connection tab
                DirectConnectionContent(
                    ipAddress = ipAddress,
                    port = port,
                    isTls = isTls,
                    hasToken = directHasToken,
                    onIpAddressChange = onIpAddressChange,
                    onPortChange = onPortChange,
                    onTlsChange = onTlsChange,
                    onConnect = onDirectConnect,
                    enabled = directConnectEnabled,
                    onShowHistory = { showHistoryDialog = true },
                )
            }

            1 -> {
                // WebRTC connection tab
                WebRTCConnectionContent(
                    remoteId = webrtcRemoteId,
                    onRemoteIdChange = { viewModel.setWebrtcRemoteId(it.uppercase()) },
                    onConnect = { viewModel.attemptWebRTCConnection(webrtcRemoteId) },
                    sessionState = sessionState,
                    hasToken = webrtcHasToken,
                    onShowHistory = { showHistoryDialog = true },
                )
            }
        }
    }

    if (showHistoryDialog) {
        ConnectionHistoryDialog(
            history = connectionHistory,
            onFill = { entry ->
                when (entry.type) {
                    ConnectionType.DIRECT -> {
                        entry.connectionInfo?.let {
                            onIpAddressChange(it.host)
                            onPortChange(it.port.toString())
                            onTlsChange(it.isTls)
                        }
                        viewModel.setPreferredConnectionMethod("direct")
                    }
                    ConnectionType.WEBRTC -> {
                        entry.remoteId?.let { viewModel.setWebrtcRemoteId(it) }
                        viewModel.setPreferredConnectionMethod("webrtc")
                    }
                }
                showHistoryDialog = false
            },
            onDelete = viewModel::removeFromHistory,
            onDismiss = { showHistoryDialog = false },
        )
    }
}

@Composable
private fun DirectConnectionContent(
    ipAddress: String,
    port: String,
    isTls: Boolean,
    hasToken: Boolean,
    onIpAddressChange: (String) -> Unit,
    onPortChange: (String) -> Unit,
    onTlsChange: (Boolean) -> Unit,
    onConnect: () -> Unit,
    enabled: Boolean,
    onShowHistory: () -> Unit,
) {
    // Host input
    TextField(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp),
        value = ipAddress,
        onValueChange = onIpAddressChange,
        label = { Text("Server host") },
        placeholder = { Text("homeassistant.local") },
        singleLine = true,
        colors = TextFieldDefaults.colors(
            focusedTextColor = MaterialTheme.colorScheme.onBackground,
            unfocusedTextColor = MaterialTheme.colorScheme.onBackground,
        )
    )

    // Port input
    TextField(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp),
        value = port,
        onValueChange = onPortChange,
        label = { Text("Port") },
        placeholder = { Text("8095") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        colors = TextFieldDefaults.colors(
            focusedTextColor = MaterialTheme.colorScheme.onBackground,
            unfocusedTextColor = MaterialTheme.colorScheme.onBackground,
        )
    )

    // TLS toggle
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            checked = isTls,
            onCheckedChange = onTlsChange
        )
        Text("Use TLS (wss://)")
    }

    // Connect button + history icon
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Button(
            modifier = Modifier.weight(1f),
            onClick = onConnect,
            enabled = enabled,
        ) {
            Text(if (hasToken) "Connect with saved credentials" else "Connect")
        }
        IconButton(onClick = onShowHistory) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.List,
                contentDescription = "Connection history",
            )
        }
    }
}

@Composable
private fun WebRTCConnectionContent(
    remoteId: String,
    onRemoteIdChange: (String) -> Unit,
    onConnect: () -> Unit,
    sessionState: SessionState,
    hasToken: Boolean,
    onShowHistory: () -> Unit,
) {
    val isInvalidRemoteId = remoteId.isNotBlank() && !RemoteId.isValid(remoteId)
    val isConnected = sessionState is SessionState.Connected.WebRTC
    val isConnecting = sessionState is SessionState.Connecting
    var showQrDialog by remember { mutableStateOf(false) }

    Text(
        text = "Connect from anywhere without port forwarding",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(bottom = 12.dp)
    )

    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        // Remote ID input field
        TextField(
            modifier = Modifier
                .weight(1f)
                .padding(bottom = 8.dp),
            value = remoteId,
            onValueChange = onRemoteIdChange,
            label = { Text("Remote ID") },
            placeholder = { Text("XXXXXXXX-XXXXX-XXXXX-XXXXXXXX") },
            singleLine = true,
            colors = TextFieldDefaults.colors(
                focusedTextColor = MaterialTheme.colorScheme.onBackground,
                unfocusedTextColor = MaterialTheme.colorScheme.onBackground,
            ),
            supportingText = {
                Text(
                    text = "Enter the Remote ID from your Music Assistant server settings",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            isError = isInvalidRemoteId
        )

        IconButton(onClick = { showQrDialog = true }) {
            Icon(
                imageVector = Icons.Default.QrCodeScanner,
                contentDescription = "Scan QR code",
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }

    // Validation message
    if (isInvalidRemoteId) {
        Text(
            text = "Invalid Remote ID format",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.padding(bottom = 8.dp)
        )
    }

    // Info text about WebRTC
    Text(
        text = "WebRTC uses cloud signaling with end-to-end encryption. Works through most firewalls and NATs.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
        modifier = Modifier.padding(bottom = 12.dp)
    )

    // Connect button + history icon
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Button(
            modifier = Modifier.weight(1f),
            onClick = onConnect,
            enabled = remoteId.isNotBlank() && !isInvalidRemoteId && !isConnected && !isConnecting,
        ) {
            Text(
                when {
                    isConnected -> "Connected"
                    isConnecting -> "Connecting..."
                    hasToken -> "Connect with saved credentials"
                    else -> "Connect via WebRTC"
                }
            )
        }
        IconButton(onClick = onShowHistory) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.List,
                contentDescription = "Connection history",
            )
        }
    }

    if (showQrDialog) {
        QrScanDialog(
            onDismiss = { showQrDialog = false },
            onScanned = { scannedText ->
                onRemoteIdChange(
                    (scannedText.indexOf(webRtcUrlPrefix) + webRtcUrlPrefix.length)
                        .takeIf { it < scannedText.length }
                        ?.let { scannedText.substring(it) }
                        ?: scannedText)
                showQrDialog = false
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QrScanDialog(
    onDismiss: () -> Unit,
    onScanned: (String) -> Unit,
) {
    BasicAlertDialog(
        onDismissRequest = onDismiss,
        content = {

            Column(
                modifier = Modifier.fillMaxWidth().wrapContentHeight()
                    .background(
                        MaterialTheme.colorScheme.surfaceContainer,
                        RoundedCornerShape(corner = CornerSize(12.dp))
                    )
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    style = MaterialTheme.typography.bodyLarge,
                    text = "Scan QR code"
                )
                ScannerWithPermissions(
                    modifier = Modifier.heightIn(120.dp, 360.dp),
                    onScanned = { text ->
                        onScanned(text)
                        true // return true to disable the scanner
                    },
                    types = listOf(CodeType.QR),
                    cameraPosition = CameraPosition.BACK,
                    enableTorch = false
                )
                OutlinedButton(
                    modifier = Modifier.align(Alignment.End),
                    onClick = onDismiss
                ) {
                    Text("Cancel")
                }
            }
        },
    )
}

@Composable
private fun ConnectingSection(
    ipAddress: String,
    port: String,
    preferredMethod: String?,
    onCancel: () -> Unit
) {
    val text = if (preferredMethod == "webrtc") {
        "Connecting to remote server..."
    } else {
        "Connecting to $ipAddress:$port..."
    }
    SectionCard {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center
            )
            OutlinedButton(
                onClick = onCancel,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Cancel")
            }
        }
    }
}

@Composable
private fun ServerInfoSection(
    connectionInfo: ConnectionInfo?,
    serverInfo: ServerInfo?,
    isWebRTC: Boolean = false,
    onDisconnect: () -> Unit
) {
    SectionCard {
        SectionTitle("Server")

        val connectionText = if (isWebRTC) {
            "Connected via WebRTC"
        } else {
            connectionInfo?.let { "Connected to ${it.host}:${it.port}" }
        }
        connectionText?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }

        serverInfo?.let { server ->
            Text(
                text = "Version ${server.serverVersion}, Schema ${server.schemaVersion}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                modifier = Modifier.padding(bottom = 12.dp)
            )
        }

        OutlinedButton(
            modifier = Modifier.fillMaxWidth(),
            onClick = onDisconnect
        ) {
            Text("Disconnect")
        }
    }
}

@Composable
private fun LoginSection(user: User?) {
    SectionCard {
        SectionTitle("Authentication")

        AuthenticationPanel(
            modifier = Modifier.fillMaxWidth(),
            user = user,
        )
    }
}

@Composable
private fun SendspinSection(
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel,
) {
    val sendspinEnabled by viewModel.sendspinEnabled.collectAsStateWithLifecycle()
    val sendspinDeviceName by viewModel.sendspinDeviceName.collectAsStateWithLifecycle()
    val sendspinUseCustomConnection by viewModel.sendspinUseCustomConnection.collectAsStateWithLifecycle()
    val sendspinPort by viewModel.sendspinPort.collectAsStateWithLifecycle()
    val sendspinPath by viewModel.sendspinPath.collectAsStateWithLifecycle()
    val sendspinCodecPreference by viewModel.sendspinCodecPreference.collectAsStateWithLifecycle()

    SectionCard(modifier = modifier) {
        SectionTitle("Local player ${if (sendspinEnabled) "enabled" else "(Sendspin protocol)"}")

        // Text fields on top - disabled when player is running
        TextField(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            value = sendspinDeviceName,
            onValueChange = { viewModel.setSendspinDeviceName(it) },
            label = { Text("Player name") },
            singleLine = true,
            enabled = !sendspinEnabled,
            colors = TextFieldDefaults.colors(
                focusedTextColor = MaterialTheme.colorScheme.onBackground,
                unfocusedTextColor = MaterialTheme.colorScheme.onBackground,
                disabledTextColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
            )
        )

        // Codec selection
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Codec preference",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = sendspinCodecPreference.uiTitle(),
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (sendspinEnabled)
                        MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                    else
                        MaterialTheme.colorScheme.onBackground
                )
            }

            OverflowMenu(
                modifier = Modifier,
                buttonContent = { onClick ->
                    Icon(
                        modifier = Modifier
                            .clickable(enabled = !sendspinEnabled) { onClick() }
                            .size(24.dp),
                        imageVector = Icons.Default.ExpandMore,
                        contentDescription = "Select codec",
                        tint = if (sendspinEnabled)
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        else
                            MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                options = Codecs.list.map { item ->
                    OverflowMenuOption(
                        title = item.uiTitle()
                    ) { viewModel.setSendspinCodecPreference(item) }
                }
            )
        }

        // Custom connection toggle
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = sendspinUseCustomConnection,
                onCheckedChange = { viewModel.setSendspinUseCustomConnection(it) },
                enabled = !sendspinEnabled
            )
            Text(
                text = "Custom Sendspin connection",
                color = if (sendspinEnabled)
                    MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                else
                    MaterialTheme.colorScheme.onBackground
            )
        }

        // Connection fields (only shown when using custom connection)
        if (sendspinUseCustomConnection) {
            val sendspinHost by viewModel.sendspinHost.collectAsStateWithLifecycle()
            val sendspinUseTls by viewModel.sendspinUseTls.collectAsStateWithLifecycle()

            TextField(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                value = sendspinHost,
                onValueChange = { viewModel.setSendspinHost(it) },
                label = { Text("Host") },
                singleLine = true,
                enabled = !sendspinEnabled,
                colors = TextFieldDefaults.colors(
                    focusedTextColor = MaterialTheme.colorScheme.onBackground,
                    unfocusedTextColor = MaterialTheme.colorScheme.onBackground,
                    disabledTextColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                )
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                TextField(
                    modifier = Modifier
                        .weight(1f)
                        .padding(bottom = 12.dp),
                    value = sendspinPort.toString(),
                    onValueChange = {
                        it.toIntOrNull()?.let { port -> viewModel.setSendspinPort(port) }
                    },
                    label = { Text("Port (8095 by default)") },
                    singleLine = true,
                    enabled = !sendspinEnabled,
                    colors = TextFieldDefaults.colors(
                        focusedTextColor = MaterialTheme.colorScheme.onBackground,
                        unfocusedTextColor = MaterialTheme.colorScheme.onBackground,
                        disabledTextColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                    )
                )

                TextField(
                    modifier = Modifier
                        .weight(1f)
                        .padding(bottom = 12.dp),
                    value = sendspinPath,
                    onValueChange = { viewModel.setSendspinPath(it) },
                    label = { Text("Path") },
                    singleLine = true,
                    enabled = !sendspinEnabled,
                    colors = TextFieldDefaults.colors(
                        focusedTextColor = MaterialTheme.colorScheme.onBackground,
                        unfocusedTextColor = MaterialTheme.colorScheme.onBackground,
                        disabledTextColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                    )
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = sendspinUseTls,
                    onCheckedChange = { viewModel.setSendspinUseTls(it) },
                    enabled = !sendspinEnabled
                )
                Text(
                    text = "Use secure connection (WSS/TLS)",
                    color = if (sendspinEnabled)
                        MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                    else
                        MaterialTheme.colorScheme.onBackground
                )
            }
        }

        // Toggle button on the bottom
        if (sendspinEnabled) {
            OutlinedButton(
                modifier = Modifier.fillMaxWidth(),
                onClick = { viewModel.setSendspinEnabled(false) },
            ) {
                Text("Disable local player")
            }
        } else {
            Button(
                modifier = Modifier.fillMaxWidth(),
                onClick = { viewModel.setSendspinEnabled(true) },
            ) {
                Text("Enable local player")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConnectionHistoryDialog(
    history: List<ConnectionHistoryEntry>,
    onFill: (ConnectionHistoryEntry) -> Unit,
    onDelete: (ConnectionHistoryEntry) -> Unit,
    onDismiss: () -> Unit,
) {
    BasicAlertDialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight()
                .background(MaterialTheme.colorScheme.surfaceContainer, RoundedCornerShape(12.dp))
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("Saved Connections", style = MaterialTheme.typography.titleMedium)
            if (history.isEmpty()) {
                Text(
                    "No saved connections yet",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                history.forEach { entry ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        )
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { onFill(entry) }
                                    .padding(start = 12.dp, top = 8.dp, bottom = 8.dp, end = 4.dp)
                            ) {
                                Text(
                                    text = entry.displayAddress,
                                    style = MaterialTheme.typography.bodyLarge,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = when (entry.type) {
                                        ConnectionType.DIRECT -> "Direct"
                                        ConnectionType.WEBRTC -> "WebRTC (Remote Access)"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            IconButton(onClick = { onDelete(entry) }) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Delete",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
            OutlinedButton(modifier = Modifier.align(Alignment.End), onClick = onDismiss) {
                Text("Cancel")
            }
        }
    }
}

const val webRtcUrlPrefix = "https://app.music-assistant.io/?remote_id="
