@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package io.music_assistant.client.ui.compose.common.items

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import kotlinx.cinterop.useContents
import platform.CoreGraphics.CGRectMake
import platform.Foundation.NSURL
import platform.UIKit.UIActivityViewController
import platform.UIKit.UIApplication
import platform.UIKit.UIWindow
import platform.UIKit.UIWindowScene
import platform.UIKit.popoverPresentationController

@Composable
actual fun rememberShareItem(): (SharePayload) -> Unit = remember {
    {
        payload ->
        (if (payload.isUrl) NSURL.URLWithString(payload.value) else payload.value)?.let { shareValue ->
            val root = UIApplication.sharedApplication.connectedScenes
                .filterIsInstance<UIWindowScene>()
                .flatMap { it.windows.filterIsInstance<UIWindow>() }
                .firstOrNull { it.isKeyWindow() }?.rootViewController
            root?.let {
                var presenter = it
                while (true) {
                    presenter = presenter.presentedViewController ?: break
                }
                val activity = UIActivityViewController(listOf(shareValue), null)
                activity.popoverPresentationController?.let { popover ->
                    popover.sourceView = presenter.view
                    popover.sourceRect = presenter.view.bounds.useContents {
                        CGRectMake(size.width / 2.0, size.height / 2.0, 0.0, 0.0)
                    }
                    popover.permittedArrowDirections = 0u
                }
                presenter.presentViewController(activity, animated = true, completion = null)
            }
        }
    }
}
