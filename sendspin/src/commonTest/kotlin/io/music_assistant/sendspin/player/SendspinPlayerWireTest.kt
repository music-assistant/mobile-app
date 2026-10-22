package io.music_assistant.sendspin.player

import io.music_assistant.sendspin.api.PlayerEvent
import io.music_assistant.sendspin.api.WireVersion
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** Every public-API scenario on the 9.x wire, plus its `client/state` cadence. */
@OptIn(ExperimentalCoroutinesApi::class)
internal class LegacySendspinPlayerTest : SendspinPlayerScenarios(WireVersion.LEGACY_9) {
    @Test
    fun stateIsSynchronizedOnceAfterActivationAndThenEveryTwoSecondsWhilePlaying() = playerTest { h ->
        val server = h.connectServer()
        h.awaitConnected()
        h.nextEvent()
        assertEquals(1, server.clientStates.size)
        assertEquals("synchronized", server.clientStates.single().player().getValue("state").jsonPrimitive.content)

        server.startStream()
        val start = currentTime * 1_000
        repeat(10) { i -> server.sendAudio(4, start + i * 500_000L, h.pcm(500)) }
        runCurrent()
        assertIs<PlayerEvent.PlaybackStarted>(h.nextEvent())
        advanceTimeBy(4_100)
        runCurrent()
        assertTrue(server.clientStates.size >= 3, "states: ${server.clientStates.size}")
    }
}

/** Every public-API scenario on spec 1.0.0-rc1, plus its `client/state` shape and timing. */
@OptIn(ExperimentalCoroutinesApi::class)
internal class SpecSendspinPlayerTest : SendspinPlayerScenarios(WireVersion.SPEC_1_0) {
    @Test
    fun stateIsSentOnceTheClockIsSyncedAndAgainOnlyWhenTheDelayChanges() = playerTest { h ->
        val server = h.connectServer()
        h.awaitConnected()
        h.nextEvent()
        assertTrue(server.clientStates.isEmpty(), "one burst is not convergence")
        advanceTimeBy(1_000) // the second burst, 2 s after the first
        runCurrent()

        val first = server.clientStates.single()
        assertEquals("true", first.getValue("available").jsonPrimitive.content)
        val player = first.player()
        assertFalse(player.containsKey("state"))
        assertEquals(0, player.getValue("output_delay_ms").jsonPrimitive.content.toInt())
        assertEquals(
            SendspinPlayerImpl.REQUIRED_LEAD_TIME_MS,
            player.getValue("required_lead_time_ms").jsonPrimitive.content.toInt(),
        )
        assertEquals(SendspinPlayerImpl.MIN_BUFFER_MS, player.getValue("min_buffer_ms").jsonPrimitive.content.toInt())
        assertEquals(0, player.getValue("supported_commands").jsonArray.size)

        h.config.value = h.config.value!!.copy(userDelayMs = 500)
        runCurrent()
        assertEquals(500, server.clientStates[1].player().getValue("output_delay_ms").jsonPrimitive.content.toInt())

        // A negative lag is local latency compensation; the wire range starts at 0.
        h.config.value = h.config.value!!.copy(userDelayMs = -200)
        runCurrent()
        assertEquals(0, server.clientStates[2].player().getValue("output_delay_ms").jsonPrimitive.content.toInt())

        server.startStream()
        val start = currentTime * 1_000
        repeat(10) { i -> server.sendAudio(4, start + i * 500_000L, h.pcm(500)) }
        runCurrent()
        assertIs<PlayerEvent.PlaybackStarted>(h.nextEvent())
        advanceTimeBy(10_000)
        runCurrent()
        assertEquals(3, server.clientStates.size, "no periodic resend on spec 1.0.0-rc1")
    }

    @Test
    fun noStateBeforeTheClockIsSynced() = playerTest { h ->
        val server = h.connectServer(silent = true)
        h.awaitConnected()
        advanceTimeBy(1_500)
        runCurrent()
        assertTrue(server.clientStates.isEmpty())
    }
}

private fun JsonObject.player(): JsonObject = getValue("player").jsonObject
