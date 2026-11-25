package io.music_assistant.client.watch

import io.music_assistant.client.watch.data.UserDefaultsPreferences
import io.music_assistant.client.watch.data.WatchPreferences
import platform.WatchKit.WKInterfaceDevice

internal actual fun defaultWatchPreferences(): WatchPreferences = UserDefaultsPreferences()

internal actual fun watchDeviceName(): String =
    "${WKInterfaceDevice.currentDevice().name} (watchOS)"
