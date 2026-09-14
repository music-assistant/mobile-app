package io.music_assistant.client.ui.compose.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.music_assistant.client.player.sendspin.audio.Codec
import io.music_assistant.client.player.sendspin.audio.Codecs
import io.music_assistant.client.ui.compose.common.OverflowMenuButton
import io.music_assistant.client.ui.compose.common.OverflowMenuOption
import io.music_assistant.client.ui.compose.common.TvFocusFlow
import io.music_assistant.client.ui.compose.common.TvPreferenceRow
import io.music_assistant.client.ui.compose.common.TvTextEditorDialog
import io.music_assistant.client.ui.compose.common.localizedTitle
import io.music_assistant.client.ui.compose.common.tvFocus
import io.music_assistant.client.utils.SessionState
import io.music_assistant.client.utils.hasCamera
import io.music_assistant.client.webrtc.model.RemoteId
import kotlinx.coroutines.delay
import musicassistantclient.composeapp.generated.resources.Res
import musicassistantclient.composeapp.generated.resources.cd_connection_history
import musicassistantclient.composeapp.generated.resources.cd_scan_qr_code
import musicassistantclient.composeapp.generated.resources.cd_select_codec
import musicassistantclient.composeapp.generated.resources.settings_codec_preference
import musicassistantclient.composeapp.generated.resources.settings_connect
import musicassistantclient.composeapp.generated.resources.settings_connect_saved
import musicassistantclient.composeapp.generated.resources.settings_connect_webrtc
import musicassistantclient.composeapp.generated.resources.settings_connected
import musicassistantclient.composeapp.generated.resources.settings_connecting
import musicassistantclient.composeapp.generated.resources.settings_custom_sendspin
import musicassistantclient.composeapp.generated.resources.settings_disable_local_player
import musicassistantclient.composeapp.generated.resources.settings_enable_local_player
import musicassistantclient.composeapp.generated.resources.settings_host
import musicassistantclient.composeapp.generated.resources.settings_path
import musicassistantclient.composeapp.generated.resources.settings_player_name
import musicassistantclient.composeapp.generated.resources.settings_port
import musicassistantclient.composeapp.generated.resources.settings_remote_id
import musicassistantclient.composeapp.generated.resources.settings_remote_id_invalid
import musicassistantclient.composeapp.generated.resources.settings_sendspin_require_encryption
import musicassistantclient.composeapp.generated.resources.settings_server_host
import musicassistantclient.composeapp.generated.resources.settings_use_tls
import musicassistantclient.composeapp.generated.resources.settings_use_tls_wss
import org.jetbrains.compose.resources.stringResource

// A dialog is its own window, so once it closes the platform does not restore D-pad focus to the
// row that opened it. Wait for the window to tear down before re-requesting focus.
private const val DIALOG_CLOSE_FOCUS_DELAY = 150L

/**
 * TV variant of [DirectConnectionContent]: the host/port fields become "label + value" rows
 * ([TvPreferenceRow]) that open the full-window editor dialog ([TvTextEditorDialog]) when
 * selected, so the form needs no inline text fields and fits any TV screen without scrolling.
 */
