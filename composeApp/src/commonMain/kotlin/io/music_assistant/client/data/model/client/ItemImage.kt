package io.music_assistant.client.data.model.client

import io.ktor.http.URLBuilder
import io.ktor.http.appendPathSegments
import io.ktor.http.encodeURLQueryComponent

data class ItemImage(
    val type: String,
    val path: String,
    val provider: String,
    val isRemotelyAccessible: Boolean,
) {
    fun url(serverUrl: String?): String? =
        path.takeIf { isRemotelyAccessible && it.startsWith("https") }
            ?: serverUrl?.let { server ->
                return URLBuilder(server).apply {
                    appendPathSegments("imageproxy")
                    parameters.apply {
                        append("path", path.encodeURLQueryComponent())
                        append("provider", provider)
                        append("checksum", "")
                    }
                }.buildString()
            }
}
