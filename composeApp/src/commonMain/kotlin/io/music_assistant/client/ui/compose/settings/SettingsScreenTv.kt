package io.music_assistant.client.ui.compose.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.music_assistant.client.settings.SendspinPlayerSettings
import io.music_assistant.client.settings.SettingsRepository
import io.music_assistant.client.ui.compose.common.OverflowMenuButton
import io.music_assistant.client.ui.compose.common.OverflowMenuOption
import io.music_assistant.client.ui.compose.common.TvFocusFlow
import io.music_assistant.client.ui.compose.common.TvFocusFlow.Links
import io.music_assistant.client.ui.compose.common.TvPreferenceRow
import io.music_assistant.client.ui.compose.common.TvTextEditorDialog
import io.music_assistant.client.ui.compose.common.displayName
import io.music_assistant.client.ui.compose.common.rememberTvFocusFlow
import io.music_assistant.client.ui.compose.common.tvFocus
import io.music_assistant.client.ui.compose.settings.sections.InterruptPlaybackDialog
import io.music_assistant.client.utils.SessionState
import io.music_assistant.client.utils.hasCamera
import io.music_assistant.client.webrtc.model.RemoteId
import kotlinx.coroutines.delay
import musicassistantclient.composeapp.generated.resources.Res
import musicassistantclient.composeapp.generated.resources.cd_connection_history
import musicassistantclient.composeapp.generated.resources.cd_scan_qr_code
import musicassistantclient.composeapp.generated.resources.cd_select_codec
import musicassistantclient.composeapp.generated.resources.common_done
import musicassistantclient.composeapp.generated.resources.settings_buffer_seconds_value
import musicassistantclient.composeapp.generated.resources.settings_buffer_size
import musicassistantclient.composeapp.generated.resources.settings_codec_preference
import musicassistantclient.composeapp.generated.resources.settings_connect
import musicassistantclient.composeapp.generated.resources.settings_connect_saved
import musicassistantclient.composeapp.generated.resources.settings_connect_webrtc
import musicassistantclient.composeapp.generated.resources.settings_connected
import musicassistantclient.composeapp.generated.resources.settings_connecting
import musicassistantclient.composeapp.generated.resources.settings_custom_sendspin
import musicassistantclient.composeapp.generated.resources.settings_hi_res_audio
import musicassistantclient.composeapp.generated.resources.settings_host
import musicassistantclient.composeapp.generated.resources.settings_local_player_disabled
import musicassistantclient.composeapp.generated.resources.settings_local_player_enabled
import musicassistantclient.composeapp.generated.resources.settings_path
import musicassistantclient.composeapp.generated.resources.settings_player_name
import musicassistantclient.composeapp.generated.resources.settings_port
import musicassistantclient.composeapp.generated.resources.settings_remote_id
import musicassistantclient.composeapp.generated.resources.settings_remote_id_invalid
import musicassistantclient.composeapp.generated.resources.settings_sendspin_advanced_title
import musicassistantclient.composeapp.generated.resources.settings_sendspin_reset_defaults
import musicassistantclient.composeapp.generated.resources.settings_sendspin_save_changes
import musicassistantclient.composeapp.generated.resources.settings_server_host
import musicassistantclient.composeapp.generated.resources.settings_use_tls
import musicassistantclient.composeapp.generated.resources.settings_use_tls_wss
import org.jetbrains.compose.resources.stringResource

// A dialog is its own window, so once it closes the platform does not restore D-pad focus to the
// row that opened it. Wait for the window to tear down before re-requesting focus.
private const val DIALOG_CLOSE_FOCUS_DELAY = 150L

// Sized to its own content, not the screen, so the "Advanced" dialog stays comfortably readable
// without stretching edge to edge on a TV.
private const val DIALOG_WIDTH_FRACTION = 0.6f

// Caps the dialog below the full screen height so its rounded corners and the backdrop around it
// stay visible even when every field is expanded; the dialog's own Column scrolls past this.
private const val DIALOG_HEIGHT_FRACTION = 0.85f

