package io.music_assistant.client.data

import io.music_assistant.client.data.model.client.SongTiming
import io.music_assistant.client.utils.currentTimeMillis
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.updateAndGet
import kotlinx.coroutines.isActive
import kotlin.math.abs
import kotlin.time.Duration.Companion.seconds

/**
 * Single source of truth for "what elapsed-time is each queue at, right now".
 *
 * Server events (`QueueTimeUpdatedEvent`, `QueueAddedEvent`, ...) write
 * anchors here. Play/pause transitions snapshot the interpolated position so
 * the pause duration doesn't fold into the next forward step. Consumers
 * (in-app slider, MediaSession writes for AA + notification, iOS NowPlaying,
 * audiobook chapter logic) all read the same source — synchronously via
 * [effectiveSec] or as a smoothly-ticking flow via [observe].
 *
 * A radio stream that reports the song on air ([setSong]) is timed by that song
 * instead; the queue's anchors, which count how long the station has played,
 * carry on beneath it and return when the song report goes.
 *
 * No background tick: the cold [observe] flow ticks at 500 ms only while a
 * collector is attached AND the queue is playing. Sync reads are O(1) map
 * lookups — no allocations, no ipc.
 */
class PlayerPositionTracker {
    /** Anchor data for a single queue. */
    data class Anchor(
        val elapsedSec: Double,
        val wallMs: Long,
        val isPlaying: Boolean,
        val durationSec: Double?,
        /**
         * Media-seconds advanced per wall-second (1.0 = normal). Variable speed
         * (audiobooks/podcasts) makes media-time run faster/slower than the wall
         * clock; the server reports elapsed in media-time, so we must scale the
         * interpolated delta to match, or the slider drifts then snaps each anchor.
         */
        val speed: Double = 1.0,
        /** Hold the optimistic position until Sendspin confirms audio is flowing. */
        val freeze: Freeze? = null,
    ) {
        /** Position right now: anchor + speed-scaled wall-time since anchor (capped at duration). */
        fun effectiveNow(): Double {
            if (!isPlaying || freeze != null) return elapsedSec
            val advanced = elapsedSec + (currentTimeMillis() - wallMs) / 1000.0 * speed
            return durationSec?.let { advanced.coerceAtMost(it) } ?: advanced
        }

        /** Snapshots the position at a play/pause transition, also for the server position beneath a freeze. */
        fun withPlaying(isPlaying: Boolean): Anchor {
            val self = if (this.isPlaying == isPlaying) {
                this
            } else {
                copy(elapsedSec = effectiveNow(), wallMs = currentTimeMillis(), isPlaying = isPlaying)
            }
            return freeze?.let { self.copy(freeze = it.copy(server = it.server?.withPlaying(isPlaying))) } ?: self
        }

        /** Keeps [server] beneath a freeze and notes when it reports the frozen position; else [server] wins. */
        fun withServer(server: Anchor): Anchor = freeze
            ?.let {
                val agreed = it.serverAgreed || abs(server.elapsedSec - elapsedSec) <= AGREEMENT_TOLERANCE_SEC
                copy(freeze = it.copy(server = server, serverAgreed = agreed))
            }
            ?: server
    }

    /**
     * Optimistic anchors wait for [confirmPlaying], not server queue echoes: MA can
     * report the seek target or next-track position before Sendspin has audio.
     */
    enum class FreezeReason { SEEK, TRACK_CHANGE }

    /**
     * An optimistic position that waits for audio. [server] is the latest server position beneath
     * it. [serverAgreed] is true once the server reported the frozen position, so the server acted
     * on the command. [id] identifies the freeze for [releaseUnconfirmed].
     */
    data class Freeze(
        val reason: FreezeReason,
        val id: Long,
        val server: Anchor?,
        val serverAgreed: Boolean = false,
    )

    /** A radio stream's report of its song, and the anchor it gives the queue. */
    private data class Song(val timing: SongTiming, val anchor: Anchor)

