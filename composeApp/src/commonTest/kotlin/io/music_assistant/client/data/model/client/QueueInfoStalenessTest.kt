package io.music_assistant.client.data.model.client

import io.music_assistant.client.data.model.server.RepeatMode
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Pins the [QueueInfo.isBefore] comparison used by the staleness gate that
 * drops server queue replays. The MA server occasionally re-emits queue
 * events that predate fresher state we already received (observed around
 * Siri "next track" handoffs and rapid queue mutations); without the gate,
 * a stale replay clobbers fresher state and the user-visible playhead snaps
 * backward.
 *
 * Cross-id and existing==null cases are the caller's concern (handled in
 * `MainDataSource.takeIfNotStale` before calling [isBefore]).
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
    fun olderIsBeforeNewer() {
        val older = queueInfoOf("q1", 1000.0)
        val newer = queueInfoOf("q1", 1001.5)

        assertTrue(
            older.isBefore(newer),
            "An event with an older timestamp should be flagged as before",
        )
    }

    @Test
    fun newerIsNotBeforeOlder() {
        val older = queueInfoOf("q1", 1000.0)
        val newer = queueInfoOf("q1", 1001.5)

        assertFalse(
            newer.isBefore(older),
            "An event with a newer timestamp must not be flagged as before",
        )
    }

    @Test
    fun equalStampsAreNotBefore() {
        // The gate uses strict less-than, so an event with an equal timestamp
        // is admitted. Server replays produce strictly older stamps; a
        // same-stamp event is more likely a benign retransmit on a freshly
        // re-established transport, where dropping it would lose state.
        val a = queueInfoOf("q1", 1000.0)
        val b = queueInfoOf("q1", 1000.0)

        assertFalse(a.isBefore(b))
    }

    @Test
    fun nullIncomingStampShortCircuits() {
        // A `null` stamp on the receiver means "we have no monotonic signal"
        // — the comparison must short-circuit to false (admit), so a future
        // server build that omits the field can't silently drop legitimate
        // updates.
        val malformed = queueInfoOf("q1", elapsedTimeLastUpdated = null)
        val real = queueInfoOf("q1", 1000.0)

        assertFalse(
            malformed.isBefore(real),
            "Null receiver stamp must short-circuit to admit",
        )
    }

    @Test
    fun nullOtherStampShortCircuits() {
        // Symmetric case: if the existing entry was decoded from a malformed
        // payload (no stamp), the next event with a real stamp must still be
        // admitted.
        val real = queueInfoOf("q1", 1000.0)
        val malformed = queueInfoOf("q1", elapsedTimeLastUpdated = null)

        assertFalse(
            real.isBefore(malformed),
            "Null other stamp must short-circuit to admit",
        )
    }

    @Test
    fun bothNullStampsShortCircuit() {
        val a = queueInfoOf("q1", elapsedTimeLastUpdated = null)
        val b = queueInfoOf("q1", elapsedTimeLastUpdated = null)

        assertFalse(a.isBefore(b))
    }

    @Test
    fun staleServerEventIsBeforeOptimisticBump() {
        // Optimistic UI writes (e.g. ToggleShuffle) bump elapsedTimeLastUpdated
        // to a value strictly above the last known server stamp (existing +
        // tiny epsilon, see LocalPlayerRepository) so a server replay whose
        // timestamp predates the user action is rejected. This documents the
        // contract.
        val lastServerEvent = queueInfoOf("q1", 1000.0)
        val optimistic = lastServerEvent.copy(
            shuffleEnabled = true,
            elapsedTimeLastUpdated = 1000.0001,
        )
        val staleServerEvent = queueInfoOf("q1", 999.5)

        assertTrue(
            staleServerEvent.isBefore(optimistic),
            "A server event older than the optimistic bump must be flagged",
        )
    }

    @Test
    fun freshServerConfirmationIsNotBeforeOptimistic() {
        // Pins the flip side of the optimistic contract: a confirmation
        // arriving after the optimistic write must override.
        val optimistic = queueInfoOf("q1", 1000.0001)
        val freshConfirmation = queueInfoOf("q1", 1000.5)

        assertFalse(freshConfirmation.isBefore(optimistic))
    }
}
