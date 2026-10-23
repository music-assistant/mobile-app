package io.music_assistant.client.settings

import io.music_assistant.client.utils.supportsHiResAudio
import io.music_assistant.sendspin.api.AudioCodec

/** User-editable local player settings, persisted as one unit by [SettingsRepository]. */
data class SendspinPlayerSettings(
    val name: String = SettingsRepository.DEVICE_NAME,
    val bufferSeconds: Int = SettingsRepository.BUFFER_SECONDS_DEFAULT,
    val codecPreference: AudioCodec = SettingsRepository.DEFAULT_CODEC,
    /** The stored choice, 16 or 24; [effectiveBitDepth] is what the player offers. */
    val bitDepth: Int = SettingsRepository.BIT_DEPTH_DEFAULT,
    val connectionOverride: SendspinConnectionSettings = SendspinConnectionSettings(),
) {
    /** Whether [bitDepth] can apply: a lossless codec on a device that plays 24-bit. */
    fun hiResAvailable(hiResSupported: Boolean = supportsHiResAudio): Boolean =
        hiResSupported && codecPreference != AudioCodec.OPUS

    /** 24 only while [hiResAvailable]; the stored choice comes back with a lossless codec. */
    fun effectiveBitDepth(hiResSupported: Boolean = supportsHiResAudio): Int =
        bitDepth.takeIf { hiResAvailable(hiResSupported) } ?: SettingsRepository.BIT_DEPTH_DEFAULT

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
