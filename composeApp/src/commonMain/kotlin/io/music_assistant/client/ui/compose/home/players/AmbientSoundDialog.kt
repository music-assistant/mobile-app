package io.music_assistant.client.ui.compose.home.players

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import io.music_assistant.client.data.model.client.QueueOverlay
import io.music_assistant.client.data.model.client.items.SoundEffect
import io.music_assistant.client.ui.compose.common.DataState
import musicassistantclient.composeapp.generated.resources.Res
import musicassistantclient.composeapp.generated.resources.ambient_sound_load_failed
import musicassistantclient.composeapp.generated.resources.ambient_sound_none_available
import musicassistantclient.composeapp.generated.resources.ambient_sound_off
import musicassistantclient.composeapp.generated.resources.ambient_sound_sound
import musicassistantclient.composeapp.generated.resources.ambient_sound_title
import musicassistantclient.composeapp.generated.resources.ambient_sound_volume
import musicassistantclient.composeapp.generated.resources.ambient_sound_volume_hint
import musicassistantclient.composeapp.generated.resources.common_apply
import musicassistantclient.composeapp.generated.resources.common_cancel
import org.jetbrains.compose.resources.stringResource
import kotlin.math.roundToInt

private const val MIN_VOLUME = 5
private const val MAX_VOLUME = 200
private const val VOLUME_STEP = 5

/** Discrete slider stops between the endpoints (exclusive), at [VOLUME_STEP] granularity. */
private const val SLIDER_STEPS = (MAX_VOLUME - MIN_VOLUME) / VOLUME_STEP - 1

/** Snaps a volume onto the [VOLUME_STEP] grid, clamped to the range the dialog offers. */
internal fun snapOverlayVolume(value: Float): Int =
    ((value / VOLUME_STEP).roundToInt() * VOLUME_STEP).coerceIn(MIN_VOLUME, MAX_VOLUME)

/**
 * Picks the ambient sound of a queue and its volume relative to the music. Nothing reaches the
 * server before Apply. "Off" turns the overlay off, and the server keeps the last sound.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AmbientSoundDialog(
    overlay: QueueOverlay,
    playerName: String,
    loadSounds: suspend () -> Result<List<SoundEffect>>,
    onApply: (sourceUri: String?, volume: Int) -> Unit,
    onDismissRequest: () -> Unit,
) {
    var sounds by remember { mutableStateOf<DataState<List<SoundEffect>>>(DataState.Loading()) }
    LaunchedEffect(Unit) {
        sounds = loadSounds().fold(
            onSuccess = { DataState.Data(it) },
            onFailure = { DataState.Error() },
        )
    }
    // Null is "Off". A disabled overlay opens on "Off" even though the server keeps its sound.
    var selectedUri by remember { mutableStateOf(overlay.sourceUri?.takeIf { overlay.enabled }) }
    var volume by remember { mutableIntStateOf(snapOverlayVolume(overlay.volume.toFloat())) }
    val loaded = (sounds as? DataState.Data)?.data

    Dialog(onDismissRequest = onDismissRequest) {
        Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Column {
                    Text(
                        text = stringResource(Res.string.ambient_sound_title),
                        style = MaterialTheme.typography.headlineSmall,
                    )
                    Text(
                        text = playerName,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Text(
                    text = stringResource(Res.string.ambient_sound_sound),
                    style = MaterialTheme.typography.labelLarge,
                )
                when (val state = sounds) {
                    is DataState.Data -> SoundDropdown(
                        sounds = state.data,
                        // A sound the catalog no longer lists still shows under its last name.
                        selectedName = selectedUri?.let { uri ->
                            state.data.firstOrNull { it.uri == uri }?.name ?: overlay.sourceName ?: uri
                        },
                        onSelect = { selectedUri = it },
                    )

                    is DataState.Error -> Text(
                        text = stringResource(Res.string.ambient_sound_load_failed),
                        color = MaterialTheme.colorScheme.error,
                    )

                    else -> Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(modifier = Modifier.size(32.dp))
                    }
                }
                if (loaded?.isEmpty() == true) {
                    Text(
                        text = stringResource(Res.string.ambient_sound_none_available),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Column {
                    Text(
                        text = stringResource(Res.string.ambient_sound_volume, volume),
                        style = MaterialTheme.typography.labelLarge,
                    )
                    Slider(
                        value = volume.toFloat(),
                        onValueChange = { volume = snapOverlayVolume(it) },
                        valueRange = MIN_VOLUME.toFloat()..MAX_VOLUME.toFloat(),
                        steps = SLIDER_STEPS,
                        enabled = selectedUri != null,
                    )
                    Text(
                        text = stringResource(Res.string.ambient_sound_volume_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = onDismissRequest) {
                        Text(stringResource(Res.string.common_cancel))
                    }
                    TextButton(
                        onClick = {
                            onApply(selectedUri, volume)
                            onDismissRequest()
                        },
                        enabled = sounds !is DataState.Loading,
                    ) {
                        Text(stringResource(Res.string.common_apply))
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SoundDropdown(
    sounds: List<SoundEffect>,
    selectedName: String?,
    onSelect: (String?) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val offLabel = stringResource(Res.string.ambient_sound_off)
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
                text = selectedName ?: offLabel,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f),
            )
            ExposedDropdownMenuDefaults.TrailingIcon(expanded)
        }
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text(offLabel) },
                onClick = {
                    onSelect(null)
                    expanded = false
                },
            )
            // A sound without a uri cannot be selected by the server, so it is not offered.
            sounds.forEach { sound ->
                sound.uri?.let { uri ->
                    DropdownMenuItem(
                        text = { Text(sound.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        onClick = {
                            onSelect(uri)
                            expanded = false
                        },
                    )
                }
            }
        }
    }
}
