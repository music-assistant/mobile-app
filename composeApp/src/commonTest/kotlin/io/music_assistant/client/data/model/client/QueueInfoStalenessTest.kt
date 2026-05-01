package io.music_assistant.client.data.model.client

import io.music_assistant.client.data.model.client.QueueInfo.Companion.isStaleReplay
import io.music_assistant.client.data.model.server.RepeatMode
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Guards the staleness gate used to drop server queue replays. The MA server
 * occasionally re-emits queue events that predate fresher state we already
 * received (observed around Siri "next track" handoffs and rapid queue
 * mutations). Without the gate, a stale replay clobbers fresher state and the
 * user-visible playhead snaps backward.
 *
 * The gate is a pure comparison on `elapsedTimeLastUpdated`, deliberately not
 * `elapsedTime` (which is legitimately null mid-pause-transition).
 */
class QueueInfoStalenessTest {
    private fun queueInfoOf(
        id: String,
        elapsedTimeLastUpdated: Double?,
        elapsedTime: Double? = elapsedTimeLastUpdated,
    ) = QueueInfo(
        id = id,
        available = true,
        shuffleEnabled = false,
        repeatMode = RepeatMode.OFF,
        elapsedTime = elapsedTime,
        elapsedTimeLastUpdated = elapsedTimeLastUpdated,
        currentItem = null,
    )

    @Test
    fun monotonicTimestampsAreNotStale() {
        val first = queueInfoOf("q1", 1000.0)
        val second = queueInfoOf("q1", 1001.5)

        assertFalse(
            isStaleReplay(incoming = second, existing = first),
            "An event whose timestamp is newer than what we have must not be dropped",
        )
    }

    @Test
    fun decreasingTimestampIsStale() {
        val current = queueInfoOf("q1", 1001.5)
        val replay = queueInfoOf("q1", 1000.0)

        assertTrue(
            isStaleReplay(incoming = replay, existing = current),
            "An event with an older timestamp than current must be dropped",
        )
    }

    @Test
    fun equalTimestampIsNotStale() {
        // The gate uses strict less-than, so an event with an equal timestamp
        // is admitted. Server-replay scenarios produce strictly older stamps;
        // a same-stamp event is more likely a benign retransmit on a freshly
        // re-established transport, where dropping it would lose state.
        val current = queueInfoOf("q1", 1000.0)
        val sameStamp = queueInfoOf("q1", 1000.0)

        assertFalse(
            isStaleReplay(incoming = sameStamp, existing = current),
            "An event with the same timestamp as current must not be dropped",
        )
    }

    @Test
    fun differentQueueIdsAreNeverStale() {
        // The gate is per-queue. An older event for queue B is unrelated to
        // a fresher event for queue A; comparing across ids would
        // permanently silence one of them on a multi-queue server.
        val queueA = queueInfoOf("queueA", 5000.0)
        val queueB = queueInfoOf("queueB", 1000.0)

        assertFalse(
            isStaleReplay(incoming = queueB, existing = queueA),
            "Cross-queue comparisons must not flag staleness",
        )
    }

    @Test
    fun firstEventForQueueIsNeverStale() {
        // No existing state means we have nothing to compare against — the
        // event is unconditionally fresh.
        val first = queueInfoOf("q1", 1000.0)

        assertFalse(
            isStaleReplay(incoming = first, existing = null),
            "An event with no prior state must always be admitted",
        )
    }

    @Test
    fun optimisticBumpedTimestampPreservesAgainstStaleServerEvent() {
        // Optimistic UI writes (e.g. ToggleShuffle) bump elapsedTimeLastUpdated
        // to a value strictly above the last known server stamp (existing +
        // tiny epsilon, see LocalPlayerRepository) so a server replay whose
        // timestamp predates the user action is rejected. This documents the
        // contract.
        val lastServerEvent = queueInfoOf("q1", 1000.0)
        val optimistic = lastServerEvent.copy(
            shuffleEnabled = true,
            // The realistic optimistic stamp: existing + epsilon. The exact
            // epsilon doesn't matter to the gate, only that it's > existing.
            elapsedTimeLastUpdated = 1000.0001,
        )
        val staleServerEvent = queueInfoOf("q1", 999.5)

        assertTrue(
            isStaleReplay(incoming = staleServerEvent, existing = optimistic),
            "A server event older than the optimistic bump must be dropped to preserve optimistic state",
        )
    }

    @Test
    fun freshServerConfirmationOverridesOptimistic() {
        // The flip side of the optimistic case: a server confirmation that
        // arrives *after* the optimistic write must be admitted. Otherwise
        // the optimistic state would be sticky against legitimate server
        // updates. Realistically the server confirmation arrives with a stamp
        // that's the network round-trip + server processing above the last
        // server event — orders of magnitude above the optimistic epsilon.
        val optimistic = queueInfoOf("q1", 1000.0001)
        val freshConfirmation = queueInfoOf("q1", 1000.5)

        assertFalse(
            isStaleReplay(incoming = freshConfirmation, existing = optimistic),
            "A server event newer than the optimistic bump must override",
        )
    }

    @Test
    fun nullIncomingTimestampDisablesGate() {
        // A `null` incoming stamp means "we have no monotonic signal." The gate
        // must short-circuit to admit, so a future server build that omits the
        // field can't silently drop a legitimate update.
        val current = queueInfoOf("q1", 1000.0)
        val malformed = queueInfoOf(id = "q1", elapsedTimeLastUpdated = null)

        assertFalse(
            isStaleReplay(incoming = malformed, existing = current),
            "A null incoming timestamp must bypass the gate (admit), not be treated as 0.0",
        )
    }

    @Test
    fun nullExistingTimestampDisablesGate() {
        // Symmetric case: if the existing entry was decoded from a malformed
        // payload (no stamp), the next event with a real stamp must still be
        // admitted — otherwise we'd be permanently locked out of updating
        // that queue.
        val malformedExisting = queueInfoOf(id = "q1", elapsedTimeLastUpdated = null)
        val incoming = queueInfoOf("q1", 1000.0)

        assertFalse(
            isStaleReplay(incoming = incoming, existing = malformedExisting),
            "A null existing timestamp must bypass the gate (admit) — never permanently lock out updates",
        )
    }

    @Test
    fun bothNullTimestampsAdmit() {
        val malformedCurrent = queueInfoOf(id = "q1", elapsedTimeLastUpdated = null)
        val malformedIncoming = queueInfoOf(id = "q1", elapsedTimeLastUpdated = null)

        assertFalse(
            isStaleReplay(incoming = malformedIncoming, existing = malformedCurrent),
            "When neither side has a stamp, the gate is fully disabled — admit",
        )
    }
}
