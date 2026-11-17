@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")

package io.music_assistant.client.logging

import io.music_assistant.client.player.PlatformContext

/**
 * Prepares the log files. Run [prepareLogShareFile] / [prepareCrashLogShareFile]
 * (sanitize + write) off the main thread, then give the path to `FileSharer.share`.
 */
expect class LogSharer(platformContext: PlatformContext) {
    fun prepareLogShareFile(logText: String): String
    fun prepareCrashLogShareFile(): String?
    fun hasCrashLog(): Boolean
    fun deleteCrashLog()
}