@Composable
internal fun DirectConnectionContentTv(
    configFlow: TvFocusFlow,
    configLinks: Map<String, TvFocusFlow.Links>,
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
    var editing by remember { mutableStateOf<String?>(null) }
    var returnTo by remember { mutableStateOf("host") }
    // Set when a dialog opens; the LaunchedEffect below then knows to re-land focus after it
    // closes, without stealing focus from the tabs on the form's first composition.
    var dialogWasOpen by remember { mutableStateOf(false) }

    if (editing != null) {
        val editor = when (editing) {
            "host" -> TvEditorSpec(
                title = stringResource(Res.string.settings_server_host),
                initialValue = ipAddress,
                keyboardType = KeyboardType.Text,
                validate = { true },
                onSave = onIpAddressChange,
            )

            "port" -> TvEditorSpec(
                title = stringResource(Res.string.settings_port),
                initialValue = port,
                keyboardType = KeyboardType.Number,
                validate = { it.isEmpty() || it.toIntOrNull() != null },
                onSave = onPortChange,
            )

            else -> null
        }
        if (editor != null) {
            dialogWasOpen = true
            TvTextEditorDialog(
                title = editor.title,
                initialValue = editor.initialValue,
                keyboardType = editor.keyboardType,
                validate = editor.validate,
                onConfirm = { value ->
                    editor.onSave(value)
                    returnTo = editing ?: "host"
                    editing = null
                },
                onDismiss = {
                    returnTo = editing ?: "host"
                    editing = null
                },
            )
        }
    }

    // The dialog is its own window, so once it closes put D-pad focus back on the row that opened
    // it (the platform does not restore it for us).
    LaunchedEffect(editing) {
        if (editing == null && dialogWasOpen) {
            dialogWasOpen = false
            delay(DIALOG_CLOSE_FOCUS_DELAY)
            configFlow.requestFocus(returnTo)
        }
    }

    TvPreferenceRow(
        label = stringResource(Res.string.settings_server_host),
        value = ipAddress.ifBlank { "homeassistant.local" },
        onClick = { editing = "host" },
        focusModifier = configFlow.modifierFor("host", configLinks.getValue("host"))
            .testTag("Config-Host"),
    )
    TvPreferenceRow(
        label = stringResource(Res.string.settings_port),
        value = port.ifBlank { "8095" },
        onClick = { editing = "port" },
        focusModifier = configFlow.modifierFor("port", configLinks.getValue("port"))
            .testTag("Config-Port"),
    )

    // TLS toggle
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(
            checked = isTls,
            onCheckedChange = onTlsChange,
            modifier = configFlow.modifierFor("tls", configLinks.getValue("tls"))
                .testTag("Config-Tls"),
        )
        Text(stringResource(Res.string.settings_use_tls))
    }

    // Connect button + history icon
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Button(
            modifier = configFlow.modifierFor("connect", configLinks.getValue("connect"))
                .testTag("Config-Connect")
                .weight(1f),
            onClick = onConnect,
            enabled = enabled,
        ) {
            Text(
                if (hasToken) {
                    stringResource(Res.string.settings_connect_saved)
                } else {
                    stringResource(Res.string.settings_connect)
                },
            )
        }
        IconButton(
            onClick = onShowHistory,
            modifier = configFlow.modifierFor("history", configLinks.getValue("history"))
                .testTag("Config-History"),
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.List,
                contentDescription = stringResource(Res.string.cd_connection_history),
            )
        }
    }
}

/**
 * TV variant of [WebRTCConnectionContent]: the remote-ID field becomes a [TvPreferenceRow] that
 * opens the full-window editor dialog ([TvTextEditorDialog]) when selected, so the tab needs no
 * inline text field and fits any TV screen without scrolling.
 */
