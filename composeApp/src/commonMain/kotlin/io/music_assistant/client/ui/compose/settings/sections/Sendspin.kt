package io.music_assistant.client.ui.compose.settings.sections

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.music_assistant.client.settings.SendspinConnectionSettings
import io.music_assistant.client.settings.SendspinPlayerSettings
import io.music_assistant.client.settings.SettingsRepository
import io.music_assistant.client.ui.compose.common.displayName
import io.music_assistant.client.ui.compose.common.localizedDescription
import io.music_assistant.client.utils.supportsHiResAudio
import io.music_assistant.sendspin.api.AudioCodec
import io.music_assistant.sendspin.api.estimatedBytesPerSecond
import musicassistantclient.composeapp.generated.resources.Res
import musicassistantclient.composeapp.generated.resources.cd_collapse_sendspin_advanced
import musicassistantclient.composeapp.generated.resources.cd_expand_sendspin_advanced
import musicassistantclient.composeapp.generated.resources.common_cancel
import musicassistantclient.composeapp.generated.resources.dialog_interrupting_playback
import musicassistantclient.composeapp.generated.resources.dialog_interrupting_playback_confirm
import musicassistantclient.composeapp.generated.resources.dialog_interrupting_playback_message
import musicassistantclient.composeapp.generated.resources.settings_buffer_seconds_value
import musicassistantclient.composeapp.generated.resources.settings_buffer_size
import musicassistantclient.composeapp.generated.resources.settings_buffer_size_estimate
import musicassistantclient.composeapp.generated.resources.settings_buffer_size_hint
import musicassistantclient.composeapp.generated.resources.settings_codec_preference
import musicassistantclient.composeapp.generated.resources.settings_custom_sendspin
import musicassistantclient.composeapp.generated.resources.settings_hi_res_audio
import musicassistantclient.composeapp.generated.resources.settings_hi_res_audio_hint
import musicassistantclient.composeapp.generated.resources.settings_hi_res_audio_lossless_only
import musicassistantclient.composeapp.generated.resources.settings_hi_res_audio_unsupported
import musicassistantclient.composeapp.generated.resources.settings_host
import musicassistantclient.composeapp.generated.resources.settings_local_player
import musicassistantclient.composeapp.generated.resources.settings_local_player_clear_name
import musicassistantclient.composeapp.generated.resources.settings_path
import musicassistantclient.composeapp.generated.resources.settings_player_name
import musicassistantclient.composeapp.generated.resources.settings_port
import musicassistantclient.composeapp.generated.resources.settings_sendspin_advanced_summary
import musicassistantclient.composeapp.generated.resources.settings_sendspin_advanced_title
import musicassistantclient.composeapp.generated.resources.settings_sendspin_intro
import musicassistantclient.composeapp.generated.resources.settings_sendspin_reset_defaults
import musicassistantclient.composeapp.generated.resources.settings_sendspin_save_changes
import org.jetbrains.compose.resources.stringResource
import kotlin.math.roundToInt

@Composable
fun SendspinSettingsSection(
    enabled: Boolean,
    settings: SendspinPlayerSettings,
    isSavable: Boolean,
    isResettable: Boolean,
    isConfirmationPending: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    onSettingsChange: ((SendspinPlayerSettings) -> SendspinPlayerSettings) -> Unit,
    onSave: () -> Unit,
    onReset: () -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    if (isConfirmationPending) {
        InterruptPlaybackDialog(onConfirm = onConfirm, onDismiss = onDismiss)
    }

    var showAdvancedConfig by rememberSaveable { mutableStateOf(settings.hasNonDefaultAdvancedSettings()) }

    Column {
        Row(
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = stringResource(Res.string.settings_local_player),
                style = MaterialTheme.typography.titleLargeEmphasized,
            )
            Switch(
                checked = enabled,
                onCheckedChange = onEnabledChange,
            )
        }
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
            ),
            shape = RoundedCornerShape(12.dp),
        ) {
            Text(
                text = stringResource(Res.string.settings_sendspin_intro),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp),
            )
            DeviceNameSection(
                name = settings.name,
                onNameChange = { name -> onSettingsChange { it.copy(name = name) } },
            )
            CodecPreferenceSection(
                codec = settings.codecPreference,
                onCodecChange = { codec -> onSettingsChange { it.copy(codecPreference = codec) } },
            )
            HiResAudioSection(
                settings = settings,
                onBitDepthChange = { depth -> onSettingsChange { it.copy(bitDepth = depth) } },
            )
            AdvancedConfigToggleSection(
                expanded = showAdvancedConfig,
                onToggle = { showAdvancedConfig = !showAdvancedConfig },
            )
            AnimatedVisibility(visible = showAdvancedConfig) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                ) {
                    CustomConnectionSection(
                        connection = settings.connectionOverride,
                        onConnectionChange = { connection ->
                            onSettingsChange { it.copy(connectionOverride = connection) }
                        },
                    )
                    BufferSizeSection(
                        settings = settings,
                        onBufferSecondsChange = { seconds -> onSettingsChange { it.copy(bufferSeconds = seconds) } },
                    )
                }
            }
            ActionButtonsSection(
                isResettable = isResettable,
                onReset = onReset,
                isSavable = isSavable,
                onSave = onSave,
            )
        }
    }
}

