package io.music_assistant.client.utils

/** Gives a file to the platform share sheet. */
interface FileSharer {
    /** Writes [text] as UTF-8 to [fileName] in the app cache and returns its path. Call it off the main thread. */
    fun writeCacheFile(fileName: String, text: String): String

    /** Opens the share sheet for the file at [path]. Call it on the main thread. */
    fun share(path: String, mimeType: String, chooserTitle: String)
}
