package io.music_assistant.sendspin.api

/**
 * Sent as `device_info` in `client/hello`, so the server can name this client in its
 * UI and logs. Each field is cut to 64 bytes of UTF-8.
 */
public data class DeviceInfo(
    val productName: String,
    val manufacturer: String,
    val softwareVersion: String,
)
