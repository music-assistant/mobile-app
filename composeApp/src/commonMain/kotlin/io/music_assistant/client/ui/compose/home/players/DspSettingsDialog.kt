package io.music_assistant.client.ui.compose.home.players

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.music_assistant.client.data.model.server.DspConfigPreset
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun DspSettingsDialog(
    playerId: String,
    viewModel: DspSettingsViewModel = koinViewModel(),
    onDismissRequest: () -> Unit,
) {
    LaunchedEffect(playerId) {
        viewModel.load(playerId)
    }

    val state by viewModel.state.collectAsStateWithLifecycle()

    Dialog(onDismissRequest = onDismissRequest) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp)
        ) {
            Box(modifier = Modifier.padding(vertical = 16.dp)) {
                Column {
                    Text(
                        "DSP settings",
                        style = MaterialTheme.typography.headlineSmall,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )

                    when (val s = state) {
                        is DspSettingsViewModel.DspDialogState.Loading -> {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 32.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator()
                            }
                        }

                        is DspSettingsViewModel.DspDialogState.Error -> {
                            Text(
                                "Failed to load DSP configuration",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(16.dp)
                            )
                        }

                        is DspSettingsViewModel.DspDialogState.Content -> {
                            DspContent(
                                state = s,
                                onToggleEnabled = { viewModel.toggleEnabled(playerId) },
                                onApplyPreset = { viewModel.applyPreset(playerId, it) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DspContent(
    state: DspSettingsViewModel.DspDialogState.Content,
    onToggleEnabled: () -> Unit,
    onApplyPreset: (DspConfigPreset) -> Unit,
) {
    Column(modifier = Modifier.padding(top = 16.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                "Enable DSP",
                style = MaterialTheme.typography.bodyLarge,
            )
            Switch(
                checked = state.config.enabled,
                onCheckedChange = { onToggleEnabled() },
            )
        }

        if (state.presets.isNotEmpty()) {
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            Text(
                "Presets",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )

            val iconSize = 20.dp

            LazyColumn(modifier = Modifier.heightIn(max = 300.dp)) {
                items(state.presets, key = { it.presetId ?: it.name }) { preset ->
                    val presetKey = preset.presetId ?: preset.name
                    val isApplied = state.appliedPresetId == presetKey

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .alpha(if (state.config.enabled) 1f else 0.4f)
                            .clickable(enabled = state.config.enabled) {
                                onApplyPreset(preset)
                            }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(modifier = Modifier.size(iconSize)) {
                            if (isApplied) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "Applied",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(iconSize)
                                )
                            }
                        }
                        Text(
                            preset.name,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
        }
    }
}
