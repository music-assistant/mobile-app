package io.music_assistant.client.settings

import io.music_assistant.sendspin.api.AudioCodec

/** User-editable local player settings, persisted as one unit by [SettingsRepository]. */
data class SendspinPlayerSettings(
    val name: String = SettingsRepository.DEVICE_NAME,
    val bufferCapacityMb: Int = SettingsRepository.BUFFER_MB_DEFAULT,
    val codecPreference: AudioCodec = SettingsRepository.DEFAULT_CODEC,
    val connectionOverride: SendspinConnectionSettings = SendspinConnectionSettings(),
) {
    /** Projection that ignores the override fields while the override is off: they have no effect then. */
    fun effective(): SendspinPlayerSettings = when {
        connectionOverride.enabled -> this
        else -> copy(connectionOverride = SendspinConnectionSettings())
    }

    companion object {
        val defaults = SendspinPlayerSettings()
    }
}

data class SendspinConnectionSettings(
    val enabled: Boolean = false,
    val tls: Boolean = false,
    val host: String = "",
    val port: Int = SettingsRepository.DEFAULT_SENDSPIN_PORT,
    val path: String = SettingsRepository.DEFAULT_SENDSPIN_PATH,
)
