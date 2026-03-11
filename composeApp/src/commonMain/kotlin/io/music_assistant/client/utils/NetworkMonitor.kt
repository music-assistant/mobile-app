@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")

package io.music_assistant.client.utils

import io.music_assistant.client.player.PlatformContext
import kotlinx.coroutines.flow.StateFlow

expect class NetworkMonitor(platformContext: PlatformContext) {
    val isAvailable: StateFlow<Boolean>
}
