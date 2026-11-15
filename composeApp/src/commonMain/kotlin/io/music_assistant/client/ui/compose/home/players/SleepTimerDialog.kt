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
import androidx.compose.material.icons.filled.Check
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
import io.music_assistant.client.data.model.client.items.Audiobook
import io.music_assistant.client.data.model.client.items.PlayableItem
import io.music_assistant.client.data.model.client.items.PodcastEpisode
import io.music_assistant.client.data.model.client.items.Track
import io.music_assistant.client.data.model.server.SleepTimerMode
import io.music_assistant.client.utils.formatDuration
import musicassistantclient.composeapp.generated.resources.Res
import musicassistantclient.composeapp.generated.resources.player_sleep_timer
import musicassistantclient.composeapp.generated.resources.sleep_timer_clear
import musicassistantclient.composeapp.generated.resources.sleep_timer_end_of_audiobook
import musicassistantclient.composeapp.generated.resources.sleep_timer_end_of_chapter
import musicassistantclient.composeapp.generated.resources.sleep_timer_end_of_episode
import musicassistantclient.composeapp.generated.resources.sleep_timer_end_of_item
import musicassistantclient.composeapp.generated.resources.sleep_timer_end_of_track
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
 * timestamp, or `null` when no timer runs or an end-of-content timer waits while paused.
 * [endMode] is the item or chapter end that the running timer waits for. [endModes] are the
 * end-of-content options that the server accepts now, named after [currentItem].
 */
@Composable
fun SleepTimerDialog(
    expiresAtSec: Double?,
    endMode: SleepTimerMode?,
    endModes: List<SleepTimerMode>,
    currentItem: PlayableItem?,
    onSelect: (seconds: Int) -> Unit,
    onSelectEnd: (SleepTimerMode) -> Unit,
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
                // alone, so show the countdown instead of ticking an option. A paused
                // end-of-content timer has no countdown, so name the end instead.
                val status = remaining?.let { stringResource(Res.string.sleep_timer_stops_in, it.formatDuration()) }
                    ?: endMode?.let { sleepTimerEndLabel(it, currentItem) }
                status?.let {
                    Text(
                        text = it,
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

                    if (endModes.isNotEmpty()) {
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                        endModes.forEach { mode ->
                            SleepTimerRow(
                                icon = if (mode == endMode) Icons.Default.Check else Icons.Default.Bedtime,
                                label = sleepTimerEndLabel(mode, currentItem),
                                onClick = {
                                    onSelectEnd(mode)
                                    onDismissRequest()
                                },
                            )
                        }
                    }

                    if (remaining != null || endMode != null) {
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

/** Label of an end-of-content [mode], named after the type of [item] like the web frontend. */
@Composable
fun sleepTimerEndLabel(mode: SleepTimerMode, item: PlayableItem?): String = stringResource(
    when {
        mode == SleepTimerMode.END_OF_CHAPTER -> Res.string.sleep_timer_end_of_chapter
        item is Track -> Res.string.sleep_timer_end_of_track
        item is PodcastEpisode -> Res.string.sleep_timer_end_of_episode
        item is Audiobook -> Res.string.sleep_timer_end_of_audiobook
        else -> Res.string.sleep_timer_end_of_item
    },
)

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
