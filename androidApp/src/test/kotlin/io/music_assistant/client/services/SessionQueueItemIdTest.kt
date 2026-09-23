package io.music_assistant.client.services

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

/**
 * [sessionQueueItemId] is the single source of truth both the queue writer (building each
 * [android.support.v4.media.session.MediaSessionCompat.QueueItem]'s id) and the playback writer
 * (setActiveQueueItemId, via [MediaNotificationData.longItemId]) must agree on -- otherwise the
 * active id silently never matches anything in the queue list, which is what made Android Auto's
 * queue screen always open scrolled to the top instead of the currently playing track.
 */
class SessionQueueItemIdTest {
    @Test
    fun `same queue slot id always produces the same session id`() {
        assertEquals(sessionQueueItemId("slot-1"), sessionQueueItemId("slot-1"))
    }

    @Test
    fun `two duplicate tracks occupying different slots get different session ids`() {
        // Same track (e.g. repeat mode, or added to the queue twice) at two different
        // positions must resolve to two distinct slots, not collapse to one.
        val firstSlot = sessionQueueItemId("slot-1")
        val secondSlot = sessionQueueItemId("slot-2")

        assertNotEquals(firstSlot, secondSlot)
    }
}
