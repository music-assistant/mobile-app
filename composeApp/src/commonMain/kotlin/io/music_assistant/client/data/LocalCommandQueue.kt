package io.music_assistant.client.data

import co.touchlab.kermit.Logger
import io.music_assistant.client.api.Request
import io.music_assistant.client.ui.compose.common.action.PlayerAction
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.concurrent.Volatile

/**
 * Local-player commands travel over the MA API, not over Sendspin. While the
 * command transport is not ready they are queued (deduplicated per action) and
 * replayed by one worker that wakes when [isReady] turns true or when a
 * command is queued while already ready (a send that failed across a
 * readiness bounce). Sendspin activation is not a trigger: the audio
 * connection may stay up through an MA-only outage.
 *
 * Queueing also calls [requestRecovery]. Every command here comes from a user
 * gesture or an interruption, so the queue asks for the session back instead of
 * waiting for readiness to arrive on its own — a backgrounded session torn down
 * for idleness would otherwise only come back on the next foreground.
 *
 * A replay is sent once; a failure during replay is logged and dropped, so
 * a persistently failing command cannot spin the worker.
 *
 * A command that starts or moves playback goes through [sendOrDrop] instead
 * and is never queued: replayed after an outage, it would move playback at a
 * moment the user no longer expects.
 */
internal class LocalCommandQueue(
    private val isReady: StateFlow<Boolean>,
    private val send: suspend (Request) -> Result<*>,
    scope: CoroutineScope,
    private val requestRecovery: () -> Unit,
) {
    private val log = Logger.withTag("LocalCommandQueue")
    private val mutex = Mutex()
    private val queue = mutableListOf<Entry>()
    private val wake = Channel<Unit>(Channel.CONFLATED)

    /** True from the first queued command until the drain has sent it. */
    private val pending = MutableStateFlow(false)

    /** Bumped by every Pause and Stop: a later one cancels a [sendOrDrop] that still waits. */
    @Volatile
    private var pauses = 0

    private data class Entry(val action: PlayerAction, val request: Request)

    init {
        scope.launch { isReady.filter { it }.collect { wake.trySend(Unit) } }
        scope.launch { wake.consumeEach { if (isReady.value) drain() } }
    }

    /** [userIntent] false marks a pause the app sends on its own: it never drops a waiting [sendOrDrop]. */
    suspend fun sendOrQueue(action: PlayerAction, request: Request, userIntent: Boolean = true) {
        if (action.movesPlayback) {
            sendOrDrop(listOf(request))
            return
        }
        if (action.endsPlayback && userIntent) pauses++
        if (!isReady.value) {
            // Enqueue first: the drain must see the entry when readiness flips.
            enqueue(action, request)
            requestRecovery()
            return
        }
        if (send(request).isFailure) {
            enqueue(action, request)
            wake.trySend(Unit)
        }
    }

    /**
     * Sends [requests] in order, or drops them all. It waits up to [SEND_OR_DROP_WAIT_MS]
     * for the session (and asks for it back) and for the queued commands to go first, so
     * an offline pause still lands before it. Then [beforeSend] runs and the requests are
     * sent; a failure drops the rest. A Pause or Stop that arrives while it waits drops it too.
     * Returns true when every request was sent.
     */
    suspend fun sendOrDrop(requests: List<Request>, beforeSend: () -> Unit = {}): Boolean {
        val pausesBefore = pauses
        if (!isReady.value) requestRecovery()
        val clear = withTimeoutOrNull(SEND_OR_DROP_WAIT_MS) {
            combine(isReady, pending) { ready, queued -> ready && !queued }.first { it }
        } != null
        val command = requests.lastOrNull()?.command
        return when {
            !clear -> false.also { log.i { "Dropped $command: the server is unreachable" } }
            pauses != pausesBefore -> false.also { log.i { "Dropped $command: a later pause replaced it" } }
            else -> {
                beforeSend()
                requests.all { request ->
                    send(request).onFailure { log.w { "Dropped ${request.command}: ${it.message}" } }.isSuccess
                }
            }
        }
    }

    suspend fun clear() = mutex.withLock {
        queue.clear()
        pending.value = false
    }

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
        mutex.withLock { pending.value = queue.isNotEmpty() }
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
                PlayerAction.Pause, PlayerAction.Stop -> {
                    queue.removeAll { it.action.endsPlayback }
                    queue.add(entry)
                }

                is PlayerAction.ToggleShuffle -> toggle { it is PlayerAction.ToggleShuffle }
                is PlayerAction.ToggleRepeatMode -> {
                    queue.removeAll { it.action is PlayerAction.ToggleRepeatMode }
                    queue.add(entry)
                }

                is PlayerAction.ToggleDontStopTheMusic -> toggle { it is PlayerAction.ToggleDontStopTheMusic }
                is PlayerAction.ToggleCrossfade -> toggle { it is PlayerAction.ToggleCrossfade }

                else -> queue.add(entry)
            }
            pending.value = queue.isNotEmpty()
        }
    }

    private companion object {
        const val REPLAY_SPACING_MS = 100L

        /** The request gate's own budget: long enough to bring back a session torn down for idleness. */
        const val SEND_OR_DROP_WAIT_MS = 10_000L
    }
}

/** Pause and Stop: the last one queued wins, and either cancels a waiting [LocalCommandQueue.sendOrDrop]. */
private val PlayerAction.endsPlayback: Boolean
    get() = this == PlayerAction.Pause || this == PlayerAction.Stop

/** Play, Next, Previous and seeks: sent at once or dropped, never queued (see [LocalCommandQueue]). */
internal val PlayerAction.movesPlayback: Boolean
    get() = this == PlayerAction.Play || this == PlayerAction.Next || this == PlayerAction.Previous ||
        this is PlayerAction.SeekTo || this is PlayerAction.SeekBy
