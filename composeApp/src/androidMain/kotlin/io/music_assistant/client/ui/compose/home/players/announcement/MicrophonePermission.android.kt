package io.music_assistant.client.ui.compose.home.players.announcement

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CompletableDeferred

@Composable
actual fun rememberMicrophonePermission(): MicrophonePermission {
    val context = LocalContext.current
    val answer = remember { PendingAnswer() }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission(), answer::complete)
    return remember(context, launcher) { AndroidMicrophonePermission(context, launcher, answer) }
}

/** The one permission prompt in flight; a new prompt replaces an abandoned one. */
private class PendingAnswer {
    private var deferred: CompletableDeferred<Boolean>? = null

    fun next(): CompletableDeferred<Boolean> = CompletableDeferred<Boolean>().also { deferred = it }

    fun complete(granted: Boolean) {
        deferred?.complete(granted)
        deferred = null
    }
}

private class AndroidMicrophonePermission(
    private val context: Context,
    private val launcher: ActivityResultLauncher<String>,
    private val answer: PendingAnswer,
) : MicrophonePermission {
    override fun isGranted(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED

    override suspend fun request(): Boolean {
        if (isGranted()) return true
        // After a second refusal the system answers false at once, without a prompt.
        val result = answer.next()
        launcher.launch(Manifest.permission.RECORD_AUDIO)
        return result.await()
    }

    override fun openSettings() {
        context.startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}
