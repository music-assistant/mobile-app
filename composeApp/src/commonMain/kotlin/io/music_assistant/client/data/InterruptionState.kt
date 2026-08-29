package io.music_assistant.client.data

internal sealed interface InterruptionState {
    data object Idle : InterruptionState
    data class Paused(val token: Long, val mayResume: Boolean) : InterruptionState

    fun began(token: Long, pauseSent: Boolean): InterruptionState = when (this) {
        Idle -> Paused(token, pauseSent)
        is Paused -> this
    }

    fun ended(resumeAllowed: Boolean, currentToken: Long): EndResult {
        val paused = this as? Paused ?: return EndResult(Idle, false, currentToken)
        val shouldResume = resumeAllowed && paused.mayResume && paused.token == currentToken
        return EndResult(Idle, shouldResume, paused.token)
    }

    data class EndResult(
        val state: InterruptionState,
        val shouldResume: Boolean,
        val token: Long,
    )
}
