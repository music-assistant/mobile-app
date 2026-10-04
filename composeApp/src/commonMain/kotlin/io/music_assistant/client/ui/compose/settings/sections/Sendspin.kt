package io.music_assistant.client.ui.compose.settings.sections

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import io.music_assistant.client.settings.SettingsRepository
import io.music_assistant.client.ui.compose.common.localizedTitle
import io.music_assistant.client.ui.compose.settings.SendspinPlayerSettings
import io.music_assistant.sendspin.api.AudioCodec
import musicassistantclient.composeapp.generated.resources.Res
import musicassistantclient.composeapp.generated.resources.button_cancel
import musicassistantclient.composeapp.generated.resources.button_confirm
import musicassistantclient.composeapp.generated.resources.cd_select_codec
import musicassistantclient.composeapp.generated.resources.dialog_interrupting_playback
import musicassistantclient.composeapp.generated.resources.dialog_interrupting_playback_message
import musicassistantclient.composeapp.generated.resources.settings_buffer_size
import musicassistantclient.composeapp.generated.resources.settings_buffer_size_hint
import musicassistantclient.composeapp.generated.resources.settings_buffer_size_value
import musicassistantclient.composeapp.generated.resources.settings_codec_preference
import musicassistantclient.composeapp.generated.resources.settings_custom_sendspin
import musicassistantclient.composeapp.generated.resources.settings_host
import musicassistantclient.composeapp.generated.resources.settings_host_placeholder
import musicassistantclient.composeapp.generated.resources.settings_local_player
import musicassistantclient.composeapp.generated.resources.settings_local_player_clear_name
import musicassistantclient.composeapp.generated.resources.settings_path
import musicassistantclient.composeapp.generated.resources.settings_path_placeholder
import musicassistantclient.composeapp.generated.resources.settings_player_name
import musicassistantclient.composeapp.generated.resources.settings_port_default
import musicassistantclient.composeapp.generated.resources.settings_port_placeholder
import musicassistantclient.composeapp.generated.resources.settings_sendspin_advanced_summary
import musicassistantclient.composeapp.generated.resources.settings_sendspin_advanced_title
import musicassistantclient.composeapp.generated.resources.settings_sendspin_intro
import musicassistantclient.composeapp.generated.resources.settings_sendspin_reset_defaults
import musicassistantclient.composeapp.generated.resources.settings_sendspin_save_changes
import musicassistantclient.composeapp.generated.resources.settings_use_tls_ws
import musicassistantclient.composeapp.generated.resources.settings_use_tls_wss_short
import org.jetbrains.compose.resources.stringResource
import kotlin.math.roundToInt

private typealias UpdateSendspinSettings = (SendspinPlayerSettings.() -> Unit) -> Unit

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SendspinSettingsManager(
    sendspinEnabled: Boolean,
    sendspinPlayerSettings: SendspinPlayerSettings,
    updateSendspinPlayerSettings: UpdateSendspinSettings,
    setSendspinEnabled: (Boolean) -> Unit,
    resetSendspinPlayerSettings: () -> Unit,
    commitSendspinPlayerSettings: () -> Unit,
    isSendspinSettingsSavable: Boolean,
    isSendspinSettingsResettable: Boolean,
) {
    var showInterruptionDialog by remember { mutableStateOf<(() -> Unit)?>(null) }

    if (showInterruptionDialog != null) {
        BasicAlertDialog(
            onDismissRequest = {
                showInterruptionDialog = null
            },
        ) {
            Surface(
                modifier = Modifier.wrapContentWidth().wrapContentHeight(),
                shape = MaterialTheme.shapes.large,
                tonalElevation = AlertDialogDefaults.TonalElevation,
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = stringResource(Res.string.dialog_interrupting_playback),
                        style = MaterialTheme.typography.headlineSmall,
                    )
                    Text(
                        text = stringResource(Res.string.dialog_interrupting_playback_message),
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Row(
                        modifier = Modifier.align(Alignment.End),
                    ) {
                        TextButton(
                            onClick = {
                                showInterruptionDialog = null
                            },

                        ) {
                            Text(stringResource(Res.string.button_cancel))
                        }
                        TextButton(
                            onClick = {
                                showInterruptionDialog?.invoke()
                                showInterruptionDialog = null
                            },
                        ) {
                            Text(stringResource(Res.string.button_confirm))
                        }
                    }
                }
            }
        }
    }

    SendspinSection(
        enabled = sendspinEnabled,
        settings = sendspinPlayerSettings,
        updateSettings = updateSendspinPlayerSettings,
        setEnabled = setSendspinEnabled,
    ) {
        ActionButtonsSection(
            isResettable = isSendspinSettingsResettable,
            onResetToDefaults = resetSendspinPlayerSettings,
            isSavable = isSendspinSettingsSavable,
            onSaveChanges = {
                showInterruptionDialog = {
                    commitSendspinPlayerSettings()
                }
            },
        )
    }
}

