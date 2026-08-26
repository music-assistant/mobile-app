package io.music_assistant.client.player.sendspin.session

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

/**
 * A transport whose connect() fails synchronously must complete the session's
 * initial outcome immediately — a caller awaiting it must not sit out the full
 * attach timeout for a failure that already happened.
 */
class SessionConnectFailureTest {
    @Test
    fun synchronousConnectFailureCompletesTheInitialOutcomeImmediately() = runTest {
        val transport = FakeSendspinTransport()
        transport.onConnect = { throw IllegalStateException("channel never opened") }
        val session = LegacySession(
            transport = transport,
            config = LegacySessionConfig(
                requiresAuth = false,
                authJson = null,
                helloJson = """{"type":"client/hello"}""",
            ),
        )
        val failure = assertFailsWith<IllegalStateException> { session.start() }
        assertEquals("channel never opened", failure.message)

        val outcome = session.awaitInitialOutcome()
        assertIs<SessionOutcome.Failed>(outcome)
        assertEquals("channel never opened", outcome.cause.message)
        session.close()
    }
}
