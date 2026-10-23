package io.music_assistant.sendspin.api

public sealed interface PlayerState {
    public data object Disabled : PlayerState

    public data class Connecting(val attempt: Int) : PlayerState

    public data class Connected(
        val playerId: String,
        val serverName: String,
        val clock: ClockQuality,
        val audio: AudioStatus,
    ) : PlayerState

    /** Connection lost; retry scheduled. [nextRetryAtMs] is null while waiting for the network. */
    public data class Reconnecting(
        val attempt: Int,
        val nextRetryAtMs: Long?,
        val audio: AudioStatus,
    ) : PlayerState

    /** No retry. Leaves only on a config change. */
    public data class Failed(val cause: FailureCause) : PlayerState
}

public data class AudioStatus(
    val phase: AudioPhase,
    /** Audio queued ahead of the sink, in milliseconds. */
    val bufferedMs: Int,
    /** True while the sink has nothing to play. Carries no judgement about why. */
    val starved: Boolean,
) {
    public companion object {
        public val IDLE: AudioStatus = AudioStatus(AudioPhase.Idle, bufferedMs = 0, starved = false)
    }
}

public enum class AudioPhase { Idle, Buffering, Playing }

public enum class ClockQuality { Good, Degraded, Lost }

public enum class FailureCause {
    Unauthorized,
    Unpaired,
    ServerRejected,

    /** The server explicitly advertised a Sendspin protocol version this client cannot speak. */
    IncompatibleProtocol,

    /**
     * The player could not be built on this device, so it never reached the
     * server. A missing platform primitive or unreadable key storage lands
     * here. The log carries the cause.
     */
    SetupFailed,
}
