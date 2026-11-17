package io.music_assistant.client.utils

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File

/** Shares cache files through the app `FileProvider`, which serves the whole cache folder. */
class AndroidFileSharer(private val context: Context) : FileSharer {
    override fun writeCacheFile(fileName: String, text: String): String =
        File(context.cacheDir, fileName).apply { writeText(text) }.absolutePath

    override fun share(path: String, mimeType: String, chooserTitle: String) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", File(path))
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(
            Intent.createChooser(intent, chooserTitle).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            },
        )
    }
}
