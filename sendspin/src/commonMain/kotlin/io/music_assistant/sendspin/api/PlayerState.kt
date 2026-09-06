package io.music_assistant.sendspin.api

sealed interface PlayerState {
    data object Disabled : PlayerState

    data class Connecting(val attempt: Int) : PlayerState

    data class Connected(
        val playerId: String,
        val serverName: String,
        val clock: ClockQuality,
        val audio: AudioStatus,
    ) : PlayerState

    /** Connection lost; retry scheduled. [nextRetryAtMs] is null while waiting for the network. */
    data class Reconnecting(
        val attempt: Int,
        val nextRetryAtMs: Long?,
        val audio: AudioStatus,
    ) : PlayerState

    /** No retry. Leaves only on a config change. */
    data class Failed(val cause: FailureCause) : PlayerState
}

data class AudioStatus(
    val phase: AudioPhase,
    /** Audio queued ahead of the sink, in milliseconds. */
    val bufferedMs: Int,
    /** True while the sink has nothing to play. Carries no judgement about why. */
    val starved: Boolean,
) {
    companion object {
        val IDLE = AudioStatus(AudioPhase.Idle, bufferedMs = 0, starved = false)
    }
}

enum class AudioPhase { Idle, Buffering, Playing }

enum class ClockQuality { Good, Degraded, Lost }

enum class FailureCause { Unauthorized, Unpaired, ServerRejected }
