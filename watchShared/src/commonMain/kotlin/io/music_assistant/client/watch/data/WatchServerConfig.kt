package io.music_assistant.client.watch.data

import io.ktor.http.URLBuilder
import io.ktor.http.encodeURLPathPart
import io.ktor.http.encodeURLQueryComponent

/**
 * Where the watch finds Music Assistant, and how it authenticates.
 *
 * The values are generated from `local.properties` into `WatchBuildConfig` (see the module's
 * `build.gradle.kts`). The token is a long-lived access token for now; the real sign-in flow
 * replaces it later without touching anything else in this module.
 */
data class WatchServerConfig(
    val host: String,
    val port: Int,
    val isTls: Boolean,
    val token: String,
) {
    val webUrl: String = "${if (isTls) "https" else "http"}://$host:$port"
    val wsUrl: String = "${if (isTls) "wss" else "ws"}://$host:$port/ws"

    /** False when `local.properties` carries no token, which the UI reports instead of retrying. */
    val isConfigured: Boolean = host.isNotBlank() && token.isNotBlank()

    /**
     * The URL the watch fetches artwork from.
     *
     * A publicly reachable `https` image is fetched directly. Everything else goes through the
     * server's imageproxy: the opaque `proxy_id` endpoint on schema 31 and newer, the legacy
     * path/provider query otherwise. The imageproxy needs no token.
     */
    internal fun artworkUrl(image: ServerImageDto, schemaVersion: Int?): String? {
        if (image.path.isEmpty()) return null
        if (image.remotelyAccessible && image.path.startsWith("https")) return image.path
        val proxyId = image.proxyId?.takeIf { (schemaVersion ?: 0) >= IMAGEPROXY_OPAQUE_SCHEMA }
        return proxyId?.let { opaqueProxyUrl(it) } ?: legacyProxyUrl(image.path, image.provider)
    }

    /**
     * Rebases a fully-qualified image URL the server pushed with a player's current media.
     * The server embeds its own view of its origin there, which the watch may not reach.
     */
    internal fun rebaseImageUrl(rawUrl: String): String? {
        if (rawUrl.isEmpty()) return null
        if (!rawUrl.contains(IMAGEPROXY_SEGMENT, ignoreCase = true)) {
            return rawUrl.takeUnless { it.startsWith("http://", ignoreCase = true) }
        }
        val tail = rawUrl.substringAfter(IMAGEPROXY_SEGMENT, missingDelimiterValue = "")
        return "$webUrl/$IMAGEPROXY_SEGMENT$tail"
    }

    private fun opaqueProxyUrl(proxyId: String): String =
        URLBuilder("$webUrl/imageproxy/${proxyId.encodeURLPathPart()}").apply {
            parameters.append("size", ARTWORK_SIZE.toString())
            parameters.append("checksum", "")
        }.buildString()

    private fun legacyProxyUrl(path: String, provider: String): String =
        URLBuilder("$webUrl/imageproxy").apply {
            parameters.append("path", path.encodeURLQueryComponent())
            parameters.append("provider", provider)
            parameters.append("checksum", "")
        }.buildString()

    companion object {
        /** First schema that serves artwork under an opaque `proxy_id`. */
        private const val IMAGEPROXY_OPAQUE_SCHEMA = 31
        private const val IMAGEPROXY_SEGMENT = "imageproxy"

        /**
         * Larger than any watch screen, on purpose: one fetch feeds both the full-bleed
         * background and the thumbnail in the queue list, and the server caches by size.
         */
        const val ARTWORK_SIZE: Int = 512

        /** The coordinates generated from `local.properties` at build time. */
        fun fromBuildConfig(): WatchServerConfig = WatchServerConfig(
            host = WatchBuildConfig.HOST,
            port = WatchBuildConfig.PORT,
            isTls = WatchBuildConfig.TLS,
            token = WatchBuildConfig.TOKEN,
        )
    }
}