private fun SendspinPlayerSettings.hasNonDefaultAdvancedSettings(): Boolean =
    bufferSeconds != SendspinPlayerSettings.defaults.bufferSeconds || connectionOverride.enabled

@Composable
private fun InterruptPlaybackDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.dialog_interrupting_playback)) },
        text = { Text(stringResource(Res.string.dialog_interrupting_playback_message)) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(Res.string.dialog_interrupting_playback_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(Res.string.common_cancel))
            }
        },
    )
}

@Composable
private fun DeviceNameSection(
    name: String,
    onNameChange: (String) -> Unit,
) {
    val focusManager = LocalFocusManager.current

    OutlinedTextField(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        value = name,
        onValueChange = onNameChange,
        label = { Text(stringResource(Res.string.settings_player_name)) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
        trailingIcon = {
            if (name != SettingsRepository.DEVICE_NAME) {
                IconButton(onClick = { onNameChange(SettingsRepository.DEVICE_NAME) }) {
                    Icon(
                        imageVector = Icons.Default.Clear,
                        contentDescription = stringResource(Res.string.settings_local_player_clear_name),
                    )
                }
            }
        },
    )
}

@Composable
private fun CodecPreferenceSection(
    codec: AudioCodec,
    onCodecChange: (AudioCodec) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(Res.string.settings_codec_preference),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth(),
        )
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            SettingsRepository.CODECS.forEachIndexed { index, option ->
                SegmentedButton(
                    shape = SegmentedButtonDefaults.itemShape(
                        index = index,
                        count = SettingsRepository.CODECS.size,
                    ),
                    onClick = { onCodecChange(option) },
                    selected = option == codec,
                    label = { Text(option.displayName) },
                )
            }
        }
        Text(
            text = codec.localizedDescription(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun AdvancedConfigToggleSection(
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    ListItem(
        headlineContent = { Text(stringResource(Res.string.settings_sendspin_advanced_title)) },
        supportingContent = { Text(stringResource(Res.string.settings_sendspin_advanced_summary)) },
        trailingContent = {
            Icon(
                modifier = Modifier.size(24.dp),
                imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = stringResource(
                    if (expanded) Res.string.cd_collapse_sendspin_advanced else Res.string.cd_expand_sendspin_advanced,
                ),
            )
        },
        modifier = Modifier.clickable(onClick = onToggle),
        colors = ListItemDefaults.colors(
            containerColor = Color.Transparent,
        ),
    )
}

@Composable
private fun ActionButtonsSection(
    isResettable: Boolean,
    onReset: () -> Unit,
    isSavable: Boolean,
    onSave: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        OutlinedButton(
            modifier = Modifier.weight(1f),
            onClick = onReset,
            contentPadding = PaddingValues(0.dp),
            enabled = isResettable,
        ) {
            Icon(
                imageVector = Icons.Default.Refresh,
                contentDescription = null,
                modifier = Modifier.padding(end = 8.dp),
            )
            Text(stringResource(Res.string.settings_sendspin_reset_defaults))
        }
        FilledTonalButton(
            modifier = Modifier.weight(1f),
            onClick = onSave,
            enabled = isSavable,
            contentPadding = PaddingValues(0.dp),
        ) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = null,
                modifier = Modifier.padding(end = 8.dp),
            )
            Text(stringResource(Res.string.settings_sendspin_save_changes))
        }
    }
}

@Composable
private fun CustomConnectionSection(
    connection: SendspinConnectionSettings,
    onConnectionChange: (SendspinConnectionSettings) -> Unit,
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(Res.string.settings_custom_sendspin),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Checkbox(
                checked = connection.enabled,
                onCheckedChange = { onConnectionChange(connection.copy(enabled = it)) },
            )
        }

        SingleChoiceSegmentedButtonRow {
            SegmentedButton(
                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                onClick = { onConnectionChange(connection.copy(tls = false)) },
                selected = !connection.tls,
                enabled = connection.enabled,
                label = { Text("ws://") },
            )
            SegmentedButton(
                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                onClick = { onConnectionChange(connection.copy(tls = true)) },
                selected = connection.tls,
                enabled = connection.enabled,
                label = { Text("wss://") },
            )
        }
        ConnectionFieldsSection(connection = connection, onConnectionChange = onConnectionChange)
    }
}

