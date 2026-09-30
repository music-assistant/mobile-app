@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")

package io.music_assistant.client.player

actual class MediaSessionBridge {
    actual fun setLongFormSeekIntervals(backSeconds: Long, forwardSeconds: Long) {
        PlatformPlayerProvider.player?.setLongFormSeekIntervals(backSeconds, forwardSeconds)
    }
}
