package io.music_assistant.client.data.model.server

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Pins the handoff floor. Server PR #4412 merged while `API_SCHEMA_VERSION` was 34 and
 * did not bump it, so 34 straddles the change and must stay unsupported; 35 is the
 * first bump strictly after the merge.
 */
class LeaderLeaveSupportTest {
    @Test
    fun unknownSchemaIsUnsupported() {
        assertFalse(supportsLeaderLeave(null))
    }

    @Test
    fun schemaBelowFloorIsUnsupported() {
        assertFalse(supportsLeaderLeave(31))
        assertFalse(supportsLeaderLeave(LEADER_LEAVE_MIN_SCHEMA - 1))
    }

    @Test
    fun floorAndAboveAreSupported() {
        assertTrue(supportsLeaderLeave(LEADER_LEAVE_MIN_SCHEMA))
        assertTrue(supportsLeaderLeave(60))
    }
}
