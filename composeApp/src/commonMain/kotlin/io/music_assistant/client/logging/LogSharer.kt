@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")

package io.music_assistant.client.logging

import io.music_assistant.client.player.PlatformContext

/**
 * Split so the caller can run the heavy [prepareLogShareFile] /
 * [prepareCrashLogShareFile] (sanitize + write) off the main thread, then
 * [presentShareFile] back on it — platform share UI must be presented on main.
 */
expect class LogSharer(platformContext: PlatformContext) {
    fun prepareLogShareFile(logText: String): String
    fun prepareCrashLogShareFile(): String?
    fun presentShareFile(path: String, chooserTitle: String)
    fun hasCrashLog(): Boolean
    fun deleteCrashLog()
}
