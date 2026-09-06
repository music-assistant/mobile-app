package io.music_assistant.sendspin.api

import io.music_assistant.sendspin.wire.AudioCodec

sealed interface PlayerEvent {
    /** The first PCM of a stream reached the sink. Confirms audible playback. */
    data class PlaybackStarted(val playerId: String) : PlayerEvent

    data class PlaybackStopped(val cause: StopCause) : PlayerEvent

    /** The server assigned or changed this player's id; the app should refetch players. */
    data object ServerRefreshNeeded : PlayerEvent

    /** The interruption behind a [StopCause.FocusLost] stop is over; the app may resume. */
    data object FocusRegained : PlayerEvent

    /** The module recovered or degraded on its own; worth a notice, needs no action. */
    data class Warning(val code: WarningCode) : PlayerEvent
}

enum class StopCause {
    /** Buffer ran dry while the connection was down. */
    Starved,
    ServerEnded,
    Cleared,
    FocusLost,
    SinkFailed,
    Disabled,
}

sealed interface WarningCode {
    data object ClockUnstable : WarningCode
    data class DecoderFailed(val codec: AudioCodec) : WarningCode
    data class UnsupportedFormat(val codec: String) : WarningCode
}