@Composable
private fun ConnectionFieldsSection(
    connection: SendspinConnectionSettings,
    onConnectionChange: (SendspinConnectionSettings) -> Unit,
) {
    val focusManager = LocalFocusManager.current

    var isPortFocused by remember { mutableStateOf(false) }
    var isPathFocused by remember { mutableStateOf(false) }

    // Raw text so the field can be cleared while editing; empty means the default port.
    var portText by remember { mutableStateOf(connection.port.toString()) }
    LaunchedEffect(connection.port) {
        if (portText.toPortOrDefault() != connection.port) portText = connection.port.toString()
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        OutlinedTextField(
            modifier = Modifier
                .weight(if (isPortFocused || isPathFocused) 1f else 2f)
                .padding(bottom = 12.dp),
            value = connection.host,
            onValueChange = { onConnectionChange(connection.copy(host = it)) },
            label = { SingleLineText(stringResource(Res.string.settings_host)) },
            singleLine = true,
            placeholder = { SingleLineText("example.com") },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            keyboardActions = KeyboardActions(
                onNext = { focusManager.moveFocus(FocusDirection.Down) },
            ),
            enabled = connection.enabled,
        )
        OutlinedTextField(
            modifier = Modifier
                .onFocusChanged { isPortFocused = it.isFocused }
                .weight(if (isPortFocused) 2f else 1f)
                .padding(bottom = 12.dp),
            value = portText,
            onValueChange = { text ->
                text.takeIf { it.length <= MAX_PORT_LENGTH && it.all(Char::isDigit) }?.let {
                    portText = it
                    onConnectionChange(connection.copy(port = it.toPortOrDefault()))
                }
            },
            label = { SingleLineText(stringResource(Res.string.settings_port)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Number,
                imeAction = ImeAction.Next,
            ),
            placeholder = { SingleLineText(SettingsRepository.DEFAULT_SENDSPIN_PORT.toString()) },
            keyboardActions = KeyboardActions(
                onNext = { focusManager.moveFocus(FocusDirection.Next) },
            ),
            enabled = connection.enabled,
        )
        OutlinedTextField(
            modifier = Modifier
                .onFocusChanged { isPathFocused = it.isFocused }
                .weight(if (isPathFocused) 2f else 1f)
                .padding(bottom = 12.dp),
            value = connection.path,
            onValueChange = { onConnectionChange(connection.copy(path = it)) },
            placeholder = { SingleLineText(SettingsRepository.DEFAULT_SENDSPIN_PATH) },
            label = { SingleLineText(stringResource(Res.string.settings_path)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
            enabled = connection.enabled,
        )
    }
}

@Composable
private fun SingleLineText(text: String) {
    Text(
        text = text,
        maxLines = 1,
        softWrap = false,
        overflow = TextOverflow.Clip,
    )
}

private const val MAX_PORT_LENGTH = 5

/** One decimal, rounded: "1.9". */
private fun Long.toMegabytes(): String {
    val tenths = (this * TENTHS + SettingsRepository.BYTES_PER_MB / 2) / SettingsRepository.BYTES_PER_MB
    return "${tenths / TENTHS}.${tenths % TENTHS}"
}

private const val TENTHS = 10

private fun String.toPortOrDefault(): Int = toIntOrNull() ?: SettingsRepository.DEFAULT_SENDSPIN_PORT

@Composable
private fun HiResAudioSection(
    settings: SendspinPlayerSettings,
    onBitDepthChange: (Int) -> Unit,
) {
    val available = settings.hiResAvailable()
    ListItem(
        headlineContent = { Text(stringResource(Res.string.settings_hi_res_audio)) },
        supportingContent = {
            Text(
                stringResource(
                    when {
                        !supportsHiResAudio -> Res.string.settings_hi_res_audio_unsupported
                        !available -> Res.string.settings_hi_res_audio_lossless_only
                        else -> Res.string.settings_hi_res_audio_hint
                    },
                ),
            )
        },
        trailingContent = {
            Switch(
                checked = settings.effectiveBitDepth() == SettingsRepository.BIT_DEPTH_HI_RES,
                onCheckedChange = { hiRes ->
                    onBitDepthChange(
                        if (hiRes) SettingsRepository.BIT_DEPTH_HI_RES else SettingsRepository.BIT_DEPTH_DEFAULT,
                    )
                },
                enabled = available,
            )
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    )
}

@Composable
private fun BufferSizeSection(
    settings: SendspinPlayerSettings,
    onBufferSecondsChange: (Int) -> Unit,
) {
    val seconds = settings.bufferSeconds
    val estimateBytes = seconds.toLong() * estimatedBytesPerSecond(settings.codecPreference, settings.effectiveBitDepth())
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(Res.string.settings_buffer_size),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = stringResource(Res.string.settings_buffer_seconds_value, seconds),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )
        }
        Slider(
            value = seconds.toFloat(),
            onValueChange = { onBufferSecondsChange(it.roundToInt()) },
            valueRange = SettingsRepository.BUFFER_SECONDS_MIN.toFloat()..SettingsRepository.BUFFER_SECONDS_MAX.toFloat(),
            steps = (SettingsRepository.BUFFER_SECONDS_MAX - SettingsRepository.BUFFER_SECONDS_MIN) /
                SettingsRepository.BUFFER_SECONDS_STEP - 1,
        )
        Text(
            text = stringResource(Res.string.settings_buffer_size_estimate, estimateBytes.toMegabytes()),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = stringResource(Res.string.settings_buffer_size_hint),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
