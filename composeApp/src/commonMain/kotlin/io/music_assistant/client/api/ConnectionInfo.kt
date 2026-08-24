package io.music_assistant.client.api

data class ConnectionInfo(
    val host: String,
    val port: Int,
    val isTls: Boolean,
    val basePath: String = "",
) {
    /** Defensive: callers normalize on input, but a stale persisted value must not leak. */
    private val path = normalizeBasePath(basePath)

    val webUrl: String = origin(if (isTls) "https" else "http")
    val wsUrl: String = origin(if (isTls) "wss" else "ws")

    private fun origin(scheme: String) = "$scheme://$host:$port$path"

    companion object {
        /** Canonical form: "" or "/seg[/seg]" — leading slash, no trailing slash. */
        fun normalizeBasePath(raw: String): String =
            raw.trim().trim('/').takeIf { it.isNotEmpty() }?.let { "/$it" } ?: ""

        /** Display-only preview over raw, possibly half-typed text fields. Never throws. */
        fun previewWsUrl(host: String, port: String, isTls: Boolean, basePath: String): String =
            "${if (isTls) "wss" else "ws"}://$host:$port${normalizeBasePath(basePath)}"
    }
}
