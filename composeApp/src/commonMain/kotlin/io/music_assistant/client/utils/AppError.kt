package io.music_assistant.client.utils

/**
 * Represents application-level errors with proper categorization.
 * This allows for better error handling, logging, and user feedback.
 */
sealed class AppError(
    open val message: String,
    open val cause: Throwable? = null,
) {
    /**
     * Network-related errors (connectivity, timeouts, etc.)
     */
    data class Network(
        override val message: String,
        override val cause: Throwable? = null,
    ) : AppError(message, cause)

    /**
     * Authentication and authorization errors
     */
    data class Auth(
        override val message: String,
        val errorCode: String? = null,
        override val cause: Throwable? = null,
    ) : AppError(message, cause)

    /**
     * Data parsing and serialization errors
     */
    data class Parsing(
        override val message: String,
        override val cause: Throwable? = null,
    ) : AppError(message, cause)

    /**
     * Server-side errors
     */
    data class Server(
        override val message: String,
        val statusCode: Int? = null,
        override val cause: Throwable? = null,
    ) : AppError(message, cause)

    /**
     * Resource not found errors
     */
    data class NotFound(
        override val message: String,
        val resourceType: String? = null,
    ) : AppError(message)

    /**
     * Unknown or uncategorized errors
     */
    data class Unknown(
        override val message: String,
        override val cause: Throwable? = null,
    ) : AppError(message, cause)

    /**
     * Convert to a user-friendly error message
     */
    fun toUserMessage(): String = when (this) {
        is Network -> "Network error: $message"
        is Auth -> "Authentication error: $message"
        is Parsing -> "Data error: $message"
        is Server -> "Server error: $message"
        is NotFound -> "Not found: $message"
        is Unknown -> "Error: $message"
    }

    /**
     * Convert to a detailed error message for logging
     */
    fun toDetailedMessage(): String {
        val builder = StringBuilder()
        builder.append("[${this::class.simpleName}] $message")

        when (this) {
            is Auth -> errorCode?.let { builder.append(" (code: $it)") }
            is Server -> statusCode?.let { builder.append(" (status: $it)") }
            is NotFound -> resourceType?.let { builder.append(" (resource: $it)") }
            else -> {}
        }

        cause?.let { builder.append(" - Caused by: ${it.message}") }

        return builder.toString()
    }
}
