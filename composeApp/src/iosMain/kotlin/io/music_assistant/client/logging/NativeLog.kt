package io.music_assistant.client.logging

import co.touchlab.kermit.Logger

/**
 * Entry point for Swift to emit logs through Kermit, so iOS-native events reach
 * the same writers as Kotlin logs.
 *
 * Severity is explicit per call so the Swift side keeps per-packet detail at
 * `debug` (below the share buffer's Info floor) and state/error events at `info`+.
 */
object NativeLog {
    fun debug(tag: String, message: String) = Logger.withTag(tag).d(message)
    fun info(tag: String, message: String) = Logger.withTag(tag).i(message)
    fun warn(tag: String, message: String) = Logger.withTag(tag).w(message)
    fun error(tag: String, message: String) = Logger.withTag(tag).e(message)
}
