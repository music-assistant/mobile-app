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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.music_assistant.client.api.ConnectionInfo
import io.music_assistant.client.data.model.server.ServerInfo
import io.music_assistant.client.data.model.server.User
import io.music_assistant.client.ui.compose.auth.AuthenticationPanel
import io.music_assistant.client.ui.compose.common.OverflowMenu
import io.music_assistant.client.ui.compose.common.OverflowMenuOption
import io.music_assistant.client.ui.compose.nav.BackHandler
import io.music_assistant.client.ui.theme.ThemeSetting
import io.music_assistant.client.ui.theme.ThemeViewModel
import io.music_assistant.client.utils.Codecs
import io.music_assistant.client.utils.DataConnectionState
import io.music_assistant.client.utils.SessionState
import io.music_assistant.client.utils.isIpPort
import io.music_assistant.client.utils.isValidHost
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun SettingsScreen(goHome: () -> Unit, exitApp: () -> Unit) {
    val themeViewModel = koinViewModel<ThemeViewModel>()
    val theme = themeViewModel.theme.collectAsStateWithLifecycle(ThemeSetting.FollowSystem)
    val viewModel = koinViewModel<SettingsViewModel>()
    val savedConnectionInfo by viewModel.savedConnectionInfo.collectAsStateWithLifecycle()
    val savedToken by viewModel.savedToken.collectAsStateWithLifecycle()
    val sessionState by viewModel.sessionState.collectAsStateWithLifecycle()
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
                var ipAddress by remember { mutableStateOf("") }
                var port by remember { mutableStateOf("8095") }
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

                // Auto-reconnect on error if we have saved connection info
                LaunchedEffect(sessionState) {
                    val connInfo = savedConnectionInfo
                    if (sessionState is SessionState.Disconnected.Error &&
                        connInfo != null &&
                        !autoReconnectAttempted
                    ) {
                        // Mark that we've attempted reconnection
                        autoReconnectAttempted = true
                        // Attempt reconnection with saved connection info
                        viewModel.attemptConnection(
                            connInfo.host,
                            connInfo.port.toString(),
                            connInfo.isTls
                        )
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
                        ServerConnectionSection(
                            ipAddress = ipAddress,
                            port = port,
                            isTls = isTls,
                            hasToken = savedToken != null,
                            onIpAddressChange = { ipAddress = it },
                            onPortChange = { port = it },
                            onTlsChange = { isTls = it },
                            onConnect = { viewModel.attemptConnection(ipAddress, port, isTls) },
                            enabled = ipAddress.isValidHost() && port.isIpPort()
                        )
                    }

                    SessionState.Connecting -> {
                        ConnectingSection(ipAddress, port)
                    }

                    is SessionState.Reconnecting -> {
                        // Show reconnecting state - similar to connecting but with attempt info
                        ConnectingSection(ipAddress, port)
                    }

                    is SessionState.Connected -> {
                        val connectedState = sessionState as SessionState.Connected

                        // Server Info Section (always shown when connected)
                        ServerInfoSection(
                            connectionInfo = savedConnectionInfo,
                            serverInfo = connectedState.serverInfo,
                            onDisconnect = { viewModel.disconnect() }
                        )

                        LoginSection(connectedState.user)

                        when (dataConnection) {
                            DataConnectionState.Authenticated -> {
                                // State 4: Connected and authenticated
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
private fun ServerConnectionSection(
    ipAddress: String,
    port: String,
    isTls: Boolean,
    hasToken: Boolean,
    onIpAddressChange: (String) -> Unit,
    onPortChange: (String) -> Unit,
    onTlsChange: (Boolean) -> Unit,
    onConnect: () -> Unit,
    enabled: Boolean
) {
    SectionCard {
        SectionTitle("Server Connection")

        TextField(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            value = ipAddress,
            onValueChange = onIpAddressChange,
            label = { Text("Host") },
            singleLine = true,
            colors = TextFieldDefaults.colors(
                focusedTextColor = MaterialTheme.colorScheme.onBackground,
                unfocusedTextColor = MaterialTheme.colorScheme.onBackground,
            )
        )

        TextField(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            value = port,
            onValueChange = onPortChange,
            label = { Text("Port (8095 by default)") },
            singleLine = true,
            colors = TextFieldDefaults.colors(
                focusedTextColor = MaterialTheme.colorScheme.onBackground,
                unfocusedTextColor = MaterialTheme.colorScheme.onBackground,
            )
        )

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
            Text(
                text = "Use TLS",
                color = MaterialTheme.colorScheme.onBackground
            )
        }

        if (hasToken) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                )
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Credentials present",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        }

        Button(
            modifier = Modifier.fillMaxWidth(),
            onClick = onConnect,
            enabled = enabled
        ) {
            Text("Connect")
        }
    }
}

@Composable
private fun ConnectingSection(ipAddress: String, port: String) {
    SectionCard {
        Text(
            modifier = Modifier.fillMaxWidth(),
            text = "Connecting to $ipAddress:$port...",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun ServerInfoSection(
    connectionInfo: ConnectionInfo?,
    serverInfo: ServerInfo?,
    onDisconnect: () -> Unit
) {
    SectionCard {
        SectionTitle("Server")

        connectionInfo?.let { conn ->
            Text(
                text = "Connected to ${conn.host}:${conn.port}",
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

        val sendspinHost by viewModel.sendspinHost.collectAsStateWithLifecycle()
        val sendspinUseTls by viewModel.sendspinUseTls.collectAsStateWithLifecycle()

        TextField(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            value = sendspinHost,
            onValueChange = { viewModel.setSendspinHost(it) },
            label = { Text("Custom Host (leave empty for default)") },
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
                label = { Text("Port (8927 by default)") },
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