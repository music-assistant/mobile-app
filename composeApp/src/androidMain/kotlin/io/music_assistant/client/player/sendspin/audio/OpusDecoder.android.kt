package io.music_assistant.client.player.sendspin.audio

import co.touchlab.kermit.Logger
import io.music_assistant.client.player.sendspin.model.AudioCodec
import io.music_assistant.client.player.sendspin.model.AudioFormatSpec
import io.github.jaredmdobson.concentus.OpusDecoder as ConcentusOpusDecoder
import io.github.jaredmdobson.concentus.OpusException
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Android implementation of Opus audio decoder using the Concentus library.
 *
 * Concentus is a pure Java/Kotlin port of libopus, providing Opus decoding
 * without requiring JNI or native libraries. Always outputs 16-bit PCM.
 */
actual class OpusDecoder : AudioDecoder {
    private val logger = Logger.withTag("OpusDecoder")

    // Concentus decoder instance
    private var decoder: ConcentusOpusDecoder? = null

    // Configuration
    private var channels: Int = 0
    private var sampleRate: Int = 0

    // Decoding buffer (reused to avoid allocations in hot path)
    private var pcmBuffer: ShortArray? = null

    // Lock to prevent concurrent access (decode vs release race condition)
    private val decoderLock = Any()

    actual override fun configure(config: AudioFormatSpec, codecHeader: String?) {
        logger.i { "Configuring Opus decoder: ${config.sampleRate}Hz, ${config.channels}ch, ${config.bitDepth}bit" }

        // Validate Opus-specific constraints
        require(config.channels in 1..2) {
            "Opus only supports 1 or 2 channels, got ${config.channels}"
        }

        // Opus supports specific sample rates: 8k, 12k, 16k, 24k, 48k
        val validSampleRates = setOf(8000, 12000, 16000, 24000, 48000)
        require(config.sampleRate in validSampleRates) {
            "Opus supports sample rates: $validSampleRates, got ${config.sampleRate}"
        }

        // Store configuration
        sampleRate = config.sampleRate
        channels = config.channels

        // Note: Decoder pooling disabled for consistency with FlacDecoder
        // Can be re-enabled if needed (Concentus is pure Java/Kotlin, no state machine issues)

        // Always create new decoder
        try {
            // Create Concentus Opus decoder
            decoder = ConcentusOpusDecoder(sampleRate, channels)

            // Allocate PCM buffer for decoded samples
            // Max Opus frame size is 5760 samples per channel at 48kHz (120ms frame)
            val maxFrameSize = 5760
            pcmBuffer = ShortArray(maxFrameSize * channels)

            logger.i { "Opus decoder initialized successfully" }

        } catch (e: OpusException) {
            logger.e(e) { "Failed to initialize Opus decoder" }
            throw IllegalStateException("Opus decoder initialization failed", e)
        }

        // Codec header handling (simple approach - log for now)
        if (codecHeader != null) {
            logger.d { "Codec header provided (length=${codecHeader.length}), currently ignored (simple MVP)" }
            // Future enhancement: Parse OpusHead header for pre-skip and gain values
            // This may cause a small click at the start of playback due to pre-skip samples
        }
    }

    actual override fun decode(encodedData: ByteArray): ByteArray {
        return synchronized(decoderLock) {
            val currentDecoder = decoder
                ?: throw IllegalStateException("Decoder not configured. Call configure() first.")

            val currentPcmBuffer = pcmBuffer
                ?: throw IllegalStateException("PCM buffer not allocated")

            if (encodedData.isEmpty()) {
                logger.w { "Received empty encoded data" }
                return@synchronized ByteArray(0)
            }

            try {
                logger.d { "Decoding Opus packet: ${encodedData.size} bytes" }

                // Decode Opus packet to PCM samples
                // Returns number of samples per channel
                val samplesDecoded = currentDecoder.decode(
                    encodedData,                           // input: Opus-encoded packet
                    0,                                      // input offset
                    encodedData.size,                       // input length
                    currentPcmBuffer,                       // output: PCM samples (ShortArray)
                    0,                                      // output offset
                    currentPcmBuffer.size / channels,       // frame size (samples per channel)
                    false                                   // decode FEC (forward error correction) - disabled for now
                )

                if (samplesDecoded <= 0) {
                    logger.w { "Decoder returned no samples" }
                    return@synchronized ByteArray(0)
                }

                // Total samples = samplesDecoded * channels (interleaved)
                val totalSamples = samplesDecoded * channels

                logger.d { "Decoded $samplesDecoded samples/channel ($totalSamples total samples)" }

                // Convert ShortArray to 16-bit PCM ByteArray (Concentus only outputs 16-bit)
                val buffer = ByteBuffer.allocate(totalSamples * 2)
                buffer.order(ByteOrder.LITTLE_ENDIAN)
                for (i in 0 until totalSamples) {
                    buffer.putShort(currentPcmBuffer[i])
                }
                buffer.array()

            } catch (e: OpusException) {
                logger.e(e) { "Opus decoding error" }
                // Graceful degradation: return silence instead of crashing playback
                return@synchronized ByteArray(0)
            } catch (e: Exception) {
                logger.e(e) { "Unexpected error during decode" }
                return@synchronized ByteArray(0)
            }
        }
    }

    actual override fun reset() {
        synchronized(decoderLock) {
            logger.i { "Resetting Opus decoder" }
            try {
                // Reset decoder state while keeping the instance
                decoder?.resetState()
            } catch (e: Exception) {
                logger.e(e) { "Error resetting decoder" }
            }
        }
    }

    actual override fun release() {
        synchronized(decoderLock) {
            logger.i { "Releasing Opus decoder resources" }
            // Concentus is pure Java/Kotlin, no native resources to free
            // Simply null out references for garbage collection
            decoder = null
            pcmBuffer = null
        }
    }

    actual override fun getOutputCodec(): AudioCodec = AudioCodec.PCM
    actual override fun getOutputBitDepth(): Int = 16 // Concentus always decodes to 16-bit
}
