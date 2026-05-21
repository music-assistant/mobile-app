package io.music_assistant.client.imageloader

import coil3.PlatformContext
import okio.Path
import okio.Path.Companion.toOkioPath

internal actual fun imageDiskCacheDir(context: PlatformContext): Path =
    context.cacheDir.resolve("image_cache").toOkioPath()
