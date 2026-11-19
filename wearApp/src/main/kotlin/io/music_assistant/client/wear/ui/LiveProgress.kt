package io.music_assistant.client.wear.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import io.music_assistant.client.wear.data.NowPlaying
import kotlinx.coroutines.delay
import kotlin.time.DurationUnit
import kotlin.time.TimeSource

/**
 * The fraction of the current track played, as it stands right now.
 *
 * `queue_time_updated` is still the position model: every report re-anchors the ring. But a
 * report that lands once a second — and only while the watch is hearing the server — leaves
 * the ring sitting still under a playing track. So between reports the position is carried
 * forward from the watch's own monotonic clock, measured from the last report rather than
 * against the server's wall clock, which a watch cannot be trusted to agree with.
 *
 * Null for anything without a known length — a radio stream — so the ring keeps its track
 * and draws no played arc.
 */
@Composable
fun rememberLiveProgress(nowPlaying: NowPlaying?): Float? {
    val reported = nowPlaying?.positionSeconds ?: 0.0
    // A track with no length has nothing to advance towards, and a paused one must not drift
    // away from the position the server last reported.
    val advancing = nowPlaying?.isPlaying == true && nowPlaying.progress != null
    var position by remember { mutableDoubleStateOf(reported) }
    LaunchedEffect(reported, advancing) {
        position = reported
        if (!advancing) return@LaunchedEffect
        val anchor = TimeSource.Monotonic.markNow()
        while (true) {
            delay(TICK_MS)
            position = reported + anchor.elapsedNow().toDouble(DurationUnit.SECONDS)
        }
    }
    return nowPlaying?.progressAt(position)
}

/**
 * Half a second, as on the phone. A tick is a degree or so of the ring on a normal track, so
 * the arc reads as moving without the screen redrawing at frame rate for it.
 */
private const val TICK_MS = 500L
