package io.music_assistant.client.utils

import io.music_assistant.client.player.PlatformContext
import platform.Foundation.NSBundle

actual fun appVersion(platformContext: PlatformContext): AppVersion {
    fun infoString(key: String) = NSBundle.mainBundle.objectForInfoDictionaryKey(key) as? String
    return AppVersion(
        name = infoString("CFBundleShortVersionString").orEmpty(),
        code = infoString("CFBundleVersion").orEmpty(),
    )
}
