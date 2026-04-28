// PCM bit-depth literals (16/24/32) and FLAC frame size hints are audio-format standards.
// Timing/retry values live as named constants in the private companion object below.
@file:Suppress("MagicNumber")

package io.music_assistant.client.player.sendspin.audio

import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaFormat
import android.os.Build
import co.touchlab.kermit.Logger
import io.music_assistant.client.player.sendspin.model.AudioCodec
import io.music_assistant.client.player.sendspin.model.AudioFormatSpec
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * Android implementation of FLAC audio decoder using MediaCodec.
 *
 * Uses the platform's native FLAC decoder (available on API 26+) to decode
 * FLAC-encoded audio chunks into raw PCM data for AudioTrack playback.
 *
 * Requests the source bit depth via KEY_PCM_ENCODING so MediaCodec outputs
 * native-depth PCM (16/24/32-bit) without manual conversion. The actual
 * output encoding is read from INFO_OUTPUT_FORMAT_CHANGED and exposed via
 * [getOutputBitDepth] so AudioTrack can be configured to match.
 *
 * Thread safety: All public methods are synchronized via decoderLock to prevent
 * concurrent access between decode() and release()/reset() from different coroutines.
 */
@OptIn(ExperimentalEncodingApi::class)
actual class FlacDecoder : AudioDecoder {
    private val logger = Logger.withTag("FlacDecoder")

    // Lock to prevent concurrent access (decode vs release/reset race condition)
    private val decoderLock = Any()

    // MediaCodec instance - access must be synchronized via decoderLock
    private var codec: MediaCodec? = null

    // Actual bit depth MediaCodec outputs — determined after INFO_OUTPUT_FORMAT_CHANGED
    private var outputBitDepth: Int = 16

    actual override fun configure(config: AudioFormatSpec, codecHeader: String?) {
        synchronized(decoderLock) {
            logger.i { "Configuring FLAC decoder: ${config.sampleRate}Hz, ${config.channels}ch, ${config.bitDepth}bit" }

            // Validate constraints
            require(config.channels in 1..8) {
                "FLAC supports 1-8 channels, got ${config.channels}"
            }
            require(config.sampleRate in 1..655350) {
                "Invalid sample rate: ${config.sampleRate}"
            }

            // Release any existing codec before reconfiguring
            codec?.let { existing ->
                try {
                    existing.stop()
                    existing.release()
                    logger.d { "Released existing codec before reconfigure" }
                } catch (e: Exception) {
                    logger.w(e) { "Error releasing existing codec during reconfigure" }
                }
                codec = null
            }

            // Default to requested bit depth; updated when INFO_OUTPUT_FORMAT_CHANGED fires
            outputBitDepth = config.bitDepth

            try {
                // Create MediaCodec for FLAC
                val newCodec = MediaCodec.createDecoderByType(MediaFormat.MIMETYPE_AUDIO_FLAC)

                // Create MediaFormat
                val format = MediaFormat.createAudioFormat(
                    MediaFormat.MIMETYPE_AUDIO_FLAC,
                    config.sampleRate,
                    config.channels,
                ).apply {
                    // Set max input size (conservative estimate for FLAC frames)
                    setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 32768)

                    // Request output PCM encoding matching source bit depth.
                    // API 24+ supports KEY_PCM_ENCODING; 24/32-bit encodings require API 31+.
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        val pcmEncoding = when (config.bitDepth) {
                            24 -> AudioFormat.ENCODING_PCM_24BIT_PACKED
                            32 -> AudioFormat.ENCODING_PCM_32BIT
                            else -> AudioFormat.ENCODING_PCM_16BIT
                        }
                        setInteger(MediaFormat.KEY_PCM_ENCODING, pcmEncoding)
                        logger.i { "Requested PCM encoding for ${config.bitDepth}-bit output" }
                    } else {
                        // Pre-S: MediaCodec will output 16-bit regardless
                        outputBitDepth = 16
                        logger.i { "API ${Build.VERSION.SDK_INT} < 31, falling back to 16-bit output" }
                    }

                    // FLAC requires the STREAMINFO metadata block as CSD-0.
                    // The server sends codec_header as a base64-encoded string containing
                    // the 34-byte STREAMINFO block (min/max block size, frame size,
                    // sample rate, channels, bits per sample, total samples, MD5).
                    if (!codecHeader.isNullOrEmpty()) {
                        try {
                            val headerBytes = Base64.decode(codecHeader)
                            val csd = ByteBuffer.wrap(headerBytes)
                            setByteBuffer("csd-0", csd)
                            logger.i { "Set FLAC STREAMINFO from codec_header (${headerBytes.size} bytes)" }
                        } catch (e: Exception) {
                            logger.w(e) { "Failed to base64-decode codec header, continuing without it" }
                        }
                    } else {
                        logger.w { "No codec_header provided for FLAC - decoder may fail" }
                    }
                }

                // Configure codec (no surface, no crypto, decoder mode)
                newCodec.configure(format, null, null, 0)
                newCodec.start()

                // Only assign after successful start - ensures codec is always in
                // Executing state when visible to other threads
                codec = newCodec

                logger.i { "FLAC decoder initialized (outputBitDepth=$outputBitDepth)" }
            } catch (e: IOException) {
                logger.e(e) { "Failed to create FLAC decoder - codec not available" }
                throw IllegalStateException(
                    "FLAC decoder not available on this device. " +
                            "This is unexpected on Android API 26+. " +
                            "Please report this device model.",
                    e,
                )
            } catch (e: IllegalStateException) {
                logger.e(e) { "Failed to configure FLAC decoder" }
                throw IllegalStateException("FLAC decoder configuration failed", e)
            } catch (e: Exception) {
                logger.e(e) { "Unexpected error during FLAC decoder initialization" }
                throw IllegalStateException("FLAC decoder initialization failed", e)
            }
        }
    }

    actual override fun decode(encodedData: ByteArray): ByteArray {
        return synchronized(decoderLock) {
            val currentCodec = codec
                ?: run {
                    logger.w { "Decoder not available (released or not configured)" }
                    return@synchronized ByteArray(0)
                }

            if (encodedData.isEmpty()) {
                logger.w { "Received empty encoded data" }
                return@synchronized ByteArray(0)
            }

            try {
                logger.d { "Decoding FLAC packet: ${encodedData.size} bytes" }

                val outputStream = ByteArrayOutputStream()

                // 1. Submit input with retry.
                // When all input buffers are occupied (codec backpressure), we drain
                // output to free slots, then retry. This prevents silent frame drops.
                var submitted = false
                for (attempt in 0..MAX_INPUT_RETRIES) {
                    val inputIndex = currentCodec.dequeueInputBuffer(TIMEOUT_US)
                    if (inputIndex >= 0) {
                        val inputBuffer = currentCodec.getInputBuffer(inputIndex)
                            ?: error("Input buffer is null")

                        inputBuffer.clear()
                        inputBuffer.put(encodedData)

                        currentCodec.queueInputBuffer(
                            inputIndex,
                            0,                     // offset
                            encodedData.size,      // size
                            0,                     // presentation time
                            0,                      // flags
                        )
                        submitted = true
                        break
                    }

                    // No input buffer available — drain output to free a slot, then retry
                    if (attempt < MAX_INPUT_RETRIES) {
                        drainOutput(currentCodec, outputStream)
                    }
                }

                if (!submitted) {
                    logger.e { "Failed to submit input after ${MAX_INPUT_RETRIES + 1} attempts, frame dropped (${encodedData.size} bytes)" }
                }

                // 2. Drain all available output buffers
                drainOutput(currentCodec, outputStream)

                val pcmData = outputStream.toByteArray()
                logger.d { "Decoded ${pcmData.size} PCM bytes ($outputBitDepth-bit)" }
                pcmData
            } catch (e: IllegalStateException) {
                logger.e(e) { "MediaCodec error during decode" }
                ByteArray(0)
            } catch (e: Exception) {
                logger.e(e) { "Unexpected error during decode" }
                ByteArray(0)
            }
        }
    }

    /**
     * Drain all available output buffers from the codec.
     *
     * MediaCodec is asynchronous: queuing input doesn't immediately produce output.
     * This method loops to collect all available decoded PCM data, handling format
     * changes and deprecated status codes along the way.
     */
    private fun drainOutput(codec: MediaCodec, outputStream: ByteArrayOutputStream) {
        val bufferInfo = MediaCodec.BufferInfo()

        while (true) {
            val outputIndex = codec.dequeueOutputBuffer(bufferInfo, TIMEOUT_US)

            when {
                outputIndex >= 0 -> {
                    val outBuffer = codec.getOutputBuffer(outputIndex)
                    if (outBuffer != null && bufferInfo.size > 0) {
                        val pcmData = ByteArray(bufferInfo.size)
                        outBuffer.position(bufferInfo.offset)
                        outBuffer.get(pcmData, 0, bufferInfo.size)
                        outputStream.write(pcmData)
                    }
                    codec.releaseOutputBuffer(outputIndex, false)
                }

                outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    val format = codec.outputFormat
                    logger.i { "Output format changed: $format" }
                    // Read actual PCM encoding the codec chose
                    if (format.containsKey(MediaFormat.KEY_PCM_ENCODING)) {
                        outputBitDepth = when (format.getInteger(MediaFormat.KEY_PCM_ENCODING)) {
                            AudioFormat.ENCODING_PCM_24BIT_PACKED -> 24
                            AudioFormat.ENCODING_PCM_32BIT -> 32
                            AudioFormat.ENCODING_PCM_FLOAT -> 32
                            else -> 16
                        }
                        logger.i { "MediaCodec actual output: $outputBitDepth-bit" }
                    }
                    // Continue draining — there may be more output buffers
                }

                @Suppress("DEPRECATION")
                outputIndex == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED -> {
                    // Deprecated since API 21, but some devices still return it
                    // Continue draining
                }

                else -> {
                    // INFO_TRY_AGAIN_LATER or unknown: no more output available
                    break
                }
            }
        }
    }

    actual override fun reset() {
        synchronized(decoderLock) {
            logger.i { "Resetting FLAC decoder" }
            val currentCodec = codec ?: run {
                logger.w { "No codec to reset (already released)" }
                return
            }
            try {
                // Flush codec buffers (clears input/output queues)
                currentCodec.flush()
                logger.d { "Codec flushed successfully" }
            } catch (e: IllegalStateException) {
                logger.e(e) { "Error flushing codec during reset" }
                // If flush fails, try stop/start cycle to recover
                try {
                    currentCodec.stop()
                    currentCodec.start()
                    logger.w { "Codec restarted after flush failure" }
                } catch (e2: Exception) {
                    logger.e(e2) { "Failed to restart codec, releasing it" }
                    // If stop/start also fails, codec is in an unrecoverable state.
                    // Release it and null the reference so decode() returns silence
                    // instead of crashing on a Released-state MediaCodec.
                    try {
                        currentCodec.release()
                    } catch (e3: Exception) {
                        logger.e(e3) { "Error releasing codec after failed restart" }
                    }
                    codec = null
                }
            }
        }
    }

    actual override fun release() {
        synchronized(decoderLock) {
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
    }

    actual override fun getOutputCodec(): AudioCodec = AudioCodec.PCM
    actual override fun getOutputBitDepth(): Int = outputBitDepth

    private companion object {
        // Timeout for MediaCodec operations (microseconds) — 10ms
        const val TIMEOUT_US = 10_000L

        /**
         * Maximum number of retry attempts when no input buffer is available.
         * Each retry waits TIMEOUT_US (10ms), so 3 retries = up to 40ms total.
         * Between retries we drain output to free slots.
         */
        const val MAX_INPUT_RETRIES = 3
    }
}
