package io.music_assistant.sendspin.audio

import io.music_assistant.sendspin.wire.StreamStartPlayer

internal enum class StreamPhase { Idle, Playing, Ended }

internal sealed interface StreamAction {
    /** Clear the buffer, reset de-dup, rebuild decoder and sink for [format]. */
    data class StartFresh(val format: StreamStartPlayer) : StreamAction

    /** Same stream continues on a new connection: keep buffer, decoder, and sink. */
    data object ResumeKeepBuffer : StreamAction

    /** Same format after a discontinuity: clear buffer and de-dup, flush the sink, reset the decoder; keep both. */
    data object Restart : StreamAction

    data object End : StreamAction

    /** End without an audio event: the caller reports its own cause (starvation). */
    data object Abort : StreamAction

    data object Clear : StreamAction

    data object Ignore : StreamAction
}

/**
 * Pure stream lifecycle decisions.
 *
 * MA sends `stream/clear` plus a same-format `stream/start` on every track
 * change, seek, and restart: a discontinuity, never a gapless boundary. While
 * playing, the same format keeps the sink and decoder (a fresh device stream
 * per skip costs hundreds of ms and the head of the track); a format change
 * rebuilds. From idle the sink is always rebuilt, because routing goes stale
 * while paused. The first `stream/start` of a new connection while the previous
 * connection's stream is still playing with the same format is a reconnect
 * resume, and the buffered audio must survive it.
 */
internal object StreamLifecycle {
    fun onStart(
        phase: StreamPhase,
        current: StreamStartPlayer?,
        next: StreamStartPlayer,
        newConnection: Boolean,
    ): StreamAction = when {
        phase != StreamPhase.Playing || next != current -> StreamAction.StartFresh(next)
        newConnection -> StreamAction.ResumeKeepBuffer
        else -> StreamAction.Restart
    }

    fun onEnd(phase: StreamPhase): StreamAction =
        if (phase == StreamPhase.Playing) StreamAction.End else StreamAction.Ignore

    fun onClear(phase: StreamPhase): StreamAction =
        if (phase == StreamPhase.Idle) StreamAction.Ignore else StreamAction.Clear
}
