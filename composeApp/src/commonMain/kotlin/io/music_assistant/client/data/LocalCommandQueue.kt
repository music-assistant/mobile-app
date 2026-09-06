package io.music_assistant.client.data

import co.touchlab.kermit.Logger
import io.music_assistant.client.api.Request
import io.music_assistant.client.ui.compose.common.action.PlayerAction
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Local-player commands travel over the MA API, not over Sendspin. While the
 * command transport is not ready they are queued (deduplicated per action) and
 * replayed by one worker that wakes when [isReady] turns true or when a
 * command is queued while already ready (a send that failed across a
 * readiness bounce). Sendspin activation is not a trigger: the audio
 * connection may stay up through an MA-only outage.
 *
 * A replay is sent once; a failure during replay is logged and dropped, so
 * a persistently failing command cannot spin the worker.
 */
internal class LocalCommandQueue(
    private val isReady: StateFlow<Boolean>,
    private val send: suspend (Request) -> Result<*>,
    scope: CoroutineScope,
) {
    private val log = Logger.withTag("LocalCommandQueue")
    private val mutex = Mutex()
    private val queue = mutableListOf<Entry>()
    private val wake = Channel<Unit>(Channel.CONFLATED)

    private data class Entry(val action: PlayerAction, val request: Request)

    init {
        scope.launch { isReady.filter { it }.collect { wake.trySend(Unit) } }
        scope.launch { wake.consumeEach { if (isReady.value) drain() } }
    }

    suspend fun sendOrQueue(action: PlayerAction, request: Request) {
        if (!isReady.value) {
            enqueue(action, request)
            return
        }
        if (send(request).isFailure) {
            enqueue(action, request)
            wake.trySend(Unit)
        }
    }

    suspend fun clear() = mutex.withLock { queue.clear() }

    private suspend fun drain() {
        val entries = mutex.withLock {
            if (queue.isEmpty()) return
            log.i { "Draining ${queue.size} queued commands" }
            queue.toList().also { queue.clear() }
        }
        entries.forEach { entry ->
            send(entry.request).onFailure { log.w { "Replay of ${entry.request.command} failed: ${it.message}" } }
            delay(REPLAY_SPACING_MS)
        }
    }

    private suspend fun enqueue(action: PlayerAction, request: Request) {
        mutex.withLock {
            val entry = Entry(action, request)
            fun toggle(match: (PlayerAction) -> Boolean) {
                val idx = queue.indexOfFirst { match(it.action) }
                if (idx >= 0) queue.removeAt(idx) else queue.add(entry)
            }
            when (action) {
                PlayerAction.TogglePlayPause -> toggle { it is PlayerAction.TogglePlayPause }
                PlayerAction.Play, PlayerAction.Pause -> {
                    queue.removeAll { it.action is PlayerAction.Play || it.action is PlayerAction.Pause }
                    queue.add(entry)
                }

                is PlayerAction.ToggleShuffle -> toggle { it is PlayerAction.ToggleShuffle }
                is PlayerAction.ToggleRepeatMode -> {
                    queue.removeAll { it.action is PlayerAction.ToggleRepeatMode }
                    queue.add(entry)
                }

                is PlayerAction.ToggleDontStopTheMusic -> toggle { it is PlayerAction.ToggleDontStopTheMusic }
                is PlayerAction.ToggleCrossfade -> toggle { it is PlayerAction.ToggleCrossfade }
                is PlayerAction.SeekTo -> {
                    queue.removeAll { it.action is PlayerAction.SeekTo }
                    queue.add(entry)
                }

                else -> queue.add(entry)
            }
        }
    }

    private companion object {
        const val REPLAY_SPACING_MS = 100L
    }
}
