@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")

package io.music_assistant.client.player

/** Android handles seek intervals through MediaSession custom actions. */
actual class MediaSessionBridge {
    actual fun setLongFormSeekIntervals(backSeconds: Long, forwardSeconds: Long) = Unit
}
