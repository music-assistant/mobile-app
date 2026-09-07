package io.music_assistant.client.api

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TestTimeSource

class PlaybackRecoveryWindowTest {
    private val clock = TestTimeSource()
    private val window = PlaybackRecoveryWindow(clock)

    @Test
    fun pausedIdleDoesNotAllowBackgroundRecoveryWithoutUserIntent() {
        assertFalse(window.isActive)
    }

    @Test
    fun explicitPlayProtectsRecoveryButCannotKeepItAliveIndefinitely() {
        window.request()
        assertTrue(window.isActive)
        clock += 9_999.milliseconds
        assertTrue(window.isActive)
        clock += 1.milliseconds
        assertFalse(window.isActive)
    }

    @Test
    fun anotherExplicitPlayGetsItsOwnFullGracePeriod() {
        window.request()
        clock += 9.seconds
        window.request()
        clock += 1.seconds
        assertTrue(window.isActive)
        clock += 9.seconds
        assertFalse(window.isActive)
    }

    @Test
    fun userDisconnectClearsTheGracePeriod() {
        window.request()
        window.clear()
        assertFalse(window.isActive)
    }
}
