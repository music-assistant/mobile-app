package io.music_assistant.client.utils

private const val MAX_CAUSE_DEPTH = 8

/**
 * iOS reports a blocked Local Network permission as NSURLErrorDomain -1009 in the Ktor Darwin
 * message — the same code also appears when genuinely offline. Message-based and
 * cause-chain-walking because the Darwin exception type is not visible from common code.
 */
fun Throwable.isLikelyLocalNetworkBlocked(): Boolean {
    var current: Throwable? = this
    var depth = 0
    while (current != null && depth < MAX_CAUSE_DEPTH) {
        val message = current.message
        if (message != null && isLocalNetworkBlockedMessage(message)) return true
        current = current.cause
        depth++
    }
    return false
}

private fun isLocalNetworkBlockedMessage(message: String): Boolean {
    // NSURLErrorNotConnectedToInternet; Code=-1009 and Code=1009 both appear in the wild.
    if (message.contains("NSURLErrorDomain") &&
        (message.contains("Code=1009") || message.contains("Code=-1009"))
    ) {
        return true
    }
    // Description-only fallback for wrappers that strip the domain/code pair.
    return message.contains("The internet connection appears to be offline")
}
