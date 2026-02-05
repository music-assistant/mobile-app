package io.music_assistant.client.player.sendspin.audio

import android.media.MediaCodec
import android.media.MediaFormat
import co.touchlab.kermit.Logger
import io.music_assistant.client.player.sendspin.model.AudioCodec
import io.music_assistant.client.player.sendspin.model.AudioFormatSpec
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Android implementation of FLAC audio decoder using MediaCodec.
 *
 * Uses the platform's native FLAC decoder (available on API 26+) to decode
 * FLAC-encoded audio chunks into raw PCM data for AudioTrack playback.
 *
 * Note: MediaCodec FLAC decoder always outputs 16-bit PCM samples. Conversion
 * to 24/32-bit formats is performed but doesn't increase audio quality.
 *
 * Thread safety: Not thread-safe. Caller must ensure sequential access.
 */
actual class FlacDecoder : AudioDecoder {
    private val logger = Logger.withTag("FlacDecoder")

    // MediaCodec instance
    @Volatile
    private var codec: MediaCodec? = null

    // Configuration
    private var channels: Int = 0
    private var sampleRate: Int = 0
    private var bitDepth: Int = 0

    // Timeout for MediaCodec operations (microseconds)
    private val TIMEOUT_US = 10000L // 10ms

    actual override fun configure(config: AudioFormatSpec, codecHeader: String?) {
        logger.i { "Configuring FLAC decoder: ${config.sampleRate}Hz, ${config.channels}ch, ${config.bitDepth}bit" }

        // Validate constraints
        require(config.channels in 1..8) {
            "FLAC supports 1-8 channels, got ${config.channels}"
        }
        require(config.sampleRate in 1..655350) {
            "Invalid sample rate: ${config.sampleRate}"
        }

        // Store configuration
        sampleRate = config.sampleRate
        channels = config.channels
        bitDepth = config.bitDepth

        try {
            // Create MediaCodec for FLAC
            codec = MediaCodec.createDecoderByType(MediaFormat.MIMETYPE_AUDIO_FLAC)

            // Create MediaFormat
            val format = MediaFormat.createAudioFormat(
                MediaFormat.MIMETYPE_AUDIO_FLAC,
                sampleRate,
                channels
            ).apply {
                // Set max input size (conservative estimate for FLAC frames)
                setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 32768)

                // If codec header provided, add as CSD-0 (codec-specific data)
                codecHeader?.let { header ->
                    try {
                        val csd = ByteBuffer.wrap(header.toByteArray(Charsets.ISO_8859_1))
                        setByteBuffer("csd-0", csd)
                        logger.d { "Added codec-specific data: ${header.length} bytes" }
                    } catch (e: Exception) {
                        logger.w(e) { "Failed to set codec header, continuing without it" }
                    }
                }
            }

            // Configure codec (no surface, no crypto, decoder mode)
            codec?.configure(format, null, null, 0)
            codec?.start()

            logger.i { "FLAC decoder initialized successfully" }

        } catch (e: IOException) {
            logger.e(e) { "Failed to create FLAC decoder - codec not available" }
            throw IllegalStateException(
                "FLAC decoder not available on this device. " +
                        "This is unexpected on Android API 26+. " +
                        "Please report this device model.",
                e
            )
        } catch (e: IllegalStateException) {
            logger.e(e) { "Failed to configure FLAC decoder" }
            throw IllegalStateException("FLAC decoder configuration failed", e)
        } catch (e: Exception) {
            logger.e(e) { "Unexpected error during FLAC decoder initialization" }
            throw IllegalStateException("FLAC decoder initialization failed", e)
        }
    }

    actual override fun decode(encodedData: ByteArray): ByteArray {
        val currentCodec = codec
            ?: throw IllegalStateException("Decoder not configured. Call configure() first.")

        if (encodedData.isEmpty()) {
            logger.w { "Received empty encoded data" }
            return ByteArray(0)
        }

        try {
            logger.d { "Decoding FLAC packet: ${encodedData.size} bytes" }

            // 1. Queue input buffer
            val inputIndex = currentCodec.dequeueInputBuffer(TIMEOUT_US)
            if (inputIndex < 0) {
                logger.w { "No input buffer available (timeout)" }
                return ByteArray(0) // Graceful degradation
            }

            val inputBuffer = currentCodec.getInputBuffer(inputIndex)
                ?: throw IllegalStateException("Input buffer is null")

            inputBuffer.clear()
            inputBuffer.put(encodedData)

            currentCodec.queueInputBuffer(
                inputIndex,
                0,                          // offset
                encodedData.size,          // size
                0,                          // presentation time (not used for audio streaming)
                0                           // flags
            )

            // 2. Dequeue output buffer(s)
            val info = MediaCodec.BufferInfo()
            val outputIndex = currentCodec.dequeueOutputBuffer(info, TIMEOUT_US)

            return when {
                outputIndex >= 0 -> {
                    // Got decoded data
                    val outputBuffer = currentCodec.getOutputBuffer(outputIndex)
                        ?: throw IllegalStateException("Output buffer is null")

                    // Extract PCM data
                    outputBuffer.position(info.offset)
                    outputBuffer.limit(info.offset + info.size)

                    val pcmData = ByteArray(info.size)
                    outputBuffer.get(pcmData)

                    // Release output buffer
                    currentCodec.releaseOutputBuffer(outputIndex, false)

                    logger.d { "Decoded ${info.size} PCM bytes (16-bit)" }

                    // Convert to target bit depth if needed
                    convertPcmBitDepth(pcmData)
                }

                outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    // Output format changed (happens on first decode)
                    val format = currentCodec.outputFormat
                    logger.i { "Output format changed: $format" }

                    // Verify format matches expectations
                    val outChannels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                    val outSampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)

                    if (outChannels != channels || outSampleRate != sampleRate) {
                        logger.w { "Format mismatch: expected ${sampleRate}Hz/${channels}ch, got ${outSampleRate}Hz/${outChannels}ch" }
                    }

                    // Return empty, caller will retry with next chunk
                    ByteArray(0)
                }

                outputIndex == MediaCodec.INFO_TRY_AGAIN_LATER -> {
                    // Codec is buffering, no output yet
                    logger.d { "Codec buffering, no output available" }
                    ByteArray(0)
                }

                outputIndex == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED -> {
                    // Deprecated in API 21+, ignore
                    logger.d { "Output buffers changed (ignored)" }
                    ByteArray(0)
                }

                else -> {
                    logger.w { "Unexpected output index: $outputIndex" }
                    ByteArray(0)
                }
            }

        } catch (e: IllegalStateException) {
            logger.e(e) { "MediaCodec error during decode" }
            // Graceful degradation: return silence
            return ByteArray(0)
        } catch (e: Exception) {
            logger.e(e) { "Unexpected error during decode" }
            return ByteArray(0)
        }
    }

    /**
     * Converts decoded PCM samples (16-bit ByteArray) to target bit depth.
     *
     * MediaCodec FLAC decoder always outputs 16-bit samples. For 24-bit and 32-bit
     * output, we shift the 16-bit samples to the upper bits, but this doesn't
     * increase audio quality.
     *
     * @param pcm16bit The decoded PCM samples (16-bit, little-endian)
     * @return ByteArray in little-endian format with target bit depth
     */
    private fun convertPcmBitDepth(pcm16bit: ByteArray): ByteArray {
        // Input: 16-bit PCM (little-endian)
        // Output: 16/24/32-bit PCM based on bitDepth config

        val sampleCount = pcm16bit.size / 2 // 2 bytes per 16-bit sample

        return when (bitDepth) {
            16 -> {
                // Already 16-bit, pass through
                pcm16bit
            }

            24 -> {
                // Convert 16-bit to 24-bit (shift left 8 bits)
                val buffer = ByteBuffer.allocate(sampleCount * 3)
                buffer.order(ByteOrder.LITTLE_ENDIAN)

                val shortBuffer = ByteBuffer.wrap(pcm16bit).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
                repeat(sampleCount) { i ->
                    val sample24 = shortBuffer.get(i).toInt() shl 8
                    buffer.put((sample24 and 0xFF).toByte())
                    buffer.put(((sample24 shr 8) and 0xFF).toByte())
                    buffer.put(((sample24 shr 16) and 0xFF).toByte())
                }
                buffer.array()
            }

            32 -> {
                // Convert 16-bit to 32-bit (shift left 16 bits)
                val buffer = ByteBuffer.allocate(sampleCount * 4)
                buffer.order(ByteOrder.LITTLE_ENDIAN)

                val shortBuffer = ByteBuffer.wrap(pcm16bit).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
                repeat(sampleCount) { i ->
                    val sample32 = shortBuffer.get(i).toInt() shl 16
                    buffer.putInt(sample32)
                }
                buffer.array()
            }

            else -> {
                logger.e { "Unsupported bit depth: $bitDepth, using 16-bit" }
                pcm16bit
            }
        }
    }

    actual override fun reset() {
        logger.i { "Resetting FLAC decoder" }
        try {
            codec?.let { c ->
                // Flush codec buffers (clears input/output queues)
                c.flush()
                logger.d { "Codec flushed successfully" }
            }
        } catch (e: IllegalStateException) {
            logger.e(e) { "Error flushing codec during reset" }
            // If flush fails, try stop/start cycle
            try {
                codec?.stop()
                codec?.start()
                logger.w { "Codec restarted after flush failure" }
            } catch (e2: Exception) {
                logger.e(e2) { "Failed to restart codec" }
            }
        }
    }

    actual override fun release() {
        logger.i { "Releasing FLAC decoder resources" }
        try {
            codec?.let { c ->
                c.stop()
                c.release()
                logger.d { "Codec released successfully" }
            }
        } catch (e: Exception) {
            logger.e(e) { "Error releasing codec" }
        } finally {
            codec = null
        }
    }

    actual override fun getOutputCodec(): AudioCodec = AudioCodec.PCM
}
