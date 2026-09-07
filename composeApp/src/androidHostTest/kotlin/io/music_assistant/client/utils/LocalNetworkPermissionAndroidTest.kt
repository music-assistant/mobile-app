package io.music_assistant.client.utils

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

class LocalNetworkPermissionAndroidTest {
    private val gate = LocalNetworkPermissionGate()

    @Test
    fun localNetworkPermissionIsNotAvailable() {
        assertFalse(gate.isAvailable)
    }

    @Test
    fun probeIsNoOpAndReportsGranted() {
        kotlinx.coroutines.test.runTest {
            assertEquals(true, gate.probe(1_000L))
        }
    }

    @Test
    fun darwinErrorsNeverProduceAndroidGuidance() {
        val error = Exception("Error Domain=NSURLErrorDomain Code=-1009")

        assertFalse(gate.isLikelyLocalNetworkBlocked(error))
        assertNull(gate.guidanceFor(error, probeGranted = null, locallyBlocked = false))
        assertNull(gate.guidanceFor(error, probeGranted = false, locallyBlocked = true))
    }
}
