package io.music_assistant.client.api

import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The silent-reauth retry engine: a reconnect re-auth that fails to complete is
 * retried in place, and only surfaced to login once the consecutive-failure budget
 * is spent.
 */
class SilentReauthTest {
    private fun answer(rawJson: String): Answer =
        Answer(Json.parseToJsonElement(rawJson) as JsonObject)

    private fun engine(maxSilentFailures: Int = 3) =
        SilentReauth(
            ReauthPolicy(
                maxSilentFailures = maxSilentFailures,
                roundTripTimeoutMs = 5_000,
                retryDelayMs = 1_000,
            ),
        )

    private val userPayload = """{"message_id":"m","result":{"user":{"username":"daveb"}}}"""

    @Test
    fun toleratedTimeoutRetriesUntilSuccess() = runTest {
        var calls = 0

        val resolution = engine().resolve(
            isAutoLogin = true,
            shouldAttempt = { true },
            onAttempt = {},
            send = {
                calls++
                if (calls == 1) awaitCancellation() else answer(userPayload).let { Result.success(it) }
            },
        )

        assertTrue(resolution is AuthResolution.Authenticated)
        assertEquals(2, calls, "A tolerated timeout must trigger a retry, not dead-end on InProgress")
    }

    @Test
    fun persistentSilentTimeoutsSurfaceAfterBudget() = runTest {
        var calls = 0

        val resolution = engine(maxSilentFailures = 3).resolve(
            isAutoLogin = true,
            shouldAttempt = { true },
            onAttempt = {},
            send = {
                calls++
                awaitCancellation()
            },
        )

        assertTrue(resolution is AuthResolution.Surface)
        assertEquals(3, calls, "Budget of 3 means three attempts before surfacing login")
    }

    @Test
    fun manualLoginTimeoutSurfacesWithoutRetry() = runTest {
        var calls = 0

        val resolution = engine().resolve(
            isAutoLogin = false,
            shouldAttempt = { true },
            onAttempt = {},
            send = {
                calls++
                awaitCancellation()
            },
        )

        assertTrue(resolution is AuthResolution.Surface)
        assertEquals(1, calls, "A user-initiated login must surface on the first failure, no silent retry")
    }

    @Test
    fun surfacedMessageDistinguishesAnUnsentRequestFromServerSilence() = runTest {
        val notSent = engine().resolve(
            isAutoLogin = false,
            shouldAttempt = { true },
            onAttempt = {},
            send = { Result.failure(IllegalStateException("Not connected")) },
        )
        val noReply = engine().resolve(
            isAutoLogin = false,
            shouldAttempt = { true },
            onAttempt = {},
            send = { awaitCancellation() },
        )

        assertTrue(notSent is AuthResolution.Surface)
        assertTrue(noReply is AuthResolution.Surface)
        assertEquals("No response from server", noReply.message)
        assertTrue(
            notSent.message != noReply.message,
            "A request that never left the device must not be reported as server silence",
        )
    }

    @Test
    fun serverRejectionRejectsWithoutRetry() = runTest {
        var calls = 0

        val resolution = engine().resolve(
            isAutoLogin = true,
            shouldAttempt = { true },
            onAttempt = {},
            send = {
                calls++
                Result.success(answer("""{"message_id":"m","error_code":20,"error":"Token expired"}"""))
            },
        )

        assertTrue(resolution is AuthResolution.Reject)
        assertEquals("Token expired", resolution.message)
        assertEquals(1, calls)
    }

    @Test
    fun abortsWhenConnectionDropsDuringAttempt() = runTest {
        var connected = true

        val resolution = engine().resolve(
            isAutoLogin = true,
            shouldAttempt = { connected },
            onAttempt = {},
            send = {
                connected = false
                awaitCancellation()
            },
        )

        assertTrue(resolution is AuthResolution.Aborted)
    }

    @Test
    fun silentFailuresPersistAcrossResolveCalls() = runTest {
        val engine = engine(maxSilentFailures = 2)
        var calls = 0
        val send: suspend () -> Result<Answer> = {
            calls++
            awaitCancellation()
        }

        assertTrue(engine.resolve(true, { true }, {}, send) is AuthResolution.Surface)
        val afterFirst = calls
        assertTrue(engine.resolve(true, { true }, {}, send) is AuthResolution.Surface)

        assertEquals(
            1,
            calls - afterFirst,
            "A prior episode's failures must carry over so a flapping transport still escalates",
        )
    }

    @Test
    fun resetClearsAccumulatedFailures() = runTest {
        val engine = engine(maxSilentFailures = 2)
        var calls = 0
        val send: suspend () -> Result<Answer> = {
            calls++
            awaitCancellation()
        }

        engine.resolve(true, { true }, {}, send)
        engine.reset()
        val afterReset = calls
        engine.resolve(true, { true }, {}, send)

        assertEquals(2, calls - afterReset, "reset() must restore the full budget for a new episode")
    }
}
