package io.music_assistant.client.utils

/**
 * Platform bridge for the OS-level "background usage" restriction that kills local (Sendspin)
 * playback shortly after the app is backgrounded.
 *
 * Android-only concern: when the user (or the ROM) disables "Allow background usage", the OS tears
 * down the media foreground service regardless of our WifiLock / hasActivePlayback keep-alive. iOS
 * has no equivalent toggle for active audio, so the iOS implementation is a no-op.
 */
interface BackgroundUsageGuard {
    /** True only on Android when `ActivityManager.isBackgroundRestricted()`. Always false on iOS. */
    fun isBackgroundRestricted(): Boolean

    /** Opens the battery/background-usage settings screen; falls back to app details. No-op on iOS. */
    fun openBackgroundUsageSettings()
}
