package io.music_assistant.sendspin.android

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SinkInterruptionTest {
    private val interruption = SinkInterruption()

    @Test
    fun aTransientFocusLossEndsOnTheGain() {
        interruption.focusLost(transient = true)

        assertTrue(interruption.focusGained())
    }

    @Test
    fun aCallWithoutFocusLossEndsWithCallMode() {
        // Bluetooth and Android Auto calls can change the mode only.
        interruption.callStarted()

        assertTrue(interruption.callEnded())
    }

    @Test
    fun aCallThatAlsoTookTheFocusEndsOnTheGainAfterCallMode() {
        // Telecom leaves call mode about 50 ms before it gives the focus back.
        interruption.focusLost(transient = true)
        interruption.callStarted()

        assertFalse(interruption.callEnded(), "the call still holds the focus")
        assertTrue(interruption.focusGained())
    }

    @Test
    fun aFocusGainDuringTheCallWaitsForCallMode() {
        interruption.callStarted()
        interruption.focusLost(transient = true)

        assertFalse(interruption.focusGained(), "the call is still on")
        assertTrue(interruption.callEnded())
    }

    @Test
    fun aFinalCauseNeverResumes() {
        interruption.callStarted()
        interruption.outputGone()

        assertFalse(interruption.callEnded())
        interruption.focusLost(transient = false)
        assertFalse(interruption.focusGained())
    }

    @Test
    fun anInterruptionEndsOnce() {
        interruption.focusLost(transient = true)
        assertTrue(interruption.focusGained())

        assertFalse(interruption.focusGained(), "a duck's gain is not a resume")
        assertFalse(interruption.callEnded())
    }

    @Test
    fun aGainWithoutAnInterruptionChangesNothing() {
        assertFalse(interruption.focusGained())
    }
}