private fun SendspinPlayerSettings.hasNonDefaultAdvancedSettings(): Boolean {
    val connectionSettings = connectionOverride
    return bufferCapacityMb != SettingsRepository.BUFFER_MB_DEFAULT ||
        connectionSettings.enabled ||
        connectionSettings.tls ||
        !connectionSettings.host.isNullOrEmpty() ||
        connectionSettings.port != null && connectionSettings.port != 0 ||
        !connectionSettings.path.isNullOrEmpty()
}

@Composable
fun SendspinSection(
    enabled: Boolean,
    settings: SendspinPlayerSettings,
    updateSettings: UpdateSendspinSettings,
    setEnabled: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    trailingSection: @Composable (() -> Unit) = {  },
) {
    var showAdvancedConfig by remember {
        mutableStateOf(settings.hasNonDefaultAdvancedSettings())
    }

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
                onCheckedChange = setEnabled,
            )
        }
        Card(
            modifier = modifier.fillMaxWidth(),
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
            DeviceNameSection(settings = settings, updateSettings = updateSettings)
            CodecPreferenceSection(settings = settings, updateSettings = updateSettings)
            AdvancedConfigToggleSection(
                showAdvancedConfig = showAdvancedConfig,
                toggleShowAdvancedConfig = { showAdvancedConfig = !showAdvancedConfig },
            )
            AnimatedVisibility(visible = showAdvancedConfig) {
                AdvancedConfigSection(settings = settings, updateSettings = updateSettings)
            }
            trailingSection()
        }
    }
}

@Composable
private fun DeviceNameSection(
    settings: SendspinPlayerSettings,
    updateSettings: UpdateSendspinSettings,
) {
    val focusManager = LocalFocusManager.current
    val defaultDeviceName = remember { SettingsRepository.DEVICE_NAME }

    OutlinedTextField(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        value = settings.name ?: defaultDeviceName,
        onValueChange = { name ->
            updateSettings {
                this.name = name
            }
        },
        label = { Text(stringResource(Res.string.settings_player_name)) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
        trailingIcon = {
            if (settings.name != defaultDeviceName) {
                Icon(
                    imageVector = Icons.Default.Clear,
                    contentDescription = stringResource(Res.string.settings_local_player_clear_name),
                    modifier = Modifier.clickable { updateSettings { this.name = defaultDeviceName } },
                )
            }
        },
    )
}

@Composable
private fun CodecPreferenceSection(
    settings: SendspinPlayerSettings,
    updateSettings: UpdateSendspinSettings,
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
            SettingsRepository.CODECS.forEachIndexed { index, codec ->
                SegmentedButton(
                    shape = SegmentedButtonDefaults.itemShape(
                        index = index,
                        count = SettingsRepository.CODECS.size,
                    ),
                    onClick = { updateSettings { this.codecPreference = codec } },
                    selected = codec == settings.codecPreference,
                    label = { Text(codec.localizedTitle().substringBefore(" ")) },
                )
            }
        }
        Text(
            text = (settings.codecPreference ?: AudioCodec.OPUS)
                .localizedTitle()
                .substringAfter(" ")
                .replace("(", "")
                .replace(")", ""),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun AdvancedConfigToggleSection(
    showAdvancedConfig: Boolean = false,
    toggleShowAdvancedConfig: () -> Unit = {  },
) {
    ListItem(
        headlineContent = { Text(stringResource(Res.string.settings_sendspin_advanced_title)) },
        supportingContent = { Text(stringResource(Res.string.settings_sendspin_advanced_summary)) },
        trailingContent = {
            Icon(
                modifier = Modifier.size(24.dp),
                imageVector = if (showAdvancedConfig) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = stringResource(Res.string.cd_select_codec),
            )
        },
        modifier = Modifier.clickable(onClick = toggleShowAdvancedConfig),
        colors = ListItemDefaults.colors(
            containerColor = Color.Transparent,
        ),
    )
}

@Composable
private fun ActionButtonsSection(
    isResettable: Boolean = true,
    onResetToDefaults: () -> Unit,
    isSavable: Boolean = false,
    onSaveChanges: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        OutlinedButton(
            modifier = Modifier.weight(1f),
            onClick = onResetToDefaults,
            contentPadding = PaddingValues(0.dp),
            enabled = isResettable,
        ) {
            Icon(
                imageVector = Icons.Default.Refresh,
                contentDescription = stringResource(Res.string.settings_sendspin_reset_defaults),
                modifier = Modifier.padding(end = 8.dp),
            )
            Text(stringResource(Res.string.settings_sendspin_reset_defaults))
        }
        FilledTonalButton(
            modifier = Modifier.weight(1f),
            onClick = onSaveChanges,
            enabled = isSavable,
            contentPadding = PaddingValues(0.dp),
        ) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = stringResource(Res.string.settings_sendspin_save_changes),
                modifier = Modifier.padding(end = 8.dp),
            )
            Text(stringResource(Res.string.settings_sendspin_save_changes))
        }
    }
}

@Composable
private fun AdvancedConfigSection(
    settings: SendspinPlayerSettings,
    updateSettings: UpdateSendspinSettings,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(20.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
    ) {
        CustomConnectionSection(settings = settings, updateSettings = updateSettings)
        BufferSizeSection(settings = settings, updateSettings = updateSettings)
    }
}

