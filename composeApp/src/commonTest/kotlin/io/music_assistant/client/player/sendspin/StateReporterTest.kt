package io.music_assistant.client.player.sendspin

import io.music_assistant.client.player.sendspin.model.PlayerStateValue
import io.music_assistant.client.player.sendspin.protocol.MessageDispatcher
import io.music_assistant.client.player.sendspin.session.SendspinOutboundSender
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

/**
 * A state report races a transport that is going down all the time: the channel
 * closes between the state change and the send. Its callers in [SendspinClient]
 * are flow collectors in a supervised scope with no exception handler, so an
 * escaping throw reaches the platform's default handler and kills the app.
 * Reporting is best-effort — a dropped report is corrected by the next one.
 */
class StateReporterTest {
    private object DeadSender : SendspinOutboundSender {
        override suspend fun sendJson(json: String): Unit = error("Channel not open (state: Closed)")
    }

    @Test
    fun reportNowSwallowsASendFailureOnADeadTransport() = runTest {
        val dispatcher = MessageDispatcher(emptyFlow(), DeadSender, ClockSynchronizer())
        val reporter = StateReporter(dispatcher) { SendspinState.Idle }

        // Must not throw.
        reporter.reportNow(PlayerStateValue.SYNCHRONIZED)

        reporter.close()
        dispatcher.stop()
    }
}
