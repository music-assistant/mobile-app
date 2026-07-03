package io.music_assistant.client.ui.compose.settings

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.music_assistant.client.data.model.server.DspConfigPreset
import io.music_assistant.client.settings.CarDspAction
import musicassistantclient.composeapp.generated.resources.Res
import musicassistantclient.composeapp.generated.resources.common_done
import musicassistantclient.composeapp.generated.resources.dsp_error
import musicassistantclient.composeapp.generated.resources.settings_car_dsp
import musicassistantclient.composeapp.generated.resources.settings_car_dsp_disable
import musicassistantclient.composeapp.generated.resources.settings_car_dsp_explanation
import musicassistantclient.composeapp.generated.resources.settings_car_dsp_nothing
import musicassistantclient.composeapp.generated.resources.settings_car_dsp_on_connect
import musicassistantclient.composeapp.generated.resources.settings_car_dsp_on_disconnect
import musicassistantclient.composeapp.generated.resources.settings_car_dsp_unavailable
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

/**
 * Settings → Car → DSP presets. Two dropdowns (on connect / on disconnect), each choosing
 * "Nothing", "Disable DSP", or a server preset. Selections persist immediately.
 */
@Composable
fun CarDspSettingsDialog(onDismiss: () -> Unit) {
    val viewModel = koinViewModel<CarDspViewModel>()
    LaunchedEffect(Unit) { viewModel.load() }
    val state by viewModel.state.collectAsStateWithLifecycle()

    Dialog(onDismissRequest = onDismiss) {
        Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = stringResource(Res.string.settings_car_dsp),
                    style = MaterialTheme.typography.headlineSmall,
                )
                Text(
                    text = stringResource(Res.string.settings_car_dsp_explanation),
                    style = MaterialTheme.typography.bodyMedium,
                )
                when (val s = state) {
                    is CarDspViewModel.State.Loading ->
                        Box(Modifier.fillMaxWidth(), Alignment.Center) { CircularProgressIndicator() }

                    is CarDspViewModel.State.Error ->
                        Text(
                            text = stringResource(Res.string.dsp_error),
                            color = MaterialTheme.colorScheme.error,
                        )

                    is CarDspViewModel.State.Content -> {
                        if (s.connectReset || s.disconnectReset) {
                            Text(
                                text = stringResource(Res.string.settings_car_dsp_unavailable),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                        DspActionRow(
                            label = stringResource(Res.string.settings_car_dsp_on_connect),
                            selected = s.connect,
                            presets = s.presets,
                            onSelect = viewModel::setConnect,
                        )
                        DspActionRow(
                            label = stringResource(Res.string.settings_car_dsp_on_disconnect),
                            selected = s.disconnect,
                            presets = s.presets,
                            onSelect = viewModel::setDisconnect,
                        )
                    }
                }
                Row(Modifier.fillMaxWidth(), Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text(stringResource(Res.string.common_done)) }
                }
            }
        }
    }
}

@Composable
private fun DspActionRow(
    label: String,
    selected: CarDspAction,
    presets: List<DspConfigPreset>,
    onSelect: (CarDspAction) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(text = label, style = MaterialTheme.typography.labelLarge)
        CarDspDropdown(selected = selected, presets = presets, onSelect = onSelect)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CarDspDropdown(
    selected: CarDspAction,
    presets: List<DspConfigPreset>,
    onSelect: (CarDspAction) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val options = remember(presets) {
        listOf(CarDspAction.Nothing, CarDspAction.Disable) +
            presets.map { CarDspAction.Preset(it.presetId, it.name) }
    }
    val shape = RoundedCornerShape(4.dp)
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth()
                .border(1.dp, MaterialTheme.colorScheme.outline, shape)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = selected.label(),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f),
            )
            ExposedDropdownMenuDefaults.TrailingIcon(expanded)
        }
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.label(), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    onClick = {
                        onSelect(option)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun CarDspAction.label(): String = when (this) {
    CarDspAction.Nothing -> stringResource(Res.string.settings_car_dsp_nothing)
    CarDspAction.Disable -> stringResource(Res.string.settings_car_dsp_disable)
    is CarDspAction.Preset -> name
}
