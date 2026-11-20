package io.music_assistant.client.ui.compose.home.players.share

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import io.music_assistant.client.data.share.DeviceAudioShare
import io.music_assistant.client.share.AndroidDeviceAudioShare
import io.music_assistant.client.share.ShareNotificationText
import io.music_assistant.client.ui.compose.home.players.announcement.MicrophonePermission
import io.music_assistant.client.ui.compose.home.players.announcement.rememberMicrophonePermission
import kotlinx.coroutines.CompletableDeferred
import musicassistantclient.composeapp.generated.resources.Res
import musicassistantclient.composeapp.generated.resources.device_audio_notification_text
import musicassistantclient.composeapp.generated.resources.device_audio_notification_title
import musicassistantclient.composeapp.generated.resources.device_audio_stop
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

@Composable
actual fun rememberDeviceAudioShareLauncher(): DeviceAudioShareLauncher {
    val context = LocalContext.current
    val share = koinInject<DeviceAudioShare>()
    val microphone = rememberMicrophonePermission()
    val consent = remember { PendingConsent() }
    val contract = ActivityResultContracts.StartActivityForResult()
    val projection = rememberLauncherForActivityResult(contract, consent::complete)
    val notification = ShareNotificationText(
        title = stringResource(Res.string.device_audio_notification_title),
        text = stringResource(Res.string.device_audio_notification_text),
        stopLabel = stringResource(Res.string.device_audio_stop),
    )
    return remember(context, share, microphone, projection, notification) {
        AndroidDeviceAudioShareLauncher(
            context = context,
            share = share as? AndroidDeviceAudioShare,
            microphone = microphone,
            projection = projection,
            consent = consent,
            notification = notification,
        )
    }
}

/** The one consent prompt in flight; a new prompt replaces an abandoned one. */
private class PendingConsent {
    private var deferred: CompletableDeferred<ActivityResult>? = null

    fun next(): CompletableDeferred<ActivityResult> = CompletableDeferred<ActivityResult>().also { deferred = it }

    fun complete(result: ActivityResult) {
        deferred?.complete(result)
        deferred = null
    }
}

/**
 * Playback capture needs the record permission and, for each share, the consent of the user to
 * a media projection. The service starts only with both, so a refusal starts nothing.
 */
private class AndroidDeviceAudioShareLauncher(
    private val context: Context,
    private val share: AndroidDeviceAudioShare?,
    private val microphone: MicrophonePermission,
    private val projection: ActivityResultLauncher<Intent>,
    private val consent: PendingConsent,
    private val notification: ShareNotificationText,
) : DeviceAudioShareLauncher {
    override suspend fun launch(): Boolean {
        val target = share?.takeIf { it.isSupported } ?: return false
        if (!microphone.request()) return false
        val answer = consent.next()
        projection.launch(context.getSystemService(MediaProjectionManager::class.java).createScreenCaptureIntent())
        val result = answer.await()
        val granted = result.data?.takeIf { result.resultCode == Activity.RESULT_OK } ?: return false
        target.start(result.resultCode, granted, notification)
        return true
    }
}
