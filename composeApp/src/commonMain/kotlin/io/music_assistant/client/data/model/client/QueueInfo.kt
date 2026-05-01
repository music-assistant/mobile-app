package io.music_assistant.client.data.model.client

import io.music_assistant.client.data.model.client.QueueTrack.Companion.toQueueTrack
import io.music_assistant.client.data.model.server.RepeatMode
import io.music_assistant.client.data.model.server.ServerQueue

data class QueueInfo(
    val id: String,
    val available: Boolean,
    val shuffleEnabled: Boolean,
    val repeatMode: RepeatMode?,
    val elapsedTime: Double?,
    /**
     * Server wall clock (Unix epoch seconds, fractional, UTC — DST and
     * timezone changes don't affect this value) when [elapsedTime] was last
     * recomputed. Used as the monotonic staleness signal — a queue event with
     * an older value than the one currently stored for this [id] is a server
     * replay and is dropped.
     *
     * Optimistic UI writes (`LocalPlayerRepository.updateOptimisticQueueInfo`)
     * bump this to a value strictly above the last known server stamp so a
     * subsequent stale server event can't clobber the optimistic state, while
     * any legitimate server confirmation lands far above the bump and
     * overrides it.
     *
     * Nullable to handle the (currently theoretical) case where the server
     * omits the field. A `null` on either side disables the staleness gate
     * for that comparison so a missing stamp can never silently filter out
     * legitimate updates. See [Companion.isStaleReplay].
     */
    val elapsedTimeLastUpdated: Double?,
    val currentItem: QueueTrack?,
) {
    companion object Companion {
        fun ServerQueue.toQueue() = QueueInfo(
            id = queueId,
            available = available,
            shuffleEnabled = shuffleEnabled,
            repeatMode = repeatMode,
            elapsedTime = elapsedTime,
            elapsedTimeLastUpdated = elapsedTimeLastUpdated,
            currentItem = currentItem?.toQueueTrack(),
        )

        /**
         * Returns true when [incoming] is a server replay that should be dropped
         * before it can clobber [existing] state. The comparison is per-queue
         * (different ids never compare) and on `elapsedTimeLastUpdated` only —
         * `elapsedTime` is legitimately null mid-pause-transition, whereas the
         * "last updated" stamp is what we use as the monotonic signal.
         *
         * Returns false when:
         *  - there is no existing state for this queue (every first event is fresh);
         *  - ids differ (unrelated queues);
         *  - either side's `elapsedTimeLastUpdated` is `null` — a missing stamp
         *    on either end disables the gate rather than silently dropping a
         *    legitimate update. The MA server emits the field on every queue
         *    payload today, so a `null` should never appear in practice; the
         *    short-circuit exists so a future server omission can't invert the
         *    gate and silently filter every subsequent update;
         *  - the incoming timestamp is at least as recent as existing.
         */
        fun isStaleReplay(incoming: QueueInfo, existing: QueueInfo?): Boolean {
            if (existing == null || existing.id != incoming.id) return false
            val incomingStamp = incoming.elapsedTimeLastUpdated ?: return false
            val existingStamp = existing.elapsedTimeLastUpdated ?: return false
            return incomingStamp < existingStamp
        }
    }
}
