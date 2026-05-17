package io.music_assistant.client.data.model.client

/**
 * Client-side image descriptor.
 *
 * Built by `MediaItemFactory.createImageInfo` from the server `ServerMediaItemImage`
 * DTO. The factory resolves [url] eagerly against the current server base URL
 * (direct https path or imageproxy endpoint), so the UI just binds to [url].
 */
data class ImageInfo(
    val type: ImageType,
    val path: String,
    val isRemotelyAccessible: Boolean,
    val provider: String,
    val url: String?,
)
