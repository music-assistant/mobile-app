package io.music_assistant.client.player.sendspin

import app.cash.turbine.test
import io.music_assistant.client.player.sendspin.protocol.MessageDispatcher
import io.music_assistant.client.player.sendspin.protocol.StreamLifecycleEvent
import io.music_assistant.client.player.sendspin.session.SendspinOutboundSender
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/**
 * Locks in the ordering guarantee of the single merged [StreamLifecycleEvent] flow: start/end/clear
 * are re-emitted in the exact order the server sent them. The previous three-separate-flows design
 * could not guarantee this — a stream/end and the following stream/start could be observed in
 * either order, which is what allowed stale streams to slip past a rapid-skip coalescer.
 */
class MessageDispatcherOrderingTest {
    private object DiscardingSender : SendspinOutboundSender {
        override suspend fun sendJson(json: String) = Unit
    }

    private fun startJson(header: String) =
        """{"type":"stream/start","payload":{"player":{"codec":"flac",""" +
            """"sample_rate":44100,"channels":2,"bit_depth":16,"codec_header":"$header"}}}"""

    private val endJson = """{"type":"stream/end"}"""
    private val clearJson = """{"type":"stream/clear"}"""

    private var dispatcher: MessageDispatcher? = null

    @AfterTest
    fun tearDown() {
        dispatcher?.stop()
    }

    @Test
    fun preservesStartEndOrderAcrossRapidSkips() = runTest {
        // A rapid-skip burst: three tracks, each end+start, landing on track "c".
        val texts = listOf(
            startJson("a"),
            endJson,
            startJson("b"),
            endJson,
            startJson("c"),
            clearJson,
        )
        val d = MessageDispatcher(texts.asFlow(), DiscardingSender, ClockSynchronizer())
        dispatcher = d

        d.streamLifecycleEvent.test {
            // Subscribe first, then start the listener so no early emissions are missed.
            d.start()

            assertEquals("a", assertIs<StreamLifecycleEvent.Start>(awaitItem()).message.payload.player?.codecHeader)
            assertEquals(StreamLifecycleEvent.End, awaitItem())
            assertEquals("b", assertIs<StreamLifecycleEvent.Start>(awaitItem()).message.payload.player?.codecHeader)
            assertEquals(StreamLifecycleEvent.End, awaitItem())
            assertEquals("c", assertIs<StreamLifecycleEvent.Start>(awaitItem()).message.payload.player?.codecHeader)
            assertEquals(StreamLifecycleEvent.Clear, awaitItem())

            cancelAndIgnoreRemainingEvents()
        }
    }
}
