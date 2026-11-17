@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlinx.cinterop.BetaInteropApi::class)

package io.music_assistant.client.utils

import kotlinx.cinterop.useContents
import platform.CoreGraphics.CGRectMake
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

/** Shares temporary files through `UIActivityViewController`. iOS shows no chooser title. */
class IosFileSharer : FileSharer {
    override fun writeCacheFile(fileName: String, text: String): String =
        "${NSTemporaryDirectory()}$fileName".also { path ->
            NSString.create(string = text)
                .writeToFile(path, atomically = true, encoding = NSUTF8StringEncoding, error = null)
        }

    override fun share(path: String, mimeType: String, chooserTitle: String) {
        val activityVC = UIActivityViewController(
            activityItems = listOf(NSURL.fileURLWithPath(path)),
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