    private val anchors = MutableStateFlow<Map<String, Anchor>>(emptyMap())
    private val songs = MutableStateFlow<Map<String, Song>>(emptyMap())
    private val lastFreezeId = MutableStateFlow(0L)

    /**
     * Server-anchored update. Pass `isPlaying`/`durationSec` when known;
     * `null` preserves the existing values (useful for `QueueTimeUpdatedEvent`
     * which only carries the new elapsed value).
     */
    fun setAnchor(
        queueId: String,
        elapsedSec: Double,
        isPlaying: Boolean? = null,
        durationSec: Double? = null,
        speed: Double? = null,
    ) {
        anchors.update { existing ->
            val current = existing[queueId]
            val base = current?.freeze?.server ?: current
            val server = Anchor(
                elapsedSec = elapsedSec,
                wallMs = currentTimeMillis(),
                isPlaying = isPlaying ?: base?.isPlaying ?: false,
                durationSec = durationSec ?: base?.durationSec,
                speed = speed ?: base?.speed ?: 1.0,
            )
            // Server anchors are noisy during handoff; Sendspin sync is the confirmation. A frozen
            // anchor keeps the server position beneath it for [releaseUnconfirmed].
            existing + (queueId to (current?.withServer(server) ?: server))
        }
    }

    /**
     * Play/pause transition. Snapshots the current interpolated position as
     * the new anchor so neither the slider nor MediaSession sees a jump:
     * - Pausing: anchor advances to "where we were", isPlaying=false → static.
     * - Resuming: same anchor, wallMs reset to now, isPlaying=true → forward.
     */
    fun setPlaying(queueId: String, isPlaying: Boolean) {
        anchors.update { existing ->
            val current = existing[queueId] ?: return@update existing
            val updated = current.withPlaying(isPlaying)
            if (updated == current) existing else existing + (queueId to updated)
        }
        songs.update { existing ->
            val current = existing[queueId] ?: return@update existing
            val updated = current.anchor.withPlaying(isPlaying)
            if (updated == current.anchor) existing else existing + (queueId to current.copy(anchor = updated))
        }
    }

    /**
     * The song a radio stream reports on air, or null when it reports none. A report anchors the
     * queue at the song's position and length; the same report again keeps the anchor, so a
     * play/pause snapshot holds.
     */
    fun setSong(queueId: String, timing: SongTiming?) {
        songs.update { existing ->
            val current = existing[queueId]
            when {
                timing == null -> existing - queueId
                timing == current?.timing -> existing
                else -> {
                    val now = currentTimeMillis()
                    val anchor = Anchor(
                        elapsedSec = timing.positionAt(now / 1000.0),
                        wallMs = now,
                        isPlaying = current?.anchor?.isPlaying ?: anchors.value[queueId]?.isPlaying ?: false,
                        durationSec = timing.duration,
                    )
                    existing + (queueId to Song(timing, anchor))
                }
            }
        }
    }

    /** User-dropped seek anchor; server echoes do not release the freeze. Returns the freeze id. */
    fun setOptimisticSeek(
        queueId: String,
        elapsedSec: Double,
        durationSec: Double? = null,
        speed: Double? = null,
    ): Long = setFrozen(queueId, elapsedSec, durationSec, speed, FreezeReason.SEEK)

    /** Next/Previous boundary; wait for new-stream sync before ticking again. Returns the freeze id. */
    fun setOptimisticTrackChange(
        queueId: String,
        elapsedSec: Double,
        durationSec: Double? = null,
        speed: Double? = null,
    ): Long = setFrozen(queueId, elapsedSec, durationSec, speed, FreezeReason.TRACK_CHANGE)

    private fun setFrozen(
        queueId: String,
        elapsedSec: Double,
        durationSec: Double?,
        speed: Double?,
        reason: FreezeReason,
    ): Long {
        val id = lastFreezeId.updateAndGet { it + 1 }
        val now = currentTimeMillis()
        anchors.update { existing ->
            val current = existing[queueId]
            existing + (
                queueId to Anchor(
                    elapsedSec = elapsedSec,
                    wallMs = now,
                    isPlaying = current?.isPlaying ?: false,
                    durationSec = durationSec ?: current?.durationSec,
                    speed = speed ?: current?.speed ?: 1.0,
                    freeze = Freeze(
                        reason = reason,
                        id = id,
                        server = current?.let { it.freeze?.server ?: it },
                    ),
                )
            )
        }
        return id
    }

