package io.music_assistant.client.utils

/**
 * iOS has no user-facing "disable background usage" toggle that stops active audio — background
 * audio rides the `audio` UIBackgroundMode + a live AVAudioSession. Nothing to guard against.
 */
class IosBackgroundUsageGuard : BackgroundUsageGuard {
    override fun isBackgroundRestricted(): Boolean = false
    override fun openBackgroundUsageSettings() = Unit
}
