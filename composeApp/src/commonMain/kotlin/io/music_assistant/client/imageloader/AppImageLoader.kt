package io.music_assistant.client.imageloader

import coil3.ImageLoader
import coil3.PlatformContext
import coil3.Uri
import coil3.fetch.Fetcher
import coil3.svg.SvgDecoder

internal fun buildAppImageLoader(
    context: PlatformContext,
    webrtcFetcherFactory: Fetcher.Factory<Uri>? = null,
): ImageLoader =
    ImageLoader.Builder(context)
        .components {
            webrtcFetcherFactory?.let { add(it) }
            add(SvgDecoder.Factory())
        }
        .build()
