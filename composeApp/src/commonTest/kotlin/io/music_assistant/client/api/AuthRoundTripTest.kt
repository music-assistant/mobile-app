package io.music_assistant.client.api

import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * Guards the transport-failure vs server-rejection distinction and the retry budget
 * that decides when a persistent transport failure escalates to the login screen.
 */
class AuthRoundTripTest {
    private fun answer(rawJson: String): Answer =
        Answer(Json.parseToJsonElement(rawJson) as JsonObject)

    private fun failed() = Result.failure<Answer>(IllegalStateException("Not connected"))

    @Test
    fun silentReauthBelowBudgetIsNotSurfaced() {
        val result = classifyAuthRoundTrip(
            response = failed(),
            isAutoLogin = true,
            priorSilentFailures = 1,
            maxSilentFailures = 3,
        )

        assertTrue(result is AuthRoundTrip.NoResponse)
        assertEquals(
            false,
            result.surfaceAsFailure,
            "A transient silent re-auth failure must not bounce the user to the login screen",
        )
    }

    @Test
    fun silentReauthAtBudgetIsSurfaced() {
        // priorSilentFailures = 2 means this is the 3rd consecutive failure → budget of 3 reached.
        val result = classifyAuthRoundTrip(
            response = failed(),
            isAutoLogin = true,
            priorSilentFailures = 2,
            maxSilentFailures = 3,
        )

        assertTrue(result is AuthRoundTrip.NoResponse)
        assertEquals(
            true,
            result.surfaceAsFailure,
            "A persistent silent re-auth failure must escalate so the user can reach login/Settings",
        )
    }

    @Test
    fun userLoginIsSurfacedRegardlessOfBudget() {
        val result = classifyAuthRoundTrip(
            response = failed(),
            isAutoLogin = false,
            priorSilentFailures = 0,
            maxSilentFailures = 3,
        )

        assertTrue(result is AuthRoundTrip.NoResponse)
        assertEquals(
            true,
            result.surfaceAsFailure,
            "A failed user-initiated login must always surface so the user gets feedback",
        )
    }

    @Test
    fun serverErrorCodeIsRejectionWithMessage() {
        val result = classifyAuthRoundTrip(
            response = Result.success(answer("""{"message_id":"m1","error_code":20,"error":"Token expired"}""")),
            isAutoLogin = true,
            priorSilentFailures = 0,
            maxSilentFailures = 3,
        )

        assertTrue(result is AuthRoundTrip.Rejected)
        assertEquals("Token expired", result.message)
    }

    @Test
    fun serverErrorCodeWithoutMessageFallsBack() {
        val result = classifyAuthRoundTrip(
            response = Result.success(answer("""{"message_id":"m1","error_code":20}""")),
            isAutoLogin = true,
            priorSilentFailures = 0,
            maxSilentFailures = 3,
        )

        assertTrue(result is AuthRoundTrip.Rejected)
        assertEquals("Authentication failed", result.message)
    }

    @Test
    fun cleanResponseIsResponded() {
        val payload = answer("""{"message_id":"m1","result":{"user":{"username":"daveb"}}}""")

        val result = classifyAuthRoundTrip(
            response = Result.success(payload),
            isAutoLogin = true,
            priorSilentFailures = 0,
            maxSilentFailures = 3,
        )

        assertTrue(result is AuthRoundTrip.Responded)
        assertEquals(payload, result.answer)
    }

    @Test
    fun sendFailureIsClassifiedAsNotSent() {
        val result = classifyAuthRoundTrip(
            response = failed(),
            isAutoLogin = false,
            priorSilentFailures = 0,
            maxSilentFailures = 3,
        )

        assertTrue(result is AuthRoundTrip.NoResponse)
        assertEquals(
            AuthFailureCause.NOT_SENT,
            result.cause,
            "A failed send means the server never saw the request — it must not read as server silence",
        )
    }

    @Test
    fun roundTripTimeoutIsClassifiedAsNoReply() {
        val result = classifyAuthRoundTrip(
            response = Result.failure(AuthRoundTripTimeout(5_000)),
            isAutoLogin = false,
            priorSilentFailures = 0,
            maxSilentFailures = 3,
        )

        assertTrue(result is AuthRoundTrip.NoResponse)
        assertEquals(
            AuthFailureCause.NO_REPLY,
            result.cause,
            "A request that went out and got no answer is the one true server-silence case",
        )
    }

    // --- authRoundTrip (timeout helper) ---

    @Test
    fun roundTripTimesOutToFailure() = runTest {
        val result = authRoundTrip(timeoutMs = 5_000) { awaitCancellation() }

        assertTrue(result.isFailure)
        assertTrue(
            result.exceptionOrNull() is AuthRoundTripTimeout,
            "A round-trip that never completes must surface as an AuthRoundTripTimeout failure",
        )
    }

    @Test
    fun roundTripReturnsCompletedResultBeforeTimeout() = runTest {
        val payload = answer("""{"message_id":"m1","result":{"user":{"username":"daveb"}}}""")

        val result = authRoundTrip(timeoutMs = 5_000) { Result.success(payload) }

        assertEquals(payload, result.getOrNull())
    }

    @Test
    fun roundTripPropagatesSendFailure() = runTest {
        val boom = IllegalStateException("Not connected")

        val result = authRoundTrip(timeoutMs = 5_000) { Result.failure(boom) }

        assertSame(boom, result.exceptionOrNull())
    }
}
