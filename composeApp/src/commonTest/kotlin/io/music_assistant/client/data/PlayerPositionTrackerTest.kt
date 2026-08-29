package io.music_assistant.client.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PlayerPositionTrackerTest {
    @Test
    fun pausedPositionRemainsFixedAndRejectsStaleServerEchoes() {
        val tracker = PlayerPositionTracker()
        val queueId = "queue"

        tracker.setAnchor(queueId = queueId, elapsedSec = 25.0, isPlaying = false)
        tracker.setPausedPosition(queueId = queueId, elapsedSec = 90.0)

        assertEquals(90.0, tracker.effectiveSec(queueId))
        assertTrue(tracker.isFrozenUntilConfirmed(queueId))

        tracker.setAnchor(queueId = queueId, elapsedSec = 25.5, isPlaying = false)

        assertEquals(90.0, tracker.effectiveSec(queueId))
        assertTrue(tracker.isFrozenUntilConfirmed(queueId))
    }

    @Test
    fun optimisticSeekIgnoresStaleServerAnchorFarFromSeekTarget() {
        val tracker = PlayerPositionTracker()
        val queueId = "queue"

        tracker.setAnchor(
            queueId = queueId,
            elapsedSec = 65.0,
            isPlaying = true,
            durationSec = 218.0,
            speed = 1.0,
        )
        tracker.setOptimisticSeek(
            queueId = queueId,
            elapsedSec = 149.0,
            durationSec = 218.0,
            speed = 1.0,
        )
        assertTrue(tracker.isFrozenUntilConfirmed(queueId))

        tracker.setAnchor(
            queueId = queueId,
            elapsedSec = 67.9,
            durationSec = 218.0,
            speed = 1.0,
        )

        assertEquals(149.0, tracker.effectiveSec(queueId))
        assertTrue(tracker.isFrozenUntilConfirmed(queueId))
    }

    @Test
    fun optimisticSeekIgnoresServerAnchorsUntilAudioConfirm() {
        // Server can echo the seek target before audio resumes; that must not unfreeze.
        val tracker = PlayerPositionTracker()
        val queueId = "queue"

        tracker.setAnchor(
            queueId = queueId,
            elapsedSec = 65.0,
            isPlaying = true,
            durationSec = 218.0,
            speed = 1.0,
        )
        tracker.setOptimisticSeek(
            queueId = queueId,
            elapsedSec = 149.0,
            durationSec = 218.0,
            speed = 1.0,
        )
        assertTrue(tracker.isFrozenUntilConfirmed(queueId))

        // Echo at the target still arrives too early.
        tracker.setAnchor(queueId = queueId, elapsedSec = 149.5)
        assertEquals(149.0, tracker.effectiveSec(queueId))
        assertTrue(tracker.isFrozenUntilConfirmed(queueId))

        // Audio sync is the release signal.
        tracker.confirmPlaying(queueId)
        assertTrue(!tracker.isFrozenUntilConfirmed(queueId))
        val pos = tracker.effectiveSec(queueId) ?: error("missing position")
        assertTrue(pos >= 149.0)
    }

    @Test
    fun trackChangeFreezeIgnoresServerAnchorAtBoundaryWhileOldAudioPlays() {
        // Next-track queue metadata can arrive while old audio is still draining.
        val tracker = PlayerPositionTracker()
        val queueId = "queue"

        tracker.setAnchor(queueId = queueId, elapsedSec = 53.0, isPlaying = true)
        tracker.setOptimisticTrackChange(queueId = queueId, elapsedSec = 0.0)
        assertTrue(tracker.isFrozenUntilConfirmed(queueId))
        assertEquals(0.0, tracker.effectiveSec(queueId))

        // Boundary echoes are not audio confirmation.
        tracker.setAnchor(queueId = queueId, elapsedSec = 0.5)

        assertEquals(0.0, tracker.effectiveSec(queueId))
        assertTrue(tracker.isFrozenUntilConfirmed(queueId))

        // Later pre-audio echoes stay frozen too.
        tracker.setAnchor(queueId = queueId, elapsedSec = 1.2)

        assertEquals(0.0, tracker.effectiveSec(queueId))
        assertTrue(tracker.isFrozenUntilConfirmed(queueId))
    }

    @Test
    fun trackChangeFreezeUnfreezesOnAudioConfirm() {
        // New-stream sync releases the boundary freeze.
        val tracker = PlayerPositionTracker()
        val queueId = "queue"

        tracker.setAnchor(queueId = queueId, elapsedSec = 53.0, isPlaying = true)
        tracker.setOptimisticTrackChange(queueId = queueId, elapsedSec = 0.0)
        assertTrue(tracker.isFrozenUntilConfirmed(queueId))

        tracker.confirmPlaying(queueId)

        assertTrue(!tracker.isFrozenUntilConfirmed(queueId))
    }

    @Test
    fun trackChangeFreezeIgnoresAllServerAnchorsUntilAudioConfirm() {
        // Neither old-track nor pre-audio new-track positions are confirmation.
        val tracker = PlayerPositionTracker()
        val queueId = "queue"

        tracker.setAnchor(queueId = queueId, elapsedSec = 53.0, isPlaying = true)
        tracker.setOptimisticTrackChange(queueId = queueId, elapsedSec = 0.0)
        assertTrue(tracker.isFrozenUntilConfirmed(queueId))

        // Late old-track position is still not confirmation.
        tracker.setAnchor(queueId = queueId, elapsedSec = 53.0)
        assertEquals(0.0, tracker.effectiveSec(queueId))
        assertTrue(tracker.isFrozenUntilConfirmed(queueId))

        // Pre-audio new-track position is also not confirmation.
        tracker.setAnchor(queueId = queueId, elapsedSec = 2.0, isPlaying = true)
        assertEquals(0.0, tracker.effectiveSec(queueId))
        assertTrue(tracker.isFrozenUntilConfirmed(queueId))

        // New-stream audio sync releases the freeze.
        tracker.confirmPlaying(queueId)
        assertTrue(!tracker.isFrozenUntilConfirmed(queueId))
    }

    @Test
    fun seekFreezeStillUnfreezesOnAudioConfirm() {
        // Seeks use the same Sendspin confirmation path as track changes.
        val tracker = PlayerPositionTracker()
        val queueId = "queue"

        tracker.setAnchor(queueId = queueId, elapsedSec = 65.0, isPlaying = true)
        tracker.setOptimisticSeek(queueId = queueId, elapsedSec = 149.0)
        assertTrue(tracker.isFrozenUntilConfirmed(queueId))

        tracker.confirmPlaying(queueId)

        assertTrue(!tracker.isFrozenUntilConfirmed(queueId))
    }
}
