// PCM bit-depth literals (16/24/32) and codec frame-size hints are audio-format standards.
@file:Suppress("MagicNumber")

package io.music_assistant.client.player.local

import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaFormat
import android.os.Build
import co.touchlab.kermit.Logger
import io.github.jaredmdobson.concentus.OpusException
import io.music_assistant.sendspin.api.AudioDecoder
import io.music_assistant.sendspin.api.DecoderFactory
import io.music_assistant.sendspin.wire.AudioCodec
import io.music_assistant.sendspin.wire.AudioFormatSpec
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import io.github.jaredmdobson.concentus.OpusDecoder as ConcentusOpusDecoder

/** Android decoders: MediaCodec for FLAC, Concentus for Opus, pass-through for PCM. */
class AndroidDecoderFactory : DecoderFactory {
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
 */
internal class FlacDecoder : AudioDecoder {
    private val logger = Logger.withTag("FlacDecoder")
    private var codec: MediaCodec? = null
    private val bufferInfo = MediaCodec.BufferInfo()

    override var outputBitDepth: Int = 16
        private set
    override val outputCodec: AudioCodec = AudioCodec.PCM

    override fun configure(format: AudioFormatSpec, codecHeader: ByteArray?) {
        release()
        require(format.channels in 1..8) { "FLAC supports 1-8 channels, got ${format.channels}" }
        outputBitDepth = format.bitDepth
        val mediaFormat = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_FLAC, format.sampleRate, format.channels).apply {
            setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, MAX_INPUT_SIZE)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                setInteger(
                    MediaFormat.KEY_PCM_ENCODING,
                    when (format.bitDepth) {
                        24 -> AudioFormat.ENCODING_PCM_24BIT_PACKED
                        32 -> AudioFormat.ENCODING_PCM_32BIT
                        else -> AudioFormat.ENCODING_PCM_16BIT
                    },
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
            drain(current, out)
            out.toByteArray()
        } catch (e: IllegalStateException) {
            logger.e(e) { "MediaCodec error during decode" }
            ByteArray(0)
        }
    }

    private fun drain(codec: MediaCodec, out: ByteArrayOutputStream) {
        while (true) {
            val index = codec.dequeueOutputBuffer(bufferInfo, TIMEOUT_US)
            when {
                index >= 0 -> {
                    codec.getOutputBuffer(index)?.takeIf { bufferInfo.size > 0 }?.let { buffer ->
                        val pcm = ByteArray(bufferInfo.size)
                        buffer.position(bufferInfo.offset)
                        buffer.get(pcm, 0, bufferInfo.size)
                        out.write(pcm)
                    }
                    codec.releaseOutputBuffer(index, false)
                }

                index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    val format = codec.outputFormat
                    if (format.containsKey(MediaFormat.KEY_PCM_ENCODING)) {
                        outputBitDepth = when (format.getInteger(MediaFormat.KEY_PCM_ENCODING)) {
                            AudioFormat.ENCODING_PCM_24BIT_PACKED -> 24
                            AudioFormat.ENCODING_PCM_32BIT, AudioFormat.ENCODING_PCM_FLOAT -> 32
                            else -> 16
                        }
                    }
                }

                @Suppress("DEPRECATION")
                index == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED -> Unit

                else -> return // INFO_TRY_AGAIN_LATER: nothing more right now
            }
        }
    }

    override fun reset() {
        val current = codec ?: return
        try {
            current.flush()
        } catch (e: IllegalStateException) {
            logger.w(e) { "FLAC flush failed; restarting the codec" }
            runCatching { current.stop(); current.start() }.onFailure { release() }
        }
    }

    override fun release() {
        codec?.let { runCatching { it.stop(); it.release() } }
        codec = null
    }

    private companion object {
        const val MAX_INPUT_SIZE = 32_768
        const val TIMEOUT_US = 10_000L
        const val MAX_INPUT_RETRIES = 3
    }
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
