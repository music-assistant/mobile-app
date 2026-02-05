package io.music_assistant.client.player.sendspin

/**
 * Categorized error types for Sendspin integration.
 * Helps UI distinguish between recoverable, permanent, and degraded states.
 */
sealed class SendspinError {
    /**
     * Transient error that may be automatically recovered.
     * Examples: Network interruption, brief server unavailability, reconnection in progress.
     *
     * @param cause The underlying error
     * @param willRetry Whether automatic retry is in progress
     */
    data class Transient(
        val cause: Throwable,
        val willRetry: Boolean
    ) : SendspinError()

    /**
     * Permanent error requiring user intervention.
     * Examples: Invalid configuration, authentication failure, unsupported codec.
     *
     * @param cause The underlying error
     * @param userAction Suggested action for user (e.g., "Check network settings", "Update app")
     */
    data class Permanent(
        val cause: Throwable,
        val userAction: String
    ) : SendspinError()

    /**
     * Degraded operation - functionality is limited but not completely broken.
     * Examples: High latency, frequent packet drops, audio quality reduced.
     *
     * @param reason Human-readable description of degradation
     * @param impact User-visible impact (e.g., "High latency", "Frequent audio glitches")
     */
    data class Degraded(
        val reason: String,
        val impact: String
    ) : SendspinError()
}
