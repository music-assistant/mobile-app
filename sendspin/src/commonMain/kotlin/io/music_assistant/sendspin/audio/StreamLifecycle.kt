package io.music_assistant.sendspin.audio

import io.music_assistant.sendspin.wire.StreamStartPlayer

internal enum class StreamPhase { Idle, Playing, Ended }

internal sealed interface StreamAction {
    /** Clear the buffer, reset de-dup, rebuild decoder and sink for [format]. */
    data class StartFresh(val format: StreamStartPlayer) : StreamAction

    /** Same stream continues on a new connection: keep buffer, decoder, and sink. */
    data object ResumeKeepBuffer : StreamAction

    data object End : StreamAction

    /** End without an audio event: the caller reports its own cause (starvation). */
    data object Abort : StreamAction

    data object Clear : StreamAction

    data object Ignore : StreamAction
}

/**
 * Pure stream lifecycle decisions.
 *
 * MA stitches consecutive tracks into one stream, so a `stream/start` while
 * playing is a discontinuity (seek, skip, restart), never a gapless boundary.
 * The one exception is the first `stream/start` of a new connection while the
 * previous connection's stream is still playing with the same format: that is
 * a reconnect resume, and the buffered audio must survive it.
 */
internal object StreamLifecycle {
    fun onStart(
        phase: StreamPhase,
        current: StreamStartPlayer?,
        next: StreamStartPlayer,
        newConnection: Boolean,
    ): StreamAction = when (phase) {
        StreamPhase.Idle, StreamPhase.Ended -> StreamAction.StartFresh(next)
        StreamPhase.Playing ->
            if (newConnection && next == current) StreamAction.ResumeKeepBuffer else StreamAction.StartFresh(next)
    }

    fun onEnd(phase: StreamPhase): StreamAction =
        if (phase == StreamPhase.Playing) StreamAction.End else StreamAction.Ignore

    fun onClear(phase: StreamPhase): StreamAction =
        if (phase == StreamPhase.Idle) StreamAction.Ignore else StreamAction.Clear
}
