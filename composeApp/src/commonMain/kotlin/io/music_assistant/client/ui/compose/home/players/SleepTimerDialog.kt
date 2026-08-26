// Compose layout values (sizes, paddings) are visual design tokens.
@file:Suppress("MagicNumber")

package io.music_assistant.client.ui.compose.home.players

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.TimerOff
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import io.music_assistant.client.utils.formatDuration
import musicassistantclient.composeapp.generated.resources.Res
import musicassistantclient.composeapp.generated.resources.player_sleep_timer
import musicassistantclient.composeapp.generated.resources.sleep_timer_clear
import musicassistantclient.composeapp.generated.resources.sleep_timer_hour
import musicassistantclient.composeapp.generated.resources.sleep_timer_hours
import musicassistantclient.composeapp.generated.resources.sleep_timer_minutes
import musicassistantclient.composeapp.generated.resources.sleep_timer_stops_in
import org.jetbrains.compose.resources.stringResource

private const val MINUTE_SECONDS = 60
private const val HOUR_SECONDS = 60 * 60

/** Fixed option set, in ascending order. */
private val SLEEP_TIMER_OPTIONS = listOf(
    15 * MINUTE_SECONDS,
    30 * MINUTE_SECONDS,
    45 * MINUTE_SECONDS,
    1 * HOUR_SECONDS,
    2 * HOUR_SECONDS,
)

/**
 * Sleep-timer option picker.
 *
 * Stateless by design: the running timer's expiry already rides along on the player
 * payload, so there is nothing to load. [expiresAtSec] is an absolute unix (UTC)
 * timestamp, or `null` when no timer runs.
 */
@Composable
fun SleepTimerDialog(
    expiresAtSec: Double?,
    onSelect: (seconds: Int) -> Unit,
    onClear: () -> Unit,
    onDismissRequest: () -> Unit,
) {
    val remaining = rememberSleepTimerRemaining(expiresAtSec)

    Dialog(onDismissRequest = onDismissRequest) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
        ) {
            Column(modifier = Modifier.padding(vertical = 16.dp)) {
                Text(
                    text = stringResource(Res.string.player_sleep_timer),
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
                // The elapsed part of a running timer is not knowable from the expiry
                // alone, so show the countdown instead of ticking an option.
                remaining?.let {
                    Text(
                        text = stringResource(Res.string.sleep_timer_stops_in, it.formatDuration()),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    )
                }

                Column(modifier = Modifier.padding(top = 8.dp)) {
                    SLEEP_TIMER_OPTIONS.forEach { seconds ->
                        SleepTimerRow(
                            icon = Icons.Default.Bedtime,
                            label = optionLabel(seconds),
                            onClick = {
                                onSelect(seconds)
                                onDismissRequest()
                            },
                        )
                    }

                    if (remaining != null) {
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                        SleepTimerRow(
                            icon = Icons.Default.TimerOff,
                            label = stringResource(Res.string.sleep_timer_clear),
                            tint = MaterialTheme.colorScheme.error,
                            onClick = {
                                onClear()
                                onDismissRequest()
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun optionLabel(seconds: Int): String = when {
    seconds < HOUR_SECONDS -> stringResource(Res.string.sleep_timer_minutes, seconds / MINUTE_SECONDS)
    seconds == HOUR_SECONDS -> stringResource(Res.string.sleep_timer_hour)
    else -> stringResource(Res.string.sleep_timer_hours, seconds / HOUR_SECONDS)
}

@Composable
private fun SleepTimerRow(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    tint: Color = MaterialTheme.colorScheme.onSurface,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = tint,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = tint,
            modifier = Modifier.padding(start = 12.dp),
        )
    }
}
