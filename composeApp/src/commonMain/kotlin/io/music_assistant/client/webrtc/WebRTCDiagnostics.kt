package io.music_assistant.client.webrtc

import co.touchlab.kermit.Logger
import io.ktor.websocket.CloseReason
import kotlin.random.Random
import kotlin.time.TimeSource

/** Upper bound for locally-authored exception detail included in failure logs. */
private const val MAX_SAFE_DETAIL_LENGTH = 120

/** Local-only correlation, never derived from a server, session, device or credential. */
class WebRTCDiagnostics {
    private val attempt = Random.nextLong().toULong().toString(16)
    private val started = TimeSource.Monotonic.markNow()
    private val logger = Logger.withTag("WebRTCLifecycle")

    fun event(message: String) {
        logger.i { "attempt=$attempt elapsedMs=${started.elapsedNow().inWholeMilliseconds} $message" }
    }

    fun failure(stage: String, error: Throwable) {
        // Never pass [error] to the logger: stack traces can embed URLs, SDP or remote payloads.
        val detail = safeErrorDetail(error)?.let { " detail=$it" } ?: ""
        logger.w {
            "attempt=$attempt elapsedMs=${started.elapsedNow().inWholeMilliseconds} " +
                "$stage error=${error::class.simpleName}$detail cause=${error.cause?.let { it::class.simpleName }}"
        }
    }
}

/** [error]/[check]/[require] messages are app-authored; others can embed URLs, SDP or remote payloads. */
internal fun safeErrorDetail(error: Throwable): String? = when (error) {
    is IllegalStateException, is IllegalArgumentException ->
        error.message?.replace('\n', ' ')?.take(MAX_SAFE_DETAIL_LENGTH)
    else -> null
}

/** Unknown remote text is deliberately omitted, not truncated or regex-redacted. */
internal fun safeCloseSummary(reason: CloseReason?): String = reason?.let {
    val text = when (it.message) {
        "" -> "empty"
        "Client disconnect" -> "client_disconnect"
        "Normal closure" -> "normal_closure"
        "Going away" -> "going_away"
        else -> "omitted"
    }
    "code=${it.code} reason=$text"
} ?: "code=unavailable reason=unavailable"

internal fun safeChannelLabel(label: String): String = when (label) {
    "ma-api", "sendspin", "http_proxy" -> label
    else -> "other"
}