// Retries landing initial D-pad focus in the "Advanced" dialog over this window; same cold-start
// race as CONFIG_FOCUS_RETRIES in SettingsScreen.kt.
private const val ADVANCED_DIALOG_FOCUS_RETRIES = 5

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
 * with the custom-connection fields enabled (inline fields overflowed, and TV can't scroll). Buffer
 * size is a row + dialog too, not the phone's [Slider], for the same reason the config form edits
 * host/port that way: a D-pad-focused Material3 Slider gives no visible focus affordance here.
 *
 * Settings only take effect on [onSave] (mirrors the phone's draft/save/reset flow;
 * [SendspinSettingsSection][io.music_assistant.client.ui.compose.settings.sections.SendspinSettingsSection]),
 * so every field stays editable regardless of [enabled] -- unlike the old immediate-apply design,
 * there's nothing to disable while the player runs.
 */
@Composable
internal fun SendspinSectionTv(
    authFlow: TvFocusFlow,
    authLinks: Map<String, TvFocusFlow.Links>,
    enabled: Boolean,
    settings: SendspinPlayerSettings,
    isSavable: Boolean,
    isResettable: Boolean,
    isConfirmationPending: Boolean,
    showAdvanced: Boolean,
    onToggleAdvanced: () -> Unit,
    onEnabledChange: (Boolean) -> Unit,
    onSettingsChange: ((SendspinPlayerSettings) -> SendspinPlayerSettings) -> Unit,
    onSave: () -> Unit,
    onReset: () -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val focusManager = LocalFocusManager.current
    if (isConfirmationPending) {
        InterruptPlaybackDialog(onConfirm = onConfirm, onDismiss = onDismiss)
    }

    var editing by remember { mutableStateOf<String?>(null) }
    var returnTo by remember { mutableStateOf("playerName") }

    if (editing != null) {
        val editor = when (editing) {
            "playerName" -> TvEditorSpec(
                title = stringResource(Res.string.settings_player_name),
                initialValue = settings.name,
                keyboardType = KeyboardType.Text,
                validate = { true },
                onSave = { name -> onSettingsChange { it.copy(name = name) } },
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

    // Best-effort restoration for the "Advanced" dialog itself: it always opens from the tune
    // icon, so closing it (Done or Back) should land focus back there. Guarded by
    // hasOpenedAdvanced so this doesn't steal focus on the section's initial composition, when
    // showAdvanced also starts false but nothing has closed.
    //
    // KNOWN GAP (verified live, not yet root-caused): unlike every other dialog-close restoration
    // on this screen, requestFocus("advanced") reliably returns false here, every attempt, even
    // with clearFocus(force = true) first and retries spanning several seconds -- a plain
    // requestFocus() for a different target (e.g. a row inside SendspinAdvancedDialogTv itself)
    // works fine, so this is specific to this one dialog-closes-to-bare-screen transition, not a
    // general platform limitation. Not a dead end: the system's own geometric fallback still finds
    // a nearby focusable node (confirmed live), so D-pad navigation continues to work from
    // wherever it lands -- just not precisely back on the tune icon. Left as a known limitation
    // rather than a silent "fixed" claim; revisit if a future CMP version changes this.
    var hasOpenedAdvanced by remember { mutableStateOf(false) }
    LaunchedEffect(showAdvanced) {
        if (showAdvanced) {
            hasOpenedAdvanced = true
        } else if (hasOpenedAdvanced) {
            focusManager.clearFocus(force = true)
            var attempt = 0
            while (attempt < ADVANCED_DIALOG_FOCUS_RETRIES) {
                delay(DIALOG_CLOSE_FOCUS_DELAY)
                authFlow.requestFocus("advanced")
                attempt++
            }
        }
    }

    // The "Advanced" entry point lives in this title row (not its own row below) -- the form
    // already fills a 1080p TV screen with just playerName/codec/reset/save (matching the old
    // immediate-apply design's row count), and TV forms don't scroll, so there's no room left for
    // a dedicated row here: one was tried and its own content ended up entirely below the visible
    // area, unreachable by D-pad (verified live -- the row after it couldn't be focused at all).
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(
                if (enabled) Res.string.settings_local_player_enabled else Res.string.settings_local_player_disabled,
            ),
            style = MaterialTheme.typography.titleMedium,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(
                onClick = onToggleAdvanced,
                modifier = Modifier.tvFocus(authFlow, authLinks, "advanced"),
            ) {
                Icon(
                    imageVector = Icons.Default.Tune,
                    contentDescription = stringResource(Res.string.settings_sendspin_advanced_title),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(
                checked = enabled,
                onCheckedChange = onEnabledChange,
                modifier = Modifier.tvFocus(authFlow, authLinks, "playerToggle"),
            )
        }
    }

    if (showAdvanced) {
        SendspinAdvancedDialogTv(
            settings = settings,
            onSettingsChange = onSettingsChange,
            onDismiss = onToggleAdvanced,
        )
    }

    TvPreferenceRow(
        label = stringResource(Res.string.settings_player_name),
        value = settings.name,
        onClick = { editing = "playerName" },
        focusModifier = Modifier.tvFocus(authFlow, authLinks, "playerName"),
    )

    // Codec selection
    OverflowMenuButton(
        options = SettingsRepository.CODECS.map { item ->
            OverflowMenuOption(
                title = item.displayName,
            ) { onSettingsChange { settings -> settings.copy(codecPreference = item) } }
        },
        buttonContent = { onClick ->
            Row(
                modifier = Modifier
                    .tvFocus(authFlow, authLinks, "codec")
                    .fillMaxWidth()
                    .clickable { onClick() }
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
                        text = settings.codecPreference.displayName,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                }
                Icon(
                    modifier = Modifier.size(24.dp),
                    imageVector = Icons.Default.ExpandMore,
                    contentDescription = stringResource(Res.string.cd_select_codec),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        OutlinedButton(
            modifier = Modifier
                .tvFocus(authFlow, authLinks, "reset")
                .weight(1f),
            onClick = onReset,
            enabled = isResettable,
        ) {
            Text(stringResource(Res.string.settings_sendspin_reset_defaults))
        }
        FilledTonalButton(
            modifier = Modifier
                .tvFocus(authFlow, authLinks, "save")
                .weight(1f),
            onClick = onSave,
            enabled = isSavable,
        ) {
            Text(stringResource(Res.string.settings_sendspin_save_changes))
        }
    }
}

/**
 * Hi-res audio, buffer size and the custom-connection fields, in their own window: a dialog is
 * sized to its own content and never has to compete with the rest of the settings form for a
 * 1080p screen's height, unlike [SendspinSectionTv]'s own rows (verified live -- a row added there
 * for this content ended up entirely below the visible area, unreachable by D-pad since TV forms
 * don't scroll). Each text/numeric field still opens its own [TvTextEditorDialog] on top of this
 * one, same as the rest of the TV settings screens.
 */
@Composable
private fun SendspinAdvancedDialogTv(
    settings: SendspinPlayerSettings,
    onSettingsChange: ((SendspinPlayerSettings) -> SendspinPlayerSettings) -> Unit,
    onDismiss: () -> Unit,
) {
    val flow = rememberTvFocusFlow()
    var editing by remember { mutableStateOf<String?>(null) }
    var returnTo by remember { mutableStateOf("hiRes") }

    val customEnabled = settings.connectionOverride.enabled
    val links = buildMap {
        put("hiRes", Links(down = "customToggle"))
        put("customToggle", Links(up = "hiRes", down = if (customEnabled) "customHost" else "buffer"))
        if (customEnabled) {
            put("customHost", Links(up = "customToggle", down = "customPort"))
            put("customPort", Links(up = "customHost", right = "customPath", down = "customTls"))
            put("customPath", Links(up = "customHost", left = "customPort", down = "customTls"))
            put("customTls", Links(up = "customPort", down = "buffer"))
            put("buffer", Links(up = "customTls", down = "done"))
        } else {
            put("buffer", Links(up = "customToggle", down = "done"))
        }
        put("done", Links(up = "buffer"))
    }

    if (editing != null) {
        val editor = when (editing) {
            "customHost" -> TvEditorSpec(
                title = stringResource(Res.string.settings_host),
                initialValue = settings.connectionOverride.host,
                keyboardType = KeyboardType.Text,
                validate = { true },
                onSave = { host ->
                    onSettingsChange { it.copy(connectionOverride = it.connectionOverride.copy(host = host)) }
                },
            )
            "customPort" -> TvEditorSpec(
                title = stringResource(Res.string.settings_port),
                initialValue = settings.connectionOverride.port.toString(),
                keyboardType = KeyboardType.Number,
                validate = { it.toIntOrNull() != null },
                onSave = { text ->
                    text.toIntOrNull()?.let { port ->
                        onSettingsChange { it.copy(connectionOverride = it.connectionOverride.copy(port = port)) }
                    }
                },
            )
            "customPath" -> TvEditorSpec(
                title = stringResource(Res.string.settings_path),
                initialValue = settings.connectionOverride.path,
                keyboardType = KeyboardType.Text,
                validate = { true },
                onSave = { path ->
                    onSettingsChange { it.copy(connectionOverride = it.connectionOverride.copy(path = path)) }
                },
            )
            "buffer" -> TvEditorSpec(
                title = stringResource(Res.string.settings_buffer_size),
                initialValue = settings.bufferSeconds.toString(),
                keyboardType = KeyboardType.Number,
                validate = {
                    val value = it.toIntOrNull()
                    value != null &&
                        value in SettingsRepository.BUFFER_SECONDS_MIN..SettingsRepository.BUFFER_SECONDS_MAX
                },
                onSave = { text ->
                    text.toIntOrNull()?.let { seconds ->
                        onSettingsChange { it.copy(bufferSeconds = seconds) }
                    }
                },
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
                    returnTo = editing ?: "hiRes"
                    editing = null
                },
                onDismiss = {
                    returnTo = editing ?: "hiRes"
                    editing = null
                },
            )
        }
    }

    // Land initial focus when the dialog first opens, and put it back on the row that opened a
    // nested editor once that editor's own window closes (the platform does not restore it for
    // us). This hardware can drop the first requestFocus, so retry like the config form does.
    LaunchedEffect(editing) {
        if (editing == null) {
            var attempt = 0
            while (attempt < ADVANCED_DIALOG_FOCUS_RETRIES) {
                flow.requestFocus(returnTo)
                attempt++
                delay(DIALOG_CLOSE_FOCUS_DELAY)
            }
        }
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.32f)),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth(DIALOG_WIDTH_FRACTION)
                    .fillMaxHeight(DIALOG_HEIGHT_FRACTION)
                    .background(MaterialTheme.colorScheme.surfaceContainer, RoundedCornerShape(12.dp))
                    .padding(16.dp)
                    // Unlike the main settings screen, this is its own window: nothing else on
                    // screen competes with it for D-pad focus, so the scroll container doesn't
                    // have the sibling-focus-theft problem documented on the main form (see
                    // buildAuthLinks). With every field expanded (custom connection + buffer) the
                    // content still doesn't always fit even a generously sized dialog, so it needs
                    // to scroll rather than just being sized to its content.
                    .verticalScroll(rememberScrollState()),
            ) {
                Text(
                    text = stringResource(Res.string.settings_sendspin_advanced_title),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(bottom = 12.dp),
                )

                val hiResAvailable = settings.hiResAvailable()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(Res.string.settings_hi_res_audio),
                        color = if (hiResAvailable) {
                            MaterialTheme.colorScheme.onBackground
                        } else {
                            MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                        },
                    )
                    Switch(
                        checked = settings.effectiveBitDepth() == SettingsRepository.BIT_DEPTH_HI_RES,
                        onCheckedChange = { hiRes ->
                            onSettingsChange {
                                it.copy(
                                    bitDepth = if (hiRes) {
                                        SettingsRepository.BIT_DEPTH_HI_RES
                                    } else {
                                        SettingsRepository.BIT_DEPTH_DEFAULT
                                    },
                                )
                            }
                        },
                        enabled = hiResAvailable,
                        modifier = flow.modifierFor("hiRes", links.getValue("hiRes")),
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(
                        checked = customEnabled,
                        onCheckedChange = { checked ->
                            onSettingsChange {
                                it.copy(connectionOverride = it.connectionOverride.copy(enabled = checked))
                            }
                        },
                        modifier = flow.modifierFor("customToggle", links.getValue("customToggle")),
                    )
                    Text(text = stringResource(Res.string.settings_custom_sendspin))
                }

                if (customEnabled) {
                    TvPreferenceRow(
                        label = stringResource(Res.string.settings_host),
                        value = settings.connectionOverride.host,
                        onClick = { editing = "customHost" },
                        focusModifier = flow.modifierFor("customHost", links.getValue("customHost")),
                    )
                    TvPreferenceRow(
                        label = stringResource(Res.string.settings_port),
                        value = settings.connectionOverride.port.toString(),
                        onClick = { editing = "customPort" },
                        focusModifier = flow.modifierFor("customPort", links.getValue("customPort")),
                    )
                    TvPreferenceRow(
                        label = stringResource(Res.string.settings_path),
                        value = settings.connectionOverride.path,
                        onClick = { editing = "customPath" },
                        focusModifier = flow.modifierFor("customPath", links.getValue("customPath")),
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(
                            checked = settings.connectionOverride.tls,
                            onCheckedChange = { checked ->
                                onSettingsChange {
                                    it.copy(connectionOverride = it.connectionOverride.copy(tls = checked))
                                }
                            },
                            modifier = flow.modifierFor("customTls", links.getValue("customTls")),
                        )
                        Text(text = stringResource(Res.string.settings_use_tls_wss))
                    }
                }

                TvPreferenceRow(
                    label = stringResource(Res.string.settings_buffer_size),
                    value = stringResource(Res.string.settings_buffer_seconds_value, settings.bufferSeconds),
                    onClick = { editing = "buffer" },
                    focusModifier = flow.modifierFor("buffer", links.getValue("buffer")),
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    Button(
                        modifier = flow.modifierFor("done", links.getValue("done")),
                        onClick = onDismiss,
                    ) {
                        Text(stringResource(Res.string.common_done))
                    }
                }
            }
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
