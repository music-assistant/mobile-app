package io.music_assistant.client.ui.compose.common

import androidx.compose.runtime.Composable

/** The result of [rememberPlaylistFilePicker]. A cancelled picker gives none. */
sealed interface PickedPlaylistFile {
    /** The file as UTF-8 text. */
    data class Read(val name: String, val text: String) : PickedPlaylistFile

    /** The file is larger than `MAX_M3U_FILE_BYTES`. */
    data object TooLarge : PickedPlaylistFile

    /** The platform could not read the file. */
    data object Unreadable : PickedPlaylistFile
}

/**
 * Remembers a launcher for the system file picker, filtered to M3U files. [onResult] gets the
 * picked file on the main thread. Android filters by MIME type only, so its filter also lets
 * text and binary files through. The caller checks the content.
 */
@Composable
expect fun rememberPlaylistFilePicker(onResult: (PickedPlaylistFile) -> Unit): () -> Unit
