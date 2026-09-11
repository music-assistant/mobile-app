package io.music_assistant.sendspin.clock

import io.music_assistant.sendspin.api.MonotonicClock
import io.music_assistant.sendspin.connection.ServerSilentException
import io.music_assistant.sendspin.wire.SendspinJson
import io.music_assistant.sendspin.wire.ServerTimePayload
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ClockProbeTest {
    private fun clientTransmitted(probeJson: String): Long {
        val root = SendspinJson.parseToJsonElement(probeJson).jsonObject
        assertEquals("client/time", root.getValue("type").jsonPrimitive.content)
        return root.getValue("payload").jsonObject.getValue("client_transmitted").jsonPrimitive.long
    }

    @Test
    fun threeSilentBurstsEndTheAttempt() = runTest {
        val clock = MonotonicClock { currentTime * 1_000 }
        val sync = ClockSync(clock)
        val sent = mutableListOf<String>()
        val probe = ClockProbe(sync, clock) { sent += it }
        val run = async { runCatching { probe.run() } }

        advanceTimeBy(100_000)
        runCurrent()
        assertIs<ServerSilentException>(run.await().exceptionOrNull())
        assertEquals(3 * ClockProbe.PROBES_PER_BURST, sent.size)
        assertEquals(0L, clientTransmitted(sent.first()))
    }

    @Test
    fun repliesKeepTheAttemptAliveAndSlowToTheSteadyCadence() = runTest {
        val clock = MonotonicClock { currentTime * 1_000 }
        val sync = ClockSync(clock)
        val sendTimes = mutableListOf<Long>()
        val probe = ClockProbe(sync, clock) { json ->
            sendTimes += currentTime
            // Replies land in the same virtual instant, so the server must report zero processing time.
            val t1 = clientTransmitted(json)
            sync.onReply(ServerTimePayload(t1, t1 + 5_000, t1 + 5_000))
        }
        val run = async { runCatching { probe.run() } }

        advanceTimeBy(120_000)
        assertTrue(run.isActive, "answered probes never trip liveness")
        assertTrue(sync.isSynced)
        // Burst length: probes at 100 ms plus the 500 ms settle.
        val burstMillis = ClockProbe.PROBES_PER_BURST * ClockProbe.PROBE_INTERVAL_MILLIS + ClockProbe.BURST_SETTLE_MILLIS
        val warmupGap = burstMillis + ClockProbe.WARMUP_BURST_INTERVAL_MILLIS
        val steadyGap = burstMillis + ClockProbe.BURST_INTERVAL_MILLIS
        val burstStarts = sendTimes.filterIndexed { i, _ -> i % ClockProbe.PROBES_PER_BURST == 0 }
        val gaps = burstStarts.zipWithNext { a, b -> b - a }
        assertTrue(gaps.take(6).all { it == warmupGap }, "warm-up gaps $gaps")
        assertTrue(gaps.drop(12).all { it == steadyGap }, "steady gaps $gaps")
        run.cancel()
    }
}
