// Compose layout values (sizes, tick intervals) are visual design tokens.
@file:Suppress("MagicNumber")

package io.music_assistant.client.ui.compose.home.players

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import io.music_assistant.client.utils.currentTimeMillis
import io.music_assistant.client.utils.formatDuration
import kotlinx.coroutines.delay
import musicassistantclient.composeapp.generated.resources.Res
import musicassistantclient.composeapp.generated.resources.cd_sleep_timer
import musicassistantclient.composeapp.generated.resources.cd_sleep_timer_off
import musicassistantclient.composeapp.generated.resources.player_sleep_timer
import org.jetbrains.compose.resources.stringResource
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/**
 * Time left on the sleep timer, ticking once a second, or `null` when no timer runs.
 *
 * [expiresAtSec] is an absolute unix (UTC) timestamp owned by the server, so a badly
 * skewed device clock skews the readout. The server still stops playback on time, and
 * the field is corrected by the next `PlayerUpdatedEvent`, so this is not worth a
 * clock-sync round trip. Keyed on the expiry: a new timer restarts the tick, and the
 * coroutine dies with the composition.
 */
@Composable
fun rememberSleepTimerRemaining(expiresAtSec: Double?): Duration? =
    produceState<Duration?>(initialValue = null, key1 = expiresAtSec) {
        if (expiresAtSec == null) {
            value = null
            return@produceState
        }
        while (true) {
            val leftMs = expiresAtSec * 1000.0 - currentTimeMillis()
            if (leftMs <= 0) {
                value = null
                break
            }
            value = leftMs.milliseconds
            delay(1000)
        }
    }.value

/**
 * Moon + remaining time, in the shared badge pill.
 *
 * A null [remaining] is the idle state: outlined pill and the `--:--` that
 * [formatDuration] already renders for a null duration. It stays tappable either way —
 * tapping with no timer running is how you set one. [endLabel] names the item or chapter
 * end that a timer waits for. It shows while that timer has no countdown (paused).
 */
@Composable
fun SleepTimerBadge(
    remaining: Duration?,
    endLabel: String?,
    tint: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val active = remaining != null || endLabel != null
    BadgePill(
        contentDescription = stringResource(
            if (active) Res.string.cd_sleep_timer else Res.string.cd_sleep_timer_off,
        ),
        tint = tint,
        on = active,
        onClick = onClick,
        modifier = modifier,
    ) {
        // Colors come from the pill via LocalContentColor.
        Icon(
            imageVector = Icons.Default.Bedtime,
            contentDescription = null,
            modifier = Modifier.size(BADGE_ICON_SIZE),
        )
        Text(
            text = remaining?.formatDuration() ?: endLabel ?: stringResource(Res.string.player_sleep_timer),
            style = MaterialTheme.typography.labelSmall,
        )
    }
}
