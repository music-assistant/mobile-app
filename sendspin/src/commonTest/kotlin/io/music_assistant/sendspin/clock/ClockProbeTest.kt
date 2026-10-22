package io.music_assistant.sendspin.clock

import io.music_assistant.sendspin.api.MonotonicClock
import io.music_assistant.sendspin.connection.ServerSilentException
import io.music_assistant.sendspin.wire.SendspinJson
import io.music_assistant.sendspin.wire.ServerTimePayload
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
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
        // An unanswered probe ends its burst, so each silent burst sends one.
        assertEquals(ClockProbe.SILENT_BURSTS_LIMIT, sent.size)
        assertEquals(0L, clientTransmitted(sent.first()))
    }

    @Test
    fun eachProbeWaitsForTheLastReplyAndBurstsStartWithinTheSpecBound() = runTest {
        val clock = MonotonicClock { currentTime * 1_000 }
        val sync = ClockSync(clock)
        val sendTimes = mutableListOf<Long>()
        val probe = ClockProbe(sync, clock) { json ->
            sendTimes += currentTime
            val t1 = clientTransmitted(json)
            backgroundScope.launch {
                delay(REPLY_MILLIS)
                sync.onReply(ServerTimePayload(t1, t1 + 2_500, t1 + 2_500))
            }
        }
        val run = async { runCatching { probe.run() } }

        advanceTimeBy(120_000)
        assertTrue(run.isActive, "answered probes never trip liveness")
        assertTrue(sync.isConverged)
        val bursts = sendTimes.chunked(ClockProbe.PROBES_PER_BURST)
        assertTrue(bursts.dropLast(1).all { it.size == ClockProbe.PROBES_PER_BURST }, "burst sizes")
        assertTrue(
            bursts.dropLast(1).all { burst -> burst.zipWithNext { a, b -> b - a }.all { it == REPLY_MILLIS } },
            "each probe goes out when the previous reply lands",
        )
        val gaps = bursts.map { it.first() }.zipWithNext { a, b -> b - a }
        assertTrue(gaps.take(6).all { it == ClockProbe.WARMUP_BURST_INTERVAL_MILLIS }, "warm-up gaps $gaps")
        assertTrue(gaps.drop(16).all { it == ClockProbe.BURST_INTERVAL_MILLIS }, "steady gaps $gaps")
        assertTrue(gaps.all { it <= 10_000 }, "spec: bursts start at most 10 s apart: $gaps")
        run.cancel()
    }

    @Test
    fun anUnansweredProbeEndsItsBurst() = runTest {
        val clock = MonotonicClock { currentTime * 1_000 }
        val sync = ClockSync(clock)
        val sendTimes = mutableListOf<Long>()
        val probe = ClockProbe(sync, clock) { json ->
            sendTimes += currentTime
            // The server answers only the first three probes of the first burst.
            if (sendTimes.size <= 3) {
                val t1 = clientTransmitted(json)
                backgroundScope.launch {
                    delay(REPLY_MILLIS)
                    sync.onReply(ServerTimePayload(t1, t1 + 2_500, t1 + 2_500))
                }
            }
        }
        val run = async { runCatching { probe.run() } }

        advanceTimeBy(1_600)
        assertEquals(4, sendTimes.size, "three answered probes, then the unanswered one")
        assertTrue(sync.isSynced, "the burst still fed its best reply")
        advanceTimeBy(1_000)
        assertEquals(5, sendTimes.size, "the next burst starts on the warm-up cadence")
        assertEquals(ClockProbe.WARMUP_BURST_INTERVAL_MILLIS, sendTimes[4] - sendTimes[0])
        run.cancel()
    }

    private companion object {
        const val REPLY_MILLIS = 5L
    }
}
