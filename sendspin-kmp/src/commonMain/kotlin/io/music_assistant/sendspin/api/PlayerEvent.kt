package io.music_assistant.sendspin.api

public sealed interface PlayerEvent {
    /** The first PCM of a stream reached the sink. Confirms audible playback. */
    public data class PlaybackStarted(val playerId: String) : PlayerEvent

    public data class PlaybackStopped(val cause: StopCause) : PlayerEvent

    /** The server assigned or changed this player's id; the app should refetch players. */
    public data object ServerRefreshNeeded : PlayerEvent

    /** The interruption behind a [StopCause.FocusLost] stop is over; the app may resume. */
    public data object FocusRegained : PlayerEvent

    /**
     * The server started a stream while the output is held: the server still
     * plays, because the app's pause has not reached it. The app should pause
     * [playerId] again, so the server and the hold agree.
     */
    public data class StreamStartedWhileHeld(val playerId: String) : PlayerEvent

    /** The module recovered or degraded on its own; worth a notice, needs no action. */
    public data class Warning(val code: WarningCode) : PlayerEvent
}

public enum class StopCause {
    /** Buffer ran dry while the connection was down. */
    Starved,
    ServerEnded,
    Cleared,
    FocusLost,
    SinkFailed,
    Disabled,
}

public sealed interface WarningCode {
    public data object ClockUnstable : WarningCode
    public data class DecoderFailed(val codec: AudioCodec) : WarningCode
    public data class UnsupportedFormat(val codec: String) : WarningCode
}