    /**
     * Ends freeze [freezeId] if the server never reported its position: the server ignored the
     * command (a `next` on the last item) or rejected it. The position falls back to the server
     * position beneath the freeze. Call it [SERVER_CONFIRM_WINDOW] after the freeze; a later
     * freeze or an audio confirmation makes it a no-op.
     */
    fun releaseUnconfirmed(queueId: String, freezeId: Long) {
        anchors.update { existing ->
            val current = existing[queueId] ?: return@update existing
            val freeze = current.freeze
                ?.takeIf { it.id == freezeId && !it.serverAgreed }
                ?: return@update existing
            existing + (queueId to (freeze.server ?: current.copy(freeze = null)))
        }
    }

    /** Release an optimistic freeze once Sendspin reports synchronized audio. */
    fun confirmPlaying(queueId: String) {
        anchors.update { existing ->
            val current = existing[queueId] ?: return@update existing
            if (current.freeze == null) return@update existing
            existing + (
                queueId to current.copy(
                    elapsedSec = current.effectiveNow(),
                    wallMs = currentTimeMillis(),
                    isPlaying = true,
                    freeze = null,
                )
            )
        }
    }

    /** Anchor changes only, without interpolation ticks; null also reports removal/reset. */
    internal fun observeAnchor(queueId: String): Flow<Anchor?> =
        combine(anchors, songs) { anchors, songs -> shown(queueId, anchors, songs) }
            .distinctUntilChanged()

    /** O(1) read of latest interpolated position. */
    fun effectiveSec(queueId: String): Double? = shown(queueId, anchors.value, songs.value)?.effectiveNow()

    /** The song's anchor over the queue's, except while an optimistic freeze holds the queue. */
    private fun shown(queueId: String, anchors: Map<String, Anchor>, songs: Map<String, Song>): Anchor? {
        val queue = anchors[queueId]
        return queue?.takeIf { it.freeze != null } ?: songs[queueId]?.anchor ?: queue
    }

    /** True while an optimistic seek or track-change is waiting for confirmation. */
    fun isFrozenUntilConfirmed(queueId: String): Boolean =
        anchors.value[queueId]?.freeze != null

    /**
     * Cold flow of interpolated position. Emits immediately on subscription,
     * then either:
     * - re-emits on every anchor change (track flip, seek, server scrub,
     *   pause/resume), AND
     * - ticks at 500 ms while the queue is playing.
     *
     * Stops ticking when the queue is paused (waits for the next anchor
     * change). Cancels cleanly when the collector unsubscribes.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun observe(queueId: String): Flow<Double> = observeAnchor(queueId)
        .flatMapLatest { anchor ->
            if (anchor == null) {
                flowOf(0.0)
            } else {
                flow {
                    while (currentCoroutineContext().isActive) {
                        emit(anchor.effectiveNow())
                        if (!anchor.isPlaying || anchor.freeze != null) break
                        delay(TICK_MS)
                    }
                }
            }
        }

    /** Drop a queue's anchor (e.g., when the queue is removed). */
    fun remove(queueId: String) {
        anchors.update { it - queueId }
        songs.update { it - queueId }
    }

    /** Clear all anchors (disconnect / reset). */
    fun clear() {
        anchors.update { emptyMap() }
        songs.update { emptyMap() }
    }

    companion object {
        private const val TICK_MS = 500L

        /** A server reports the seek target or the 0:00 of a track change within this time. */
        val SERVER_CONFIRM_WINDOW = 5.seconds

        /** A server position this close to a frozen position confirms it. */
        private const val AGREEMENT_TOLERANCE_SEC = 2.0
    }
}
