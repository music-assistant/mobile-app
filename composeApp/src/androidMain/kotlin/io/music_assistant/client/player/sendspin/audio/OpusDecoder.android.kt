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
 * without requiring JNI or native libraries.
 *
 * Note: Opus always decodes to 16-bit PCM samples. Conversion to 24/32-bit
 * formats is performed but doesn't increase audio quality.
 */
actual class OpusDecoder : AudioDecoder {
    private val logger = Logger.withTag("OpusDecoder")

    // Concentus decoder instance
    private var decoder: ConcentusOpusDecoder? = null

    // Configuration
    private var channels: Int = 0
    private var sampleRate: Int = 0
    private var bitDepth: Int = 0

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
        bitDepth = config.bitDepth

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

                // Convert ShortArray (16-bit PCM) to ByteArray based on target bit depth
                convertShortArrayToByteArray(currentPcmBuffer, totalSamples)

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

    /**
     * Converts decoded PCM samples (ShortArray) to ByteArray with specified bit depth.
     *
     * Opus always decodes to 16-bit samples. For 24-bit and 32-bit output, we shift
     * the 16-bit samples to the upper bits, but this doesn't increase audio quality.
     *
     * @param samples The decoded PCM samples (16-bit shorts)
     * @param sampleCount Number of samples to convert
     * @return ByteArray in little-endian format with target bit depth
     */
    private fun convertShortArrayToByteArray(samples: ShortArray, sampleCount: Int): ByteArray {
        return when (bitDepth) {
            16 -> {
                // 16-bit: 2 bytes per sample (little-endian)
                val buffer = ByteBuffer.allocate(sampleCount * 2)
                buffer.order(ByteOrder.LITTLE_ENDIAN)
                for (i in 0 until sampleCount) {
                    buffer.putShort(samples[i])
                }
                buffer.array()
            }

            24 -> {
                // 24-bit: 3 bytes per sample (little-endian)
                // Convert 16-bit to 24-bit by shifting left 8 bits
                val buffer = ByteBuffer.allocate(sampleCount * 3)
                buffer.order(ByteOrder.LITTLE_ENDIAN)
                for (i in 0 until sampleCount) {
                    val sample24 = samples[i].toInt() shl 8  // Shift to upper 16 bits of 24-bit
                    buffer.put((sample24 and 0xFF).toByte())
                    buffer.put(((sample24 shr 8) and 0xFF).toByte())
                    buffer.put(((sample24 shr 16) and 0xFF).toByte())
                }
                buffer.array()
            }

            32 -> {
                // 32-bit: 4 bytes per sample (little-endian)
                // Convert 16-bit to 32-bit by shifting left 16 bits
                val buffer = ByteBuffer.allocate(sampleCount * 4)
                buffer.order(ByteOrder.LITTLE_ENDIAN)
                for (i in 0 until sampleCount) {
                    val sample32 = samples[i].toInt() shl 16  // Shift to upper 16 bits of 32-bit
                    buffer.putInt(sample32)
                }
                buffer.array()
            }

            else -> {
                logger.e { "Unsupported bit depth: $bitDepth, defaulting to 16-bit" }
                // Fallback to 16-bit
                val buffer = ByteBuffer.allocate(sampleCount * 2)
                buffer.order(ByteOrder.LITTLE_ENDIAN)
                for (i in 0 until sampleCount) {
                    buffer.putShort(samples[i])
                }
                buffer.array()
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
}
