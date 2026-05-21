@file:OptIn(ExperimentalForeignApi::class)

package io.music_assistant.client.imageloader

import coil3.PlatformContext
import kotlinx.cinterop.ExperimentalForeignApi
import okio.Path
import okio.Path.Companion.toPath
import platform.Foundation.NSCachesDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSURL
import platform.Foundation.NSUserDomainMask

internal actual fun imageDiskCacheDir(context: PlatformContext): Path {
    val urls = NSFileManager.defaultManager.URLsForDirectory(NSCachesDirectory, NSUserDomainMask)
    val base = (urls.firstOrNull() as? NSURL)?.path ?: "/tmp"
    return "$base/image_cache".toPath()
}
