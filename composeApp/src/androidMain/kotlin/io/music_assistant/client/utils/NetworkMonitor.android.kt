@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")

package io.music_assistant.client.utils

import io.music_assistant.client.player.PlatformContext
import io.music_assistant.sendspin.android.connectivityOnline
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow

actual class NetworkMonitor actual constructor(platformContext: PlatformContext) {
    /** A Koin single: the callback stays registered for the life of the process. */
    actual val isAvailable: StateFlow<Boolean> =
        connectivityOnline(platformContext.applicationContext, CoroutineScope(Dispatchers.Unconfined))
}
