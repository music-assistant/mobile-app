// PCM bit-depth literals (16/24) and codec frame-size hints are audio-format standards.
@file:Suppress("MagicNumber")

package io.music_assistant.sendspin.android

import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaFormat
import android.os.Build
import co.touchlab.kermit.Logger
import io.github.jaredmdobson.concentus.OpusException
import io.music_assistant.sendspin.api.AudioCodec
import io.music_assistant.sendspin.api.AudioDecoder
import io.music_assistant.sendspin.api.AudioFormatSpec
import io.music_assistant.sendspin.api.DecoderFactory
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.roundToInt
import io.github.jaredmdobson.concentus.OpusDecoder as ConcentusOpusDecoder

/** Android decoders: MediaCodec for FLAC, Concentus for Opus, pass-through for PCM. */
public class AndroidDecoderFactory : DecoderFactory {
    override fun supports(codec: AudioCodec): Boolean = true

    override fun create(codec: AudioCodec): AudioDecoder = when (codec) {
        AudioCodec.FLAC -> FlacDecoder()
        AudioCodec.OPUS -> OpusDecoder()
        AudioCodec.PCM -> PcmDecoder()
    }
}

/** Decoders run on the single audio thread; none of them lock. */
internal class PcmDecoder : AudioDecoder {
    override var outputBitDepth: Int = 16
        private set
    override val outputCodec: AudioCodec = AudioCodec.PCM

    override fun configure(format: AudioFormatSpec, codecHeader: ByteArray?) {
        outputBitDepth = format.bitDepth
    }

    override fun decode(input: ByteArray, offset: Int, length: Int): ByteArray =
        if (offset == 0 && length == input.size) input else input.copyOfRange(offset, offset + length)

    override fun reset() = Unit
    override fun release() = Unit
}

/**
 * FLAC through the platform [MediaCodec]. Requests native bit depth on API 31+
 * (16-bit below), and reports what the codec actually produces.
 *
 * A 24-bit stream asks for float output and converts it back to 24-bit packed:
 * the stock decoder (`C2SoftFlacDec`) produces only 16-bit or float, and for a
 * 24-bit request the framework pads its 16-bit output. Float holds 24-bit
 * samples exactly.
 */
internal class FlacDecoder : AudioDecoder {
    private val logger = Logger.withTag("FlacDecoder")
    private var codec: MediaCodec? = null
    private val bufferInfo = MediaCodec.BufferInfo()

    override var outputBitDepth: Int = 16
        private set
    override val outputCodec: AudioCodec = AudioCodec.PCM

    /** The codec outputs float, which [decode] converts to 24-bit packed. */
    private var floatOutput = false

    override fun configure(format: AudioFormatSpec, codecHeader: ByteArray?) {
        release()
        require(format.channels in 1..8) { "FLAC supports 1-8 channels, got ${format.channels}" }
        outputBitDepth = format.bitDepth
        floatOutput = false
        val mediaFormat = MediaFormat.createAudioFormat(
            MediaFormat.MIMETYPE_AUDIO_FLAC,
            format.sampleRate,
            format.channels,
        ).apply {
            setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, MAX_INPUT_SIZE)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                // The framework converts to the requested encoding, so float is what arrives.
                floatOutput = format.bitDepth == 24
                setInteger(
                    MediaFormat.KEY_PCM_ENCODING,
                    if (format.bitDepth == 24) AudioFormat.ENCODING_PCM_FLOAT else AudioFormat.ENCODING_PCM_16BIT,
                )
            } else {
                outputBitDepth = 16
            }
            // STREAMINFO from the stream's codec header is CSD-0.
            if (codecHeader != null && codecHeader.isNotEmpty()) {
                setByteBuffer("csd-0", ByteBuffer.wrap(codecHeader))
            } else {
                logger.w { "No codec header for FLAC; the decoder may fail" }
            }
        }
        codec = MediaCodec.createDecoderByType(MediaFormat.MIMETYPE_AUDIO_FLAC).also {
            it.configure(mediaFormat, null, null, 0)
            it.start()
        }
    }

    override fun decode(input: ByteArray, offset: Int, length: Int): ByteArray {
        val current = codec ?: return ByteArray(0)
        if (length == 0) return ByteArray(0)
        val out = ByteArrayOutputStream()
        return try {
            var submitted = false
            for (attempt in 0..MAX_INPUT_RETRIES) {
                val index = current.dequeueInputBuffer(TIMEOUT_US)
                if (index >= 0) {
                    val buffer = current.getInputBuffer(index) ?: error("input buffer is null")
                    buffer.clear()
                    buffer.put(input, offset, length)
                    current.queueInputBuffer(index, 0, length, 0, 0)
                    submitted = true
                    break
                }
                // All input slots taken: free some by draining output, then retry.
                if (attempt < MAX_INPUT_RETRIES) drain(current, out)
            }
            if (!submitted) logger.w { "FLAC input not accepted after ${MAX_INPUT_RETRIES + 1} attempts; frame dropped" }
            // The scheduler aligns this PCM to this chunk's timestamp: wait for the frame just queued,
            // or a late codec hands it to the next call and the timeline shifts by one chunk.
            drain(current, out, awaitOutput = submitted)
            out.toByteArray()
        } catch (e: IllegalStateException) {
            logger.e(e) { "MediaCodec error during decode" }
            ByteArray(0)
        }
    }

    /**
     * Collects all ready output. With [awaitOutput], blocks until the first PCM buffer
     * arrives or [OUTPUT_WAIT_US] passes; otherwise never waits.
     */
    private fun drain(codec: MediaCodec, out: ByteArrayOutputStream, awaitOutput: Boolean = false) {
        var waitedUs = 0L
        var gotPcm = false
        while (true) {
            val waiting = awaitOutput && !gotPcm && waitedUs < OUTPUT_WAIT_US
            val index = codec.dequeueOutputBuffer(bufferInfo, if (waiting) TIMEOUT_US else 0L)
            when {
                index >= 0 -> {
                    codec.getOutputBuffer(index)?.takeIf { bufferInfo.size > 0 }?.let { buffer ->
                        val pcm = ByteArray(bufferInfo.size)
                        buffer.position(bufferInfo.offset)
                        buffer.get(pcm, 0, bufferInfo.size)
                        out.write(if (floatOutput) floatToPcm24(pcm) else pcm)
                        gotPcm = true
                    }
                    codec.releaseOutputBuffer(index, false)
                }

                index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    val format = codec.outputFormat
                    if (format.containsKey(MediaFormat.KEY_PCM_ENCODING)) {
                        val encoding = format.getInteger(MediaFormat.KEY_PCM_ENCODING)
                        floatOutput = encoding == AudioFormat.ENCODING_PCM_FLOAT
                        outputBitDepth = when (encoding) {
                            AudioFormat.ENCODING_PCM_24BIT_PACKED, AudioFormat.ENCODING_PCM_FLOAT -> 24
                            else -> 16
                        }
                    }
                }

                @Suppress("DEPRECATION")
                index == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED -> Unit

                // INFO_TRY_AGAIN_LATER: keep waiting for the awaited frame, else nothing more right now.
                waiting -> waitedUs += TIMEOUT_US

                else -> {
                    if (awaitOutput && !gotPcm) logger.w { "FLAC output not ready after ${OUTPUT_WAIT_US / 1000} ms" }
                    return
                }
            }
        }
    }

    override fun reset() {
        val current = codec ?: return
        try {
            current.flush()
        } catch (e: IllegalStateException) {
            logger.w(e) { "FLAC flush failed; restarting the codec" }
            runCatching {
                current.stop()
                current.start()
            }.onFailure { release() }
        }
    }

    override fun release() {
        codec?.let {
            runCatching {
                it.stop()
                it.release()
            }
        }
        codec = null
    }

    private companion object {
        const val MAX_INPUT_SIZE = 32_768
        const val TIMEOUT_US = 10_000L
        const val MAX_INPUT_RETRIES = 3

        /** Bound on waiting for a queued frame's output; one FLAC block is ~96 ms of audio. */
        const val OUTPUT_WAIT_US = 100_000L
    }
}

