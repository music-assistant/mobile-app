package io.music_assistant.client.player.sendspin

import io.music_assistant.client.player.sendspin.audio.Codec
import io.music_assistant.client.player.sendspin.model.AudioFormatSpec
import io.music_assistant.client.player.sendspin.model.ClientHelloPayload
import io.music_assistant.client.player.sendspin.model.DeviceInfo
import io.music_assistant.client.player.sendspin.model.PlayerSupport
import io.music_assistant.client.player.sendspin.model.VersionedRole

object SendspinCapabilities {
    fun buildClientHello(config: SendspinConfig, codecPreference: Codec): ClientHelloPayload {
        return ClientHelloPayload(
            clientId = config.clientId,
            name = config.deviceName,
            deviceInfo = DeviceInfo.current,
            version = 1,
            supportedRoles = listOf(
                VersionedRole.PLAYER_V1,
            ),
            playerV1Support = PlayerSupport(
                supportedFormats = buildSupportedFormats(codecPreference),
                bufferCapacity = SendspinConfig.bufferCapacityFor(codecPreference),
                supportedCommands = listOf(),
            ),
        )
    }

    private fun buildSupportedFormats(codecPreference: Codec): List<AudioFormatSpec> {
        // Build format variations for the selected codec
        // Stereo (2 channels) × 3 bit depths (16, 24, 32) × 5 sample rates = 15 formats
        val sampleRates = listOf(44100, 48000, 88200, 96000, 192000)
        val bitDepths = listOf(16, 24, 32)

        return buildList {
            for (sampleRate in sampleRates) {
                for (bitDepth in bitDepths) {
                    add(
                        AudioFormatSpec(
                            codec = codecPreference.sendspinAudioCodec,
                            channels = 2,
                            sampleRate = sampleRate,
                            bitDepth = bitDepth,
                        ),
                    )
                }
            }
        }
    }
}
