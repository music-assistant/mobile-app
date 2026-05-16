package io.music_assistant.client.data.model.client

import io.ktor.http.URLBuilder
import io.ktor.http.appendPathSegments
import io.ktor.http.encodeURLQueryComponent

/**
 * Client-side image descriptor.
 *
 * Built by `MediaItemFactory.createImageInfo` from the server `ServerMediaItemImage`
 * DTO; UI binds against this and calls [url] to obtain a concrete image URL
 * (using the imageproxy endpoint when the source isn't directly reachable).
 */
data class ImageInfo(
    val path: String,
    val isRemotelyAccessible: Boolean,
    val provider: String,
) {
    fun url(serverUrl: String?): String? =
        path.takeIf { isRemotelyAccessible && it.startsWith("https") }
            ?: serverUrl?.let { server ->
                URLBuilder(server).apply {
                    appendPathSegments("imageproxy")
                    parameters.apply {
                        append("path", path.encodeURLQueryComponent())
                        append("provider", provider)
                        append("checksum", "")
                    }
                }.buildString()
            }
}
