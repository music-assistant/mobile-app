package io.music_assistant.client.data.model.client

/** The MIME type of an exported playlist file. */
const val M3U_MIME_TYPE = "audio/x-mpegurl"

/**
 * The largest playlist file that the app imports. The server reads a WebSocket message of
 * 4 MiB at most, and JSON escaping makes the text longer in the request.
 */
const val MAX_M3U_FILE_BYTES = 3 * 1024 * 1024

private val unsafeFileNameChars = Regex("""[\\/:*?"<>|]""")
private val playlistTag = Regex("""^#PLAYLIST:(.+)$""", RegexOption.MULTILINE)
private val m3uExtension = Regex("""\.m3u8?$""", RegexOption.IGNORE_CASE)

/** True if [text] is an extended M3U playlist. The server rejects other files. */
fun isM3uPlaylist(text: String): Boolean = text.trimStart().startsWith("#EXTM3U")

/** The name that the import sheet shows: the `#PLAYLIST` tag, else the file name without its extension. */
fun m3uPlaylistName(text: String, fileName: String): String =
    playlistTag.find(text)?.groupValues?.get(1)?.trim()?.takeIf { it.isNotEmpty() }
        ?: fileName.replace(m3uExtension, "")

/** The file name of an exported playlist, safe on every file system, like the web frontend. */
fun m3uExportFileName(playlistName: String): String =
    "${playlistName.replace(unsafeFileNameChars, "_").trim().ifEmpty { "playlist" }}.m3u8"
