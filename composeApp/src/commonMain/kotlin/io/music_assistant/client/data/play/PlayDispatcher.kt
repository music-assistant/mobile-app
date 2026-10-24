package io.music_assistant.client.data.play

import co.touchlab.kermit.Logger
import io.music_assistant.client.api.Request
import io.music_assistant.client.api.ServiceClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.updateAndGet
import kotlinx.coroutines.launch

/**
 * The single sender of `player_queues/play_media`, for every surface (screens, Android Auto,
 * CarPlay).
 *
 * Requests run in the app [scope], not the caller's: the server answers only after it has
 * resolved the media and started playback, which can take tens of seconds, and leaving the
 * screen must not abandon the request.
 *
 * Each queue has one lane: one request on the server and a waiting list that [coalesce]
 * keeps short. The server serializes play actions per queue and never cancels a running
 * one, so sending a burst of taps would only make it play each of them in turn. Lanes of
 * different queues are independent.
 */
class PlayDispatcher(
    private val apiClient: ServiceClient,
    private val scope: CoroutineScope,
) {
    private val log = Logger.withTag("PlayDispatch")

    private data class Lane(
        val inProgress: PlayRequest? = null,
        val waiting: List<PlayRequest> = emptyList(),
    )

    /** A queue has a lane exactly while its worker runs; both change only inside `update`. */
    private val lanes = MutableStateFlow<Map<String, Lane>>(emptyMap())

    /** Queues with a request on the server or waiting for it. */
    val busyQueueIds: StateFlow<Set<String>> = lanes
        .map { it.keys }
        .distinctUntilChanged()
        .stateIn(scope, SharingStarted.Eagerly, emptySet())

    private val _accepted = MutableSharedFlow<String>(
        extraBufferCapacity = 16,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    /** The queue of every request [play] accepts, once per request, coalesced or not. */
    val accepted: SharedFlow<String> = _accepted

    /** Enqueues synchronously, so taps keep their order whatever thread the worker runs on. */
    fun play(request: PlayRequest) {
        val queueId = request.queueOrPlayerId
        _accepted.tryEmit(queueId)
        var startWorker = false
        val lane = lanes.updateAndGet { current ->
            val lane = current[queueId]
            startWorker = lane == null
            val base = lane ?: Lane()
            current + (queueId to base.copy(waiting = coalesce(base.waiting, base.inProgress, request)))
        }.getValue(queueId)
        log.i { "$queueId: got $request; in progress=${lane.inProgress != null} waiting=${lane.waiting.size}" }
        if (startWorker) scope.launch { drain(queueId) }
    }

    private suspend fun drain(queueId: String) {
        try {
            while (true) {
                val next = takeNext(queueId) ?: return
                apiClient.sendRequest(next.toRequest())
                    .onFailure { log.w(it) { "play_media failed for $next: ${it.message}" } }
            }
        } catch (e: Throwable) {
            // Only app-scope cancellation or a broken no-throw contract of sendRequest gets here.
            // A lane that outlived its worker would make the queue look busy and swallow every tap.
            lanes.update { it - queueId }
            throw e
        }
    }

    /** Moves the first waiting request to "in progress", or closes the lane if none waits. */
    private fun takeNext(queueId: String): PlayRequest? {
        var next: PlayRequest? = null
        lanes.update { current ->
            val waiting = current[queueId]?.waiting.orEmpty()
            next = waiting.firstOrNull()
            next?.let { current + (queueId to Lane(inProgress = it, waiting = waiting.drop(1))) }
                ?: (current - queueId)
        }
        return next
    }

    private fun PlayRequest.toRequest() = Request.Library.play(
        media = media,
        queueOrPlayerId = queueOrPlayerId,
        option = option,
        endlessMixMode = endlessMixMode,
        startItem = startItem,
        shuffle = shuffle,
        startFromBeginning = startFromBeginning,
    )
}