@Composable
private fun CustomConnectionSection(
    settings: SendspinPlayerSettings,
    updateSettings: UpdateSendspinSettings,
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
                checked = settings.connectionOverride.enabled,
                onCheckedChange = {
                    updateSettings {
                       connectionOverride.enabled = it
                    }
                },
            )
        }

        SingleChoiceSegmentedButtonRow {
            SegmentedButton(
                shape = SegmentedButtonDefaults.itemShape(
                    index = 0,
                    count = 2,
                ),
                onClick = {
                    updateSettings {
                        connectionOverride.tls = false
                    }
                },
                selected = !settings.connectionOverride.tls,
                enabled = settings.connectionOverride.enabled,
                label = { Text(stringResource(Res.string.settings_use_tls_ws)) },
            )
            SegmentedButton(
                shape = SegmentedButtonDefaults.itemShape(
                    index = 1,
                    count = 2,
                ),
                onClick = {
                    updateSettings {
                        connectionOverride.tls = true
                    }
                },
                selected = settings.connectionOverride.tls,
                enabled = settings.connectionOverride.enabled,
            label = { Text(stringResource(Res.string.settings_use_tls_wss_short)) },
            )
        }
        ConnectionFieldsSection(settings = settings, updateSettings = updateSettings)
    }
}

@Composable
private fun ConnectionFieldsSection(
    settings: SendspinPlayerSettings,
    updateSettings: UpdateSendspinSettings,
) {
    val focusManager = LocalFocusManager.current

    var isHostFocused by remember { mutableStateOf(false) }
    var isPortFocused by remember { mutableStateOf(false) }
    var isPathFocused by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        OutlinedTextField(
            modifier = Modifier
                .onFocusChanged { isHostFocused = it.isFocused }
                .weight(if (isPortFocused || isPathFocused) 1f else 2f)
                .padding(bottom = 12.dp),
            value = settings.connectionOverride.host ?: "",
            onValueChange = { host -> updateSettings { connectionOverride.host = host } },
            label = {
                Text(
                    text = stringResource(Res.string.settings_host),
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Clip,
                )
            },
            singleLine = true,
            placeholder = {
                Text(
                    text = stringResource(Res.string.settings_host_placeholder),
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Clip,
                )
            },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            keyboardActions = KeyboardActions(
                onNext = { focusManager.moveFocus(FocusDirection.Down) },
            ),
            enabled = settings.connectionOverride.enabled,
        )
        OutlinedTextField(
            modifier = Modifier
                .onFocusChanged { isPortFocused = it.isFocused }
                .weight(if (isPortFocused) 2f else 1f)
                .padding(bottom = 12.dp),
            value = settings.connectionOverride.port?.toString() ?: "",
            onValueChange = { it.toIntOrNull()?.let { port -> updateSettings { connectionOverride.port = port } } },
            label = {
                Text(
                    text = stringResource(Res.string.settings_port_default),
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Clip,
                )
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Number,
                imeAction = ImeAction.Next,
            ),
            placeholder = {
                Text(
                    text = stringResource(Res.string.settings_port_placeholder),
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Clip,
                )
            },
            keyboardActions = KeyboardActions(
                onNext = { focusManager.moveFocus(FocusDirection.Next) },
            ),
            enabled = settings.connectionOverride.enabled,
        )
        OutlinedTextField(
            modifier = Modifier
                .onFocusChanged { isPathFocused = it.isFocused }
                .weight(if (isPathFocused) 2f else 1f)
                .padding(bottom = 12.dp),
            value = settings.connectionOverride.path ?: "",
            onValueChange = { path -> updateSettings { connectionOverride.path = path } },
            placeholder = {
                Text(
                    text = stringResource(Res.string.settings_path_placeholder),
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Clip,
                )
            },
            label = {
                Text(
                    text = stringResource(Res.string.settings_path),
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Clip,
                )
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
            enabled = settings.connectionOverride.enabled,
        )
    }
}

@Composable
private fun BufferSizeSection(
    settings: SendspinPlayerSettings,
    updateSettings: UpdateSendspinSettings,
) {
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
                text = stringResource(
                    Res.string.settings_buffer_size_value,
                    settings.bufferCapacityMb ?: SettingsRepository.BUFFER_MB_DEFAULT,
                ),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )
        }
        Slider(
            value = settings.bufferCapacityMb?.toFloat() ?: SettingsRepository.BUFFER_MB_DEFAULT.toFloat(),
            onValueChange = { updateSettings { bufferCapacityMb = it.roundToInt() } },
            valueRange = SettingsRepository.BUFFER_MB_MIN.toFloat()..SettingsRepository.BUFFER_MB_MAX.toFloat(),
            steps = (SettingsRepository.BUFFER_MB_MAX - SettingsRepository.BUFFER_MB_MIN) /
                SettingsRepository.BUFFER_MB_STEP - 1,
        )
        Text(
            text = stringResource(Res.string.settings_buffer_size_hint),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
