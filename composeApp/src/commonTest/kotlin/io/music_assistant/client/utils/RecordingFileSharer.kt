package io.music_assistant.client.utils

/** Keeps the written files in memory and records each share. */
class RecordingFileSharer : FileSharer {
    val files = mutableMapOf<String, String>()
    val shared = mutableListOf<Pair<String, String>>()

    override fun writeCacheFile(fileName: String, text: String): String =
        "cache/$fileName".also { files[it] = text }

    override fun share(path: String, mimeType: String, chooserTitle: String) {
        shared += path to mimeType
    }
}
