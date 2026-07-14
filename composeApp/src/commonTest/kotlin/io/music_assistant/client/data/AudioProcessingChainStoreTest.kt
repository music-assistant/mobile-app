package io.music_assistant.client.data

import io.music_assistant.client.data.model.server.AudioProcessingChain
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AudioProcessingChainStoreTest {
    @Test
    fun replacesOnlyWithIncreasingRevision() {
        val store = AudioProcessingChainStore()
        store.applyEvent("queue", chain(revision = 2, state = "ready"))

        store.applyEvent("queue", chain(revision = 1, state = "older"))
        store.applyEvent("queue", chain(revision = 2, state = "equal"))
        assertEquals("ready", store.snapshots.value["queue"]?.state)

        store.applyEvent("queue", chain(revision = 3, state = "newer"))
        assertEquals("newer", store.snapshots.value["queue"]?.state)
    }

    @Test
    fun normalizesMissingQueueIdAndRejectsMismatch() {
        val store = AudioProcessingChainStore()

        store.applyEvent("queue", chain(queueId = "", revision = 1))
        assertEquals("queue", store.snapshots.value["queue"]?.queueId)

        store.applyEvent("queue", chain(queueId = "other", revision = 2))
        assertEquals(1L, store.snapshots.value["queue"]?.revision)
    }

    @Test
    fun nullAndStoppedQueueClearSnapshot() {
        val store = AudioProcessingChainStore()
        store.applyEvent("queue", chain(revision = 1))

        store.clearStoppedQueue("queue", "item")
        assertTrue(store.snapshots.value.containsKey("queue"))

        store.clearStoppedQueue("queue", null)
        assertTrue(store.snapshots.value.isEmpty())

        store.applyEvent("queue", chain(revision = 2))
        store.applyEvent("queue", null)
        assertTrue(store.snapshots.value.isEmpty())

        store.applyEvent("queue", chain(revision = 2, state = "delayed"))
        assertTrue(store.snapshots.value.isEmpty())

        store.applyEvent("queue", chain(revision = 3, state = "new-session"))
        assertEquals("new-session", store.snapshots.value["queue"]?.state)
    }

    @Test
    fun resetConnectionAcceptsLowerProcessLocalRevisionFromRefetch() {
        val store = AudioProcessingChainStore()
        store.applyEvent("queue", chain(revision = 100, state = "old-process"))
        val oldConnectionFetch = store.captureFetch("queue")

        store.resetConnection()
        val newConnectionFetch = store.captureFetch("queue")
        assertTrue(
            store.applyFetch(
                newConnectionFetch,
                chain(revision = 1, state = "new-process"),
            ),
        )

        assertEquals("new-process", store.snapshots.value["queue"]?.state)
        assertFalse(
            store.applyFetch(
                oldConnectionFetch,
                chain(revision = 101, state = "delayed-old-process"),
            ),
        )
        assertEquals("new-process", store.snapshots.value["queue"]?.state)
    }

    @Test
    fun reconnectInvalidatesFetchBeforeReplacementSessionIsReady() {
        val store = AudioProcessingChainStore()
        store.applyEvent("queue", chain(revision = 1, state = "stale-visible"))
        val fetch = store.captureFetch("queue")

        store.invalidateConnection()

        assertEquals("stale-visible", store.snapshots.value["queue"]?.state)
        assertFalse(store.applyFetch(fetch, null))
        assertEquals("stale-visible", store.snapshots.value["queue"]?.state)
    }

    @Test
    fun delayedNullFetchCannotClearInterveningEvent() {
        val store = AudioProcessingChainStore()
        store.applyEvent("queue", chain(revision = 1, state = "initial"))
        val fetch = store.captureFetch("queue")

        store.applyEvent("queue", chain(revision = 2, state = "event"))

        assertFalse(store.applyFetch(fetch, null))
        assertEquals("event", store.snapshots.value["queue"]?.state)
    }

    @Test
    fun delayedSnapshotFetchCannotResurrectAfterClearEvent() {
        val store = AudioProcessingChainStore()
        store.applyEvent("queue", chain(revision = 1, state = "initial"))
        val fetch = store.captureFetch("queue")

        store.applyEvent("queue", null)

        assertFalse(store.applyFetch(fetch, chain(revision = 2, state = "delayed-fetch")))
        assertTrue(store.snapshots.value.isEmpty())
    }

    @Test
    fun eventAfterFetchResultStillWins() {
        val store = AudioProcessingChainStore()
        val fetch = store.captureFetch("queue")

        assertTrue(store.applyFetch(fetch, chain(revision = 1, state = "fetch")))
        store.applyEvent("queue", chain(revision = 2, state = "event"))

        assertEquals("event", store.snapshots.value["queue"]?.state)
    }

    @Test
    fun newerFetchInvalidatesOlderFetchForSameQueue() {
        val store = AudioProcessingChainStore()
        val olderFetch = store.captureFetch("queue")
        val newerFetch = store.captureFetch("queue")

        assertFalse(store.applyFetch(olderFetch, chain(revision = 2, state = "older-fetch")))
        assertTrue(store.applyFetch(newerFetch, chain(revision = 1, state = "newer-fetch")))
        assertEquals("newer-fetch", store.snapshots.value["queue"]?.state)
    }

    @Test
    fun stoppedQueueInvalidatesCapturedFetch() {
        val store = AudioProcessingChainStore()
        store.applyEvent("queue", chain(revision = 1))
        val fetch = store.captureFetch("queue")

        store.clearStoppedQueue("queue", null)

        assertFalse(store.applyFetch(fetch, chain(revision = 2)))
        assertTrue(store.snapshots.value.isEmpty())
    }

    @Test
    fun presentationRequiresCurrentQueueItemMatch() {
        val snapshots = mapOf("queue" to chain(revision = 1, queueItemId = "item"))

        assertEquals(
            snapshots["queue"],
            selectAudioProcessingChain(snapshots, "queue", "item"),
        )
        assertNull(selectAudioProcessingChain(snapshots, "queue", "next-item"))
        assertNull(selectAudioProcessingChain(snapshots, "other-queue", "item"))
        assertNull(selectAudioProcessingChain(snapshots, "queue", null))
    }

    @Test
    fun commandSupportStartsAtSchemaThirtyEight() {
        assertTrue(supportsAudioProcessingChainSchema(38))
        assertTrue(supportsAudioProcessingChainSchema(39))
        assertEquals(false, supportsAudioProcessingChainSchema(37))
        assertEquals(false, supportsAudioProcessingChainSchema(null))
    }

    private fun chain(
        queueId: String = "queue",
        queueItemId: String = "item",
        revision: Long,
        state: String = "ready",
    ) = AudioProcessingChain(
        queueId = queueId,
        queueItemId = queueItemId,
        revision = revision,
        state = state,
    )
}
