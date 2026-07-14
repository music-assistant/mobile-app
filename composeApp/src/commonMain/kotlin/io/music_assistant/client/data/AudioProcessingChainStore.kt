package io.music_assistant.client.data

import co.touchlab.kermit.Logger
import io.music_assistant.client.data.model.server.AudioProcessingChain
import kotlinx.atomicfu.atomic
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

internal const val AUDIO_PROCESSING_CHAIN_SCHEMA_VERSION = 38

internal fun supportsAudioProcessingChainSchema(schemaVersion: Int?): Boolean =
    schemaVersion?.let { it >= AUDIO_PROCESSING_CHAIN_SCHEMA_VERSION } == true

/**
 * Queue-keyed replacement snapshots. The server owns the full value, so the
 * client never merges fields from different revisions. Revisions are scoped
 * to one server connection epoch and reset when a new session is established.
 */
internal class AudioProcessingChainStore {
    internal data class FetchToken(
        val queueId: String,
        val connectionGeneration: Long,
        val queueMutationGeneration: Long,
    )

    private data class QueueState(
        val revision: Long = Long.MIN_VALUE,
        val mutationGeneration: Long = 0,
        val snapshot: AudioProcessingChain? = null,
    )

    private data class StoreState(
        val connectionGeneration: Long = 0,
        val queues: Map<String, QueueState> = emptyMap(),
    )

    private val log = Logger.withTag("AudioProcessingChainStore")
    private val _snapshots = MutableStateFlow<Map<String, AudioProcessingChain>>(emptyMap())
    private val state = atomic(StoreState())
    val snapshots: StateFlow<Map<String, AudioProcessingChain>> = _snapshots.asStateFlow()

    /**
     * Apply an event snapshot and invalidate every fetch captured before it,
     * even when the event is a clear or carries an older revision.
     */
    fun applyEvent(queueId: String, snapshot: AudioProcessingChain?) {
        if (queueId.isBlank()) {
            log.w { "Ignoring audio processing update without a queue id" }
            return
        }
        val hasQueueMismatch =
            snapshot?.queueId?.isNotEmpty() == true && snapshot.queueId != queueId
        if (hasQueueMismatch) {
            log.w {
                "Ignoring audio processing event for $queueId with payload queue ${snapshot?.queueId}"
            }
        }

        while (true) {
            val current = state.value
            val previous = current.queues[queueId] ?: QueueState()
            val normalized = snapshot
                ?.takeUnless { hasQueueMismatch }
                ?.let { value ->
                    value.takeIf { it.queueId.isNotEmpty() } ?: value.copy(queueId = queueId)
                }
            val nextQueue = when {
                normalized == null -> previous.copy(
                    mutationGeneration = previous.mutationGeneration + 1,
                    snapshot = if (snapshot == null) null else previous.snapshot,
                )
                normalized.revision > previous.revision -> QueueState(
                    revision = normalized.revision,
                    mutationGeneration = previous.mutationGeneration + 1,
                    snapshot = normalized,
                )
                else -> previous.copy(
                    mutationGeneration = previous.mutationGeneration + 1,
                )
            }
            val next = current.copy(queues = current.queues + (queueId to nextQueue))
            if (state.compareAndSet(current, next)) {
                publish(next)
                return
            }
        }
    }

    fun captureFetch(queueId: String): FetchToken {
        require(queueId.isNotBlank()) { "queueId must not be blank" }
        while (true) {
            val current = state.value
            val previous = current.queues[queueId] ?: QueueState()
            val nextQueue = previous.copy(
                mutationGeneration = previous.mutationGeneration + 1,
            )
            val next = current.copy(queues = current.queues + (queueId to nextQueue))
            if (state.compareAndSet(current, next)) {
                publish(next)
                return FetchToken(
                    queueId = queueId,
                    connectionGeneration = next.connectionGeneration,
                    queueMutationGeneration = nextQueue.mutationGeneration,
                )
            }
        }
    }

    /**
     * Apply an RPC result only if no queue mutation or connection reset occurred
     * since [token] was captured.
     */
    fun applyFetch(token: FetchToken, snapshot: AudioProcessingChain?): Boolean {
        val queueId = token.queueId
        if (snapshot?.queueId?.isNotEmpty() == true && snapshot.queueId != queueId) {
            log.w {
                "Ignoring audio processing RPC for $queueId with payload queue ${snapshot.queueId}"
            }
            return false
        }
        val normalized = snapshot?.let { value ->
            value.takeIf { it.queueId.isNotEmpty() } ?: value.copy(queueId = queueId)
        }

        while (true) {
            val current = state.value
            if (current.connectionGeneration != token.connectionGeneration) return false
            val previous = current.queues[queueId] ?: QueueState()
            if (previous.mutationGeneration != token.queueMutationGeneration) return false

            val nextQueue = when {
                normalized == null -> previous.copy(
                    mutationGeneration = previous.mutationGeneration + 1,
                    snapshot = null,
                )
                normalized.revision > previous.revision -> QueueState(
                    revision = normalized.revision,
                    mutationGeneration = previous.mutationGeneration + 1,
                    snapshot = normalized,
                )
                else -> previous.copy(
                    mutationGeneration = previous.mutationGeneration + 1,
                )
            }
            val next = current.copy(queues = current.queues + (queueId to nextQueue))
            if (state.compareAndSet(current, next)) {
                publish(next)
                return true
            }
        }
    }

    fun clear(queueId: String) {
        if (queueId.isBlank()) {
            log.w { "Ignoring audio processing clear without a queue id" }
            return
        }
        while (true) {
            val current = state.value
            val previous = current.queues[queueId] ?: QueueState()
            val nextQueue = previous.copy(
                mutationGeneration = previous.mutationGeneration + 1,
                snapshot = null,
            )
            val next = current.copy(queues = current.queues + (queueId to nextQueue))
            if (state.compareAndSet(current, next)) {
                publish(next)
                return
            }
        }
    }

    fun clearStoppedQueue(queueId: String, currentQueueItemId: String?) {
        if (currentQueueItemId == null) clear(queueId)
    }

    /** Invalidate in-flight fetches immediately while preserving stale presentation state. */
    fun invalidateConnection() {
        while (true) {
            val current = state.value
            val next = current.copy(connectionGeneration = current.connectionGeneration + 1)
            if (state.compareAndSet(current, next)) {
                publish(next)
                return
            }
        }
    }

    /** Start a new server-session epoch, clearing process-local revisions and snapshots. */
    fun resetConnection() {
        while (true) {
            val current = state.value
            val next = StoreState(connectionGeneration = current.connectionGeneration + 1)
            if (state.compareAndSet(current, next)) {
                publish(next)
                return
            }
        }
    }

    private fun publish(expected: StoreState) {
        _snapshots.update { current ->
            if (state.value !== expected) {
                current
            } else {
                buildMap {
                    expected.queues.forEach { (queueId, queueState) ->
                        queueState.snapshot?.let { put(queueId, it) }
                    }
                }
            }
        }
    }
}

internal fun selectAudioProcessingChain(
    snapshots: Map<String, AudioProcessingChain>,
    queueId: String?,
    currentQueueItemId: String?,
): AudioProcessingChain? {
    val activeQueueId = queueId ?: return null
    val activeItemId = currentQueueItemId ?: return null
    return snapshots[activeQueueId]?.takeIf { it.queueItemId == activeItemId }
}
