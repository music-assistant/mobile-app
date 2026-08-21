package io.music_assistant.client.settings

import com.russhwolf.settings.NSUserDefaultsSettings
import com.russhwolf.settings.Settings
import platform.Foundation.NSUserDefaults

actual fun provideSettings(): Settings =
    NSUserDefaultsSettings(NSUserDefaults.standardUserDefaults)

// iOS has no equivalent of the Android backup rules, so this suite is still
// part of an iCloud or a local device backup. The split keeps the two
// platforms on one code path. To keep tokens out of an iOS backup, move them
// to the Keychain with `kSecAttrAccessibleWhenUnlockedThisDeviceOnly`.
actual fun provideSecretSettings(): Settings =
    NSUserDefaultsSettings(NSUserDefaults(suiteName = "AppSecrets"))
