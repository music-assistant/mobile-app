package io.music_assistant.client.utils

// Flips to true when targeting SDK 37: Android 17 gates local networks behind
// ACCESS_LOCAL_NETWORK, and the probe becomes a runtime permission check.
actual val localNetworkPermissionGateExists: Boolean = false
