package io.music_assistant.client.ui.compose.common

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import co.touchlab.kermit.Logger
import io.music_assistant.client.data.model.client.MAX_M3U_FILE_BYTES
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream

// The picker filters by MIME type only, and providers report an M3U file with different types.
// A provider that does not know the extension reports text or binary, so these stay too.
private val playlistMimeTypes = arrayOf(
    "audio/x-mpegurl",
    "audio/mpegurl",
    "application/x-mpegurl",
    "application/vnd.apple.mpegurl",
    "text/plain",
    "application/octet-stream",
)

@Composable
actual fun rememberPlaylistFilePicker(onResult: (PickedPlaylistFile) -> Unit): () -> Unit {
    val resolver = LocalContext.current.contentResolver
    val scope = rememberCoroutineScope()
    val currentOnResult by rememberUpdatedState(onResult)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch { currentOnResult(withContext(Dispatchers.IO) { resolver.readPlaylistFile(uri) }) }
    }
    return { launcher.launch(playlistMimeTypes) }
}

private fun ContentResolver.readPlaylistFile(uri: Uri): PickedPlaylistFile = try {
    val bytes = openInputStream(uri)?.use { it.readAtMost(MAX_M3U_FILE_BYTES) }
    when {
        bytes == null -> PickedPlaylistFile.Unreadable
        bytes.size > MAX_M3U_FILE_BYTES -> PickedPlaylistFile.TooLarge
        else -> PickedPlaylistFile.Read(displayName(uri) ?: uri.lastPathSegment.orEmpty(), bytes.decodeToString())
    }
} catch (e: IOException) {
    Logger.withTag("PlaylistFilePicker").w(e) { "Cannot read $uri" }
    PickedPlaylistFile.Unreadable
} catch (e: SecurityException) {
    Logger.withTag("PlaylistFilePicker").w(e) { "No access to $uri" }
    PickedPlaylistFile.Unreadable
}

/** Stops after the first buffer past [limit], so a result larger than [limit] means a larger file. */
private fun InputStream.readAtMost(limit: Int): ByteArray {
    val out = ByteArrayOutputStream()
    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
    while (out.size() <= limit) {
        val count = read(buffer).takeIf { it >= 0 } ?: break
        out.write(buffer, 0, count)
    }
    return out.toByteArray()
}

private fun ContentResolver.displayName(uri: Uri): String? =
    query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
        cursor.takeIf { it.moveToFirst() }?.getString(0)
    }
