@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")

package io.music_assistant.client.logging

import io.music_assistant.client.player.PlatformContext

expect class LogSharer(platformContext: PlatformContext) {
    fun shareLogs(logText: String)
    fun hasCrashLog(): Boolean
    fun shareCrashLog()
    fun deleteCrashLog()
}
