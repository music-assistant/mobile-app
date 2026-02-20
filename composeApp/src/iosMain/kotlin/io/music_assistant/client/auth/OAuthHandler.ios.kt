package io.music_assistant.client.auth

import platform.Foundation.NSURL
import platform.UIKit.UIApplication

actual class OAuthHandler {
    actual fun openOAuthUrl(url: String) {
        val nsUrl = NSURL.URLWithString(url) ?: return
        UIApplication.sharedApplication.openURL(
            nsUrl,
            options = emptyMap<Any?, Any>(),
            completionHandler = null
        )
    }
}
