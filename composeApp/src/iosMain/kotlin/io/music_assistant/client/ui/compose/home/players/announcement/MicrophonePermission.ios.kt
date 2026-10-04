package io.music_assistant.client.ui.compose.home.players.announcement

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionRecordPermissionGranted
import platform.Foundation.NSURL
import platform.UIKit.UIApplication
import platform.UIKit.UIApplicationOpenSettingsURLString
import kotlin.coroutines.resume

@Composable
actual fun rememberMicrophonePermission(): MicrophonePermission = remember { IosMicrophonePermission }

private object IosMicrophonePermission : MicrophonePermission {
    override fun isGranted(): Boolean =
        AVAudioSession.sharedInstance().recordPermission == AVAudioSessionRecordPermissionGranted

    // iOS prompts only once; later calls answer with the stored choice at once.
    override suspend fun request(): Boolean = suspendCancellableCoroutine { continuation ->
        AVAudioSession.sharedInstance().requestRecordPermission { granted -> continuation.resume(granted) }
    }

    override fun openSettings() {
        NSURL.URLWithString(UIApplicationOpenSettingsURLString)?.let {
            UIApplication.sharedApplication.openURL(it, options = emptyMap<Any?, Any>(), completionHandler = null)
        }
    }
}
