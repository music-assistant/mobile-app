package io.music_assistant.client.di

import io.music_assistant.client.imageloader.ArtworkToken
import platform.Foundation.NSData

/** Native-facing artwork bytes with MIME and versioned invalidation identity. */
data class NativeArtworkResult(
    val data: NSData,
    val mimeType: String?,
    val token: ArtworkToken,
)
