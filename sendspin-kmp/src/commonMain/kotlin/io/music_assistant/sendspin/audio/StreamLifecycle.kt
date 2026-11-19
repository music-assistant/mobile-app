package io.music_assistant.sendspin.audio

import io.music_assistant.sendspin.wire.StreamStartPlayer

internal enum class StreamPhase { Idle, Playing, Ended }

internal sealed interface StreamAction {
    /** Clear the buffer, reset de-dup, rebuild decoder and sink for [format]. */
    data class StartFresh(val format: StreamStartPlayer) : StreamAction

    /** Same stream continues on a new connection: keep buffer, decoder, and sink. */
    data object ResumeKeepBuffer : StreamAction

    /**
     * In-place configuration update of the active stream: keep the buffer, the de-dup, the
     * decoder and the sink. Chunks received from now on are in [format].
     */
    data class Continue(val format: StreamStartPlayer) : StreamAction

    data object End : StreamAction

    /** End without an audio event: the caller reports its own cause (starvation). */
    data object Abort : StreamAction

    data object Clear : StreamAction

    /** Drop only the buffered chunks at or after [fromTimestampMicros]; playback continues. */
    data class ClearFrom(val fromTimestampMicros: Long) : StreamAction

    data object Ignore : StreamAction
}

/**
 * Pure stream lifecycle decisions.
 *
 * A `stream/start` while a stream is active is an in-place configuration update,
 * not a discontinuity: the server continues the same timeline, never resends
 * audio, and the chunks already buffered stay in the format they arrived in.
 * Track changes in enqueue mode, and any format change, arrive this way. Seeks
 * and skips arrive as `stream/clear`. From idle or after an end the sink is
 * always rebuilt, because routing goes stale while paused. The first
 * `stream/start` of a new connection while the previous connection's stream is
 * still playing with the same format is a reconnect resume, and the buffered
 * audio must survive it; a new connection with another format starts fresh.
 */
internal object StreamLifecycle {
    fun onStart(
        phase: StreamPhase,
        current: StreamStartPlayer?,
        next: StreamStartPlayer,
        newConnection: Boolean,
    ): StreamAction = when {
        phase != StreamPhase.Playing || (newConnection && next != current) -> StreamAction.StartFresh(next)
        newConnection -> StreamAction.ResumeKeepBuffer
        else -> StreamAction.Continue(next)
    }

    fun onEnd(phase: StreamPhase): StreamAction =
        if (phase == StreamPhase.Playing) StreamAction.End else StreamAction.Ignore

    fun onClear(phase: StreamPhase, fromTimestampMicros: Long?): StreamAction = when {
        phase == StreamPhase.Idle -> StreamAction.Ignore
        fromTimestampMicros != null -> StreamAction.ClearFrom(fromTimestampMicros)
        else -> StreamAction.Clear
    }
}
