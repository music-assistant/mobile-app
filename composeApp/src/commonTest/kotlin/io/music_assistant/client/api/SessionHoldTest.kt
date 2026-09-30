package io.music_assistant.client.api

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TestTimeSource

class SessionHoldTest {
    private val clock = TestTimeSource()
    private val hold = SessionHold(ttl = 10.seconds, clock = clock)

    @Test
    fun anIdleSessionIsNotHeld() {
        assertFalse(hold.isHeld)
    }

    @Test
    fun aHoldExpiresAtTheEndOfItsTtl() {
        hold.hold()
        assertTrue(hold.isHeld)
        clock += 9_999.milliseconds
        assertTrue(hold.isHeld)
        clock += 1.milliseconds
        assertFalse(hold.isHeld)
    }

    @Test
    fun aSecondHoldGetsAFullTtl() {
        hold.hold()
        clock += 9.seconds
        hold.hold()
        clock += 9.seconds
        assertTrue(hold.isHeld)
        clock += 1.seconds
        assertFalse(hold.isHeld)
    }

    @Test
    fun clearReleasesTheHoldImmediately() {
        hold.hold()
        hold.clear()
        assertFalse(hold.isHeld)
    }
}
