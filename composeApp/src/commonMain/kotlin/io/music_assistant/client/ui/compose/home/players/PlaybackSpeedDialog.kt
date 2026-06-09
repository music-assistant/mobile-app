@file:Suppress("MagicNumber")

package io.music_assistant.client.ui.compose.home.players

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import musicassistantclient.composeapp.generated.resources.Res
import musicassistantclient.composeapp.generated.resources.common_cancel
import musicassistantclient.composeapp.generated.resources.common_done
import musicassistantclient.composeapp.generated.resources.playback_speed_dialog_title
import org.jetbrains.compose.resources.stringResource
import kotlin.math.abs
import kotlin.math.roundToInt

private const val MIN_SPEED = 0.5
private const val MAX_SPEED = 3.0
private const val SPEED_STEP = 0.05

/** Discrete slider stops between the endpoints (exclusive), at [SPEED_STEP] granularity. */
private val SLIDER_STEPS = ((MAX_SPEED - MIN_SPEED) / SPEED_STEP).roundToInt() - 1

private val PRESETS = listOf(0.5, 0.75, 1.0, 1.25, 1.5, 1.75, 2.0, 2.5, 3.0)

/** Snaps an arbitrary speed onto the [SPEED_STEP] grid, clamped to the valid range. */
private fun snapSpeed(value: Double): Double =
    ((value / SPEED_STEP).roundToInt() * SPEED_STEP).coerceIn(MIN_SPEED, MAX_SPEED)

/**
 * Formats a speed with two decimals, e.g. `1.00`, `1.50`, `2.25`. Works via integer
 * hundredths (not `Double.toString`) to avoid float artifacts like `0.35000000000000003`,
 * and by hand because `"%.2f"` is JVM-only — unavailable in KMP `commonMain`.
 */
internal fun formatSpeed(speed: Double): String {
    val hundredths = (snapSpeed(speed) * 100).roundToInt()
    return "${hundredths / 100}.${(hundredths % 100).toString().padStart(2, '0')}"
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun PlaybackSpeedDialog(
    currentSpeed: Double,
    onConfirm: (Double) -> Unit,
    onDismissRequest: () -> Unit,
) {
    var selected by remember { mutableStateOf(snapSpeed(currentSpeed)) }
    Dialog(onDismissRequest = onDismissRequest) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(Res.string.playback_speed_dialog_title),
                        style = MaterialTheme.typography.headlineSmall,
                    )
                    Text(
                        text = "${formatSpeed(selected)}x",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))

                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PRESETS.forEach { preset ->
                        FilterChip(
                            selected = abs(selected - preset) < SPEED_STEP / 2,
                            onClick = { selected = preset },
                            label = { Text("${formatSpeed(preset)}x") },
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))

                Slider(
                    value = selected.toFloat(),
                    onValueChange = { selected = snapSpeed(it.toDouble()) },
                    valueRange = MIN_SPEED.toFloat()..MAX_SPEED.toFloat(),
                    steps = SLIDER_STEPS,
                )
                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = onDismissRequest) {
                        Text(stringResource(Res.string.common_cancel))
                    }
                    TextButton(
                        onClick = {
                            onConfirm(selected)
                            onDismissRequest()
                        },
                    ) {
                        Text(stringResource(Res.string.common_done))
                    }
                }
            }
        }
    }
}