@Composable
internal fun WebRTCConnectionContentTv(
    configFlow: TvFocusFlow,
    configLinks: Map<String, TvFocusFlow.Links>,
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

    var editing by remember { mutableStateOf<String?>(null) }
    var dialogWasOpen by remember { mutableStateOf(false) }

    if (editing != null) {
        dialogWasOpen = true
        TvTextEditorDialog(
            title = stringResource(Res.string.settings_remote_id),
            initialValue = remoteId,
            keyboardType = KeyboardType.Text,
            validate = { true },
            onConfirm = { value ->
                onRemoteIdChange(value)
                editing = null
            },
            onDismiss = { editing = null },
        )
    }

    LaunchedEffect(editing) {
        if (editing == null && dialogWasOpen) {
            dialogWasOpen = false
            delay(DIALOG_CLOSE_FOCUS_DELAY)
            configFlow.requestFocus("remoteId")
        }
    }

    TvPreferenceRow(
        label = stringResource(Res.string.settings_remote_id),
        value = remoteId.ifBlank { "XXXXXXXX-XXXXX-XXXXX-XXXXXXXX" },
        onClick = { editing = "remoteId" },
        focusModifier = configFlow.modifierFor("remoteId", configLinks.getValue("remoteId"))
            .testTag("Config-RemoteId"),
    )
    if (isInvalidRemoteId) {
        Text(
            text = stringResource(Res.string.settings_remote_id_invalid),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.padding(bottom = 8.dp),
        )
    }

    // Connect button + history icon
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Button(
            modifier = configFlow.modifierFor("connect", configLinks.getValue("connect"))
                .testTag("Config-Connect")
                .weight(1f),
            onClick = onConnect,
            enabled = remoteId.isNotBlank() && !isInvalidRemoteId && !isConnected && !isConnecting,
        ) {
            Text(
                when {
                    isConnected -> stringResource(Res.string.settings_connected)
                    isConnecting -> stringResource(Res.string.settings_connecting)
                    hasToken -> stringResource(Res.string.settings_connect_saved)
                    else -> stringResource(Res.string.settings_connect_webrtc)
                },
            )
        }
        if (hasCamera()) {
            IconButton(
                onClick = { showQrDialog = true },
                modifier = configFlow.modifierFor("scanQr", configLinks["scanQr"] ?: TvFocusFlow.Links()),
            ) {
                Icon(
                    imageVector = Icons.Default.QrCodeScanner,
                    contentDescription = stringResource(Res.string.cd_scan_qr_code),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
        IconButton(
            onClick = onShowHistory,
            modifier = configFlow.modifierFor("history", configLinks.getValue("history"))
                .testTag("Config-History"),
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.List,
                contentDescription = stringResource(Res.string.cd_connection_history),
            )
        }
    }

    if (showQrDialog) {
        QrScanDialog(
            onDismiss = { showQrDialog = false },
            onScanned = { scannedText ->
                onRemoteIdChange(
                    (scannedText.indexOf(WEB_RTC_URL_PREFIX) + WEB_RTC_URL_PREFIX.length)
                        .takeIf { it < scannedText.length }
                        ?.let { scannedText.substring(it) }
                        ?: scannedText,
                )
                showQrDialog = false
            },
        )
    }
}

/**
 * Android TV version of the local-player card: rows instead of inline text fields. Each text value
 * opens [TvTextEditorDialog] on select, so the card stays short enough to fit a 1080p screen even
 * with the custom-connection fields enabled (inline fields overflowed, and TV can't scroll).
 */
@Composable
internal fun SendspinSectionTv(
    viewModel: SettingsViewModel,
    authFlow: TvFocusFlow,
    authLinks: Map<String, TvFocusFlow.Links>,
    sendspinEnabled: Boolean,
    sendspinDeviceName: String,
    sendspinUseCustomConnection: Boolean,
    sendspinPort: Int,
    sendspinPath: String,
    sendspinCodecPreference: Codec,
) {
    val sendspinHost by viewModel.sendspinHost.collectAsStateWithLifecycle()
    val sendspinUseTls by viewModel.sendspinUseTls.collectAsStateWithLifecycle()
    val sendspinRequireEncryption by viewModel.sendspinRequireEncryption.collectAsStateWithLifecycle()

    var editing by remember { mutableStateOf<String?>(null) }
    var returnTo by remember { mutableStateOf("playerName") }

    if (editing != null) {
        val editor = when (editing) {
            "playerName" -> TvEditorSpec(
                title = stringResource(Res.string.settings_player_name),
                initialValue = sendspinDeviceName,
                keyboardType = KeyboardType.Text,
                validate = { true },
                onSave = { viewModel.setSendspinDeviceName(it) },
            )
            "customHost" -> TvEditorSpec(
                title = stringResource(Res.string.settings_host),
                initialValue = sendspinHost,
                keyboardType = KeyboardType.Text,
                validate = { true },
                onSave = { viewModel.setSendspinHost(it) },
            )
            "customPort" -> TvEditorSpec(
                title = stringResource(Res.string.settings_port),
                initialValue = sendspinPort.toString(),
                keyboardType = KeyboardType.Number,
                validate = { it.toIntOrNull() != null },
                onSave = { it.toIntOrNull()?.let { port -> viewModel.setSendspinPort(port) } },
            )
            "customPath" -> TvEditorSpec(
                title = stringResource(Res.string.settings_path),
                initialValue = sendspinPath,
                keyboardType = KeyboardType.Text,
                validate = { true },
                onSave = { viewModel.setSendspinPath(it) },
            )
            else -> null
        }
        if (editor != null) {
            TvTextEditorDialog(
                title = editor.title,
                initialValue = editor.initialValue,
                keyboardType = editor.keyboardType,
                validate = editor.validate,
                onConfirm = { value ->
                    editor.onSave(value)
                    returnTo = editing ?: "playerName"
                    editing = null
                },
                onDismiss = {
                    returnTo = editing ?: "playerName"
                    editing = null
                },
            )
        }
    }

    // The dialog is its own window, so once it closes put D-pad focus back on the row that
    // opened it (the platform does not restore it for us).
    LaunchedEffect(editing) {
        if (editing == null) {
            delay(DIALOG_CLOSE_FOCUS_DELAY)
            authFlow.requestFocus(returnTo)
        }
    }

    TvPreferenceRow(
        label = stringResource(Res.string.settings_player_name),
        value = sendspinDeviceName,
        onClick = if (sendspinEnabled) null else ({ editing = "playerName" }),
        focusModifier = Modifier.tvFocus(authFlow, authLinks, "playerName"),
    )

    // Codec selection
    OverflowMenuButton(
        options = Codecs.list.map { item ->
            OverflowMenuOption(
                title = item.localizedTitle(),
            ) { viewModel.setSendspinCodecPreference(item) }
        },
        buttonContent = { onClick ->
            Row(
                modifier = Modifier
                    .tvFocus(authFlow, authLinks, "codec")
                    .fillMaxWidth()
                    .clickable(enabled = !sendspinEnabled) { onClick() }
                    .padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(Res.string.settings_codec_preference),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = sendspinCodecPreference.localizedTitle(),
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (sendspinEnabled) {
                            MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                        } else {
                            MaterialTheme.colorScheme.onBackground
                        },
                    )
                }
                Icon(
                    modifier = Modifier.size(24.dp),
                    imageVector = Icons.Default.ExpandMore,
                    contentDescription = stringResource(Res.string.cd_select_codec),
                    tint = if (sendspinEnabled) {
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
        },
    )

    // Custom connection toggle
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(
            checked = sendspinUseCustomConnection,
            onCheckedChange = { viewModel.setSendspinUseCustomConnection(it) },
            enabled = !sendspinEnabled,
            modifier = Modifier.tvFocus(authFlow, authLinks, "customToggle"),
        )
        Text(
            text = stringResource(Res.string.settings_custom_sendspin),
            color = if (sendspinEnabled) {
                MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
            } else {
                MaterialTheme.colorScheme.onBackground
            },
        )
    }

    // Require-encryption toggle: refuse the legacy cleartext protocol
    // when the server is too old for encrypted Sendspin.
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(
            checked = sendspinRequireEncryption,
            onCheckedChange = { viewModel.setSendspinRequireEncryption(it) },
            enabled = !sendspinEnabled,
            modifier = Modifier.tvFocus(authFlow, authLinks, "requireEncryption"),
        )
        Text(
            text = stringResource(Res.string.settings_sendspin_require_encryption),
            color = if (sendspinEnabled) {
                MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
            } else {
                MaterialTheme.colorScheme.onBackground
            },
        )
    }

    // Connection rows (only shown when using custom connection)
    if (sendspinUseCustomConnection) {
        TvPreferenceRow(
            label = stringResource(Res.string.settings_host),
            value = sendspinHost,
            onClick = if (sendspinEnabled) null else ({ editing = "customHost" }),
            focusModifier = Modifier.tvFocus(authFlow, authLinks, "customHost"),
        )
        TvPreferenceRow(
            label = stringResource(Res.string.settings_port),
            value = sendspinPort.toString(),
            onClick = if (sendspinEnabled) null else ({ editing = "customPort" }),
            focusModifier = Modifier.tvFocus(authFlow, authLinks, "customPort"),
        )
        TvPreferenceRow(
            label = stringResource(Res.string.settings_path),
            value = sendspinPath,
            onClick = if (sendspinEnabled) null else ({ editing = "customPath" }),
            focusModifier = Modifier.tvFocus(authFlow, authLinks, "customPath"),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(
                checked = sendspinUseTls,
                onCheckedChange = { viewModel.setSendspinUseTls(it) },
                enabled = !sendspinEnabled,
                modifier = Modifier.tvFocus(authFlow, authLinks, "customTls"),
            )
            Text(
                text = stringResource(Res.string.settings_use_tls_wss),
                color = if (sendspinEnabled) {
                    MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                } else {
                    MaterialTheme.colorScheme.onBackground
                },
            )
        }
    }

    // Toggle button on the bottom
    if (sendspinEnabled) {
        OutlinedButton(
            modifier = Modifier
                .tvFocus(authFlow, authLinks, "playerToggle")
                .fillMaxWidth(),
            onClick = { viewModel.setSendspinEnabled(false) },
        ) {
            Text(stringResource(Res.string.settings_disable_local_player))
        }
    } else {
        Button(
            modifier = Modifier
                .tvFocus(authFlow, authLinks, "playerToggle")
                .fillMaxWidth(),
            onClick = { viewModel.setSendspinEnabled(true) },
        ) {
            Text(stringResource(Res.string.settings_enable_local_player))
        }
    }
}

private data class TvEditorSpec(
    val title: String,
    val initialValue: String,
    val keyboardType: KeyboardType,
    val validate: (String) -> Boolean,
    val onSave: (String) -> Unit,
)
