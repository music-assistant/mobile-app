@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlinx.cinterop.BetaInteropApi::class)

package io.music_assistant.client.ui.compose.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.uikit.LocalUIViewController
import io.music_assistant.client.data.model.client.MAX_M3U_FILE_BYTES
import platform.Foundation.NSData
import platform.Foundation.NSFileManager
import platform.Foundation.NSFileSize
import platform.Foundation.NSNumber
import platform.Foundation.NSString
import platform.Foundation.NSURL
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.create
import platform.Foundation.dataWithContentsOfURL
import platform.UIKit.UIDocumentPickerDelegateProtocol
import platform.UIKit.UIDocumentPickerViewController
import platform.UniformTypeIdentifiers.UTType
import platform.UniformTypeIdentifiers.UTTypeM3UPlaylist
import platform.darwin.NSObject

@Composable
actual fun rememberPlaylistFilePicker(onResult: (PickedPlaylistFile) -> Unit): () -> Unit {
    val viewController = LocalUIViewController.current
    val currentOnResult by rememberUpdatedState(onResult)
    // UIKit holds the delegate weakly.
    val delegate = remember { PlaylistPickerDelegate { currentOnResult(it) } }
    return {
        val picker = UIDocumentPickerViewController(forOpeningContentTypes = playlistTypes, asCopy = true)
            .apply { this.delegate = delegate }
        viewController.presentViewController(picker, animated = true, completion = null)
    }
}

// The type of a file follows its extension. An extension that no app declares gets a dynamic
// type, which matches the files with that extension too.
private val playlistTypes = listOfNotNull(
    UTTypeM3UPlaylist,
    UTType.typeWithFilenameExtension("m3u"),
    UTType.typeWithFilenameExtension("m3u8"),
).distinct()

private class PlaylistPickerDelegate(
    private val onResult: (PickedPlaylistFile) -> Unit,
) : NSObject(), UIDocumentPickerDelegateProtocol {
    override fun documentPicker(controller: UIDocumentPickerViewController, didPickDocumentsAtURLs: List<*>) {
        (didPickDocumentsAtURLs.firstOrNull() as? NSURL)?.let { onResult(read(it)) }
    }

    private fun read(url: NSURL): PickedPlaylistFile {
        val size = url.path
            ?.let { NSFileManager.defaultManager.attributesOfItemAtPath(it, error = null) }
            ?.get(NSFileSize) as? NSNumber
        return when {
            size == null -> PickedPlaylistFile.Unreadable
            size.longLongValue > MAX_M3U_FILE_BYTES -> PickedPlaylistFile.TooLarge
            else -> NSData.dataWithContentsOfURL(url)
                ?.let { NSString.create(data = it, encoding = NSUTF8StringEncoding)?.toString() }
                ?.let { PickedPlaylistFile.Read(url.lastPathComponent.orEmpty(), it) }
                ?: PickedPlaylistFile.Unreadable
        }
    }
}