/**
 * Native-order float PCM to 24-bit packed little-endian. A float holds a 24-bit
 * sample exactly, so 24-bit sources round-trip bit for bit.
 */
internal fun floatToPcm24(floatPcm: ByteArray): ByteArray {
    val samples = ByteBuffer.wrap(floatPcm).order(ByteOrder.nativeOrder()).asFloatBuffer()
    val out = ByteArray(samples.remaining() * 3)
    for (i in 0 until samples.remaining()) {
        val v = (samples.get(i) * 8_388_608.0).roundToInt().coerceIn(-8_388_608, 8_388_607)
        out[i * 3] = v.toByte()
        out[i * 3 + 1] = (v shr 8).toByte()
        out[i * 3 + 2] = (v shr 16).toByte()
    }
    return out
}

/** Opus through Concentus (pure Kotlin/Java libopus port); always 16-bit output. */
internal class OpusDecoder : AudioDecoder {
    private val logger = Logger.withTag("OpusDecoder")
    private var decoder: ConcentusOpusDecoder? = null
    private var channels = 0
    private var pcm = ShortArray(0)

    override val outputBitDepth: Int = 16
    override val outputCodec: AudioCodec = AudioCodec.PCM

    override fun configure(format: AudioFormatSpec, codecHeader: ByteArray?) {
        require(format.channels in 1..2) { "Opus supports 1 or 2 channels, got ${format.channels}" }
        require(format.sampleRate in OPUS_RATES) { "Opus rate must be one of $OPUS_RATES, got ${format.sampleRate}" }
        channels = format.channels
        decoder = ConcentusOpusDecoder(format.sampleRate, format.channels)
        pcm = ShortArray(MAX_FRAME_SAMPLES * channels)
        // The OpusHead pre-skip is not applied; the first frame may click.
    }

    override fun decode(input: ByteArray, offset: Int, length: Int): ByteArray {
        val current = decoder ?: return ByteArray(0)
        if (length == 0) return ByteArray(0)
        return try {
            val samples = current.decode(input, offset, length, pcm, 0, pcm.size / channels, false)
            if (samples <= 0) return ByteArray(0)
            val total = samples * channels
            val out = ByteArray(total * 2)
            for (i in 0 until total) {
                val v = pcm[i].toInt()
                out[i * 2] = v.toByte()
                out[i * 2 + 1] = (v shr 8).toByte()
            }
            out
        } catch (e: OpusException) {
            logger.e(e) { "Opus decode error" }
            ByteArray(0)
        }
    }

    override fun reset() {
        runCatching { decoder?.resetState() }
    }

    override fun release() {
        decoder = null
        pcm = ShortArray(0)
    }

    private companion object {
        val OPUS_RATES = setOf(8000, 12000, 16000, 24000, 48000)

        /** 120 ms at 48 kHz, the largest Opus frame. */
        const val MAX_FRAME_SAMPLES = 5760
    }
}
