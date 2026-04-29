package io.music_assistant.client.imageloader

import coil3.ImageLoader
import coil3.PlatformContext
import coil3.svg.SvgDecoder

internal fun buildAppImageLoader(context: PlatformContext): ImageLoader =
    ImageLoader.Builder(context)
        .components { add(SvgDecoder.Factory()) }
        .build()
