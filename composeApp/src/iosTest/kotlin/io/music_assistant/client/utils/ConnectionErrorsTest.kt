package io.music_assistant.client.utils

import kotlin.test.Test
import musicassistantclient.composeapp.generated.resources.Res
import musicassistantclient.composeapp.generated.resources.settings_error_local_network_ios
import musicassistantclient.composeapp.generated.resources.settings_error_offline_ios
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Pins the iOS Local Network permission signature: Code=1009/-1009 must match through
 * transport wrappers and cause chains, without matching other Darwin codes.
 */
class ConnectionErrorsTest {
    @Test
    fun matchesIssueReportMessage() {
        val error = Exception(
            "Exception in http request: Error Domain=NSURLErrorDomain Code=1009 " +
                "\"The internet connection appears to be offline.\"",
        )

        assertTrue(LocalNetworkPermissionGate().isLikelyLocalNetworkBlocked(error))
    }

    @Test
    fun matchesNegativeCodeForm() {
        val error = Exception(
            "Exception in http request: Error Domain=NSURLErrorDomain Code=-1009 " +
                "\"The internet connection appears to be offline.\"",
        )

        assertTrue(LocalNetworkPermissionGate().isLikelyLocalNetworkBlocked(error))
    }

    @Test
    fun matchesThroughTransportWrapper() {
        // DirectTransport can wrap failures, e.g. the recovery-machinery death path.
        val darwinError = Exception(
            "Exception in http request: Error Domain=NSURLErrorDomain Code=1009 " +
                "\"The internet connection appears to be offline.\"",
        )
        val wrapped = Exception("Recovery machinery died: something broke", darwinError)

        assertTrue(LocalNetworkPermissionGate().isLikelyLocalNetworkBlocked(wrapped))
    }

    @Test
    fun matchesDescriptionOnlyFallback() {
        val error = Exception("The internet connection appears to be offline.")

        assertTrue(LocalNetworkPermissionGate().isLikelyLocalNetworkBlocked(error))
    }

    @Test
    fun rejectsOtherDarwinCodes() {
        val cannotConnect = Exception(
            "Exception in http request: Error Domain=NSURLErrorDomain Code=-1004 " +
                "\"Could not connect to the server.\"",
        )
        val cannotFindHost = Exception(
            "Exception in http request: Error Domain=NSURLErrorDomain Code=-1003 " +
                "\"A server with the specified hostname could not be found.\"",
        )

        val gate = LocalNetworkPermissionGate()
        assertFalse(gate.isLikelyLocalNetworkBlocked(cannotConnect))
        assertFalse(gate.isLikelyLocalNetworkBlocked(cannotFindHost))
    }

    @Test
    fun rejectsNonIosErrors() {
        // Typical Android connect failure.
        val androidError = Exception("Failed to connect to /192.168.1.10:8095")

        assertFalse(LocalNetworkPermissionGate().isLikelyLocalNetworkBlocked(androidError))
    }

    @Test
    fun rejectsNullMessage() {
        val error = Exception(null as String?)

        assertFalse(LocalNetworkPermissionGate().isLikelyLocalNetworkBlocked(error))
    }

    @Test
    fun selectsPermissionGuidanceUntilProbeConfirmsAccess() {
        val error = Exception("Error Domain=NSURLErrorDomain Code=-1009")
        val gate = LocalNetworkPermissionGate()

        assertEquals(
            Res.string.settings_error_local_network_ios,
            gate.guidanceFor(error, probeGranted = null, locallyBlocked = false),
        )
        assertEquals(
            Res.string.settings_error_offline_ios,
            gate.guidanceFor(error, probeGranted = true, locallyBlocked = false),
        )
    }
}
