@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlinx.cinterop.BetaInteropApi::class)

package io.music_assistant.client.logging

import io.music_assistant.client.player.PlatformContext
import kotlinx.cinterop.useContents
import platform.CoreGraphics.CGRectMake
import platform.Foundation.NSFileManager
import platform.Foundation.NSString
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSURL
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.create
import platform.Foundation.writeToFile
import platform.UIKit.UIActivityViewController
import platform.UIKit.UIApplication
import platform.UIKit.UIWindow
import platform.UIKit.UIWindowScene
import platform.UIKit.popoverPresentationController

actual class LogSharer actual constructor(@Suppress("UNUSED_PARAMETER") platformContext: PlatformContext) {
    private fun logFilePath() = "${NSTemporaryDirectory()}ma_client_logs.txt"
    private fun crashLogFilePath() = "${NSTemporaryDirectory()}ma_crash_log.txt"

    actual fun prepareLogShareFile(logText: String): String {
        val path = logFilePath()
        writeTextToFile(LogSanitizer.sanitize(logText), path)
        return path
    }

    actual fun hasCrashLog(): Boolean =
        NSFileManager.defaultManager.fileExistsAtPath(crashLogFilePath())

    actual fun prepareCrashLogShareFile(): String? {
        val path = crashLogFilePath()
        if (!NSFileManager.defaultManager.fileExistsAtPath(path)) return null
        val rawText = NSString.create(
            contentsOfFile = path,
            encoding = NSUTF8StringEncoding,
            error = null,
        )?.toString() ?: return null
        val sanitizedPath = "${NSTemporaryDirectory()}ma_crash_log_share.txt"
        writeTextToFile(LogSanitizer.sanitize(rawText), sanitizedPath)
        return sanitizedPath
    }

    actual fun presentShareFile(path: String, @Suppress("UNUSED_PARAMETER") chooserTitle: String) {
        shareFile(path)
    }

    actual fun deleteCrashLog() {
        NSFileManager.defaultManager.removeItemAtPath(crashLogFilePath(), error = null)
    }

    private fun writeTextToFile(text: String, path: String) {
        val nsString = NSString.create(string = text)
        nsString.writeToFile(path, atomically = true, encoding = NSUTF8StringEncoding, error = null)
    }

    private fun shareFile(path: String) {
        val fileUrl = NSURL.fileURLWithPath(path)
        val activityVC = UIActivityViewController(
            activityItems = listOf(fileUrl),
            applicationActivities = null,
        )
        val rootVC = UIApplication.sharedApplication.connectedScenes
            .filterIsInstance<UIWindowScene>()
            .firstOrNull()
            ?.windows
            ?.filterIsInstance<UIWindow>()
            ?.firstOrNull { it.isKeyWindow() }
            ?.rootViewController
            ?: return
        // On iPad UIActivityViewController is presented as a popover; UIKit throws an
        // NSException in presentationTransitionWillBegin if no anchor is set
        // Anchor it to the center of the root view with no arrow.
        activityVC.popoverPresentationController?.let { popover ->
            val sourceView = rootVC.view
            popover.sourceView = sourceView
            popover.sourceRect = sourceView.bounds.useContents {
                CGRectMake(size.width / 2.0, size.height / 2.0, 0.0, 0.0)
            }
            popover.permittedArrowDirections = 0u // no arrow, centered popover
        }
        rootVC.presentViewController(activityVC, animated = true, completion = null)
    }
}
