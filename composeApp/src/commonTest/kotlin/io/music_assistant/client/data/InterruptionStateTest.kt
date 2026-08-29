package io.music_assistant.client.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class InterruptionStateTest {
    @Test
    fun `sent pause may resume after allowed end`() {
        val paused = InterruptionState.Idle.began(token = 4, pauseSent = true)
        val ended = paused.ended(resumeAllowed = true, currentToken = 4)

        assertTrue(ended.shouldResume)
        assertEquals(4, ended.token)
        assertEquals(InterruptionState.Idle, ended.state)
    }

    @Test
    fun `queued pause does not auto resume`() {
        val paused = InterruptionState.Idle.began(token = 4, pauseSent = false)

        assertFalse(paused.ended(resumeAllowed = true, currentToken = 4).shouldResume)
    }

    @Test
    fun `user cancellation token prevents resume`() {
        val paused = InterruptionState.Idle.began(token = 4, pauseSent = true)

        assertFalse(paused.ended(resumeAllowed = true, currentToken = 5).shouldResume)
    }

    @Test
    fun `system may deny resume`() {
        val paused = InterruptionState.Idle.began(token = 4, pauseSent = true)

        assertFalse(paused.ended(resumeAllowed = false, currentToken = 4).shouldResume)
    }

    @Test
    fun `duplicate begin keeps original transaction`() {
        val paused = InterruptionState.Idle.began(token = 4, pauseSent = true)

        assertEquals(paused, paused.began(token = 5, pauseSent = true))
    }

    @Test
    fun `unmatched and duplicate ends never resume`() {
        val unmatched = InterruptionState.Idle.ended(resumeAllowed = true, currentToken = 4)
        val paused = InterruptionState.Idle.began(token = 4, pauseSent = true)
        val first = paused.ended(resumeAllowed = true, currentToken = 4)
        val duplicate = first.state.ended(resumeAllowed = true, currentToken = 4)

        assertFalse(unmatched.shouldResume)
        assertFalse(duplicate.shouldResume)
    }
}
