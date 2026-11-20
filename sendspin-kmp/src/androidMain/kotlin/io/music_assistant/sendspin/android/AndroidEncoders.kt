package io.music_assistant.sendspin.android

import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaFormat
import io.music_assistant.sendspin.api.AudioCodec
import io.music_assistant.sendspin.api.AudioEncoder
import io.music_assistant.sendspin.api.CaptureFormat
import io.music_assistant.sendspin.api.CapturedAudio
import io.music_assistant.sendspin.api.EncodedAudio
import io.music_assistant.sendspin.api.EncoderFactory

/** Android encoders for the source role: MediaCodec for 16-bit FLAC, nothing else. */
public class AndroidEncoderFactory : EncoderFactory {
    override fun create(codec: AudioCodec, format: CaptureFormat): AudioEncoder? =
        format.takeIf { codec == AudioCodec.FLAC && it.bitDepth == FlacEncoder.BIT_DEPTH }?.let(::FlacEncoder)
}

/**
 * FLAC through the platform [MediaCodec]. The capture time travels through the codec
 * as the presentation time, so each output buffer comes back with the time of its
 * first sample. Any codec failure throws, and the player then continues with PCM.
 */
internal class FlacEncoder(private val format: CaptureFormat) : AudioEncoder {
    private val bufferInfo = MediaCodec.BufferInfo()
    private var codec: MediaCodec? = null

    /** Capture time of the first input. Presentation times are relative to it. */
    private var baseMicros: Long? = null

    override var header: ByteArray? = null
        private set

    init {
        val mediaFormat = MediaFormat.createAudioFormat(
            MediaFormat.MIMETYPE_AUDIO_FLAC,
            format.sampleRate,
            format.channels,
        ).apply {
            setInteger(MediaFormat.KEY_PCM_ENCODING, AudioFormat.ENCODING_PCM_16BIT)
            setInteger(MediaFormat.KEY_FLAC_COMPRESSION_LEVEL, COMPRESSION_LEVEL)
        }
        codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_FLAC).also {
            it.configure(mediaFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            it.start()
        }
    }

    override fun encode(audio: CapturedAudio): List<EncodedAudio> {
        val current = checkNotNull(codec) { "FLAC encoder is released" }
        val base = baseMicros ?: audio.captureTimeMicros.also { baseMicros = it }
        val encoded = ArrayList<EncodedAudio>()
        var offset = 0
        var refusals = 0
        while (offset < audio.pcm.size) {
            val index = current.dequeueInputBuffer(TIMEOUT_US)
            if (index < 0) {
                // All input slots taken: free some by draining output, then retry.
                check(++refusals <= MAX_INPUT_RETRIES) { "FLAC encoder accepts no input" }
                drain(current, base, encoded)
                continue
            }
            val buffer = checkNotNull(current.getInputBuffer(index)) { "input buffer is null" }
            buffer.clear()
            val room = buffer.remaining() / format.bytesPerFrame * format.bytesPerFrame
            val length = minOf(audio.pcm.size - offset, room)
            buffer.put(audio.pcm, offset, length)
            current.queueInputBuffer(index, 0, length, audio.captureTimeMicros - base + micros(offset), 0)
            offset += length
        }
        drain(current, base, encoded)
        return encoded
    }

    /** Collects all ready output; never waits, because a FLAC block spans several inputs. */
    private fun drain(codec: MediaCodec, base: Long, encoded: MutableList<EncodedAudio>) {
        while (true) {
            val index = codec.dequeueOutputBuffer(bufferInfo, 0L)
            when {
                index >= 0 -> {
                    val bytes = ByteArray(bufferInfo.size)
                    codec.getOutputBuffer(index)?.let { buffer ->
                        buffer.position(bufferInfo.offset)
                        buffer.get(bytes)
                    }
                    val config = bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0
                    when {
                        config -> header = header ?: flacStreamHeader(bytes)
                        bytes.isNotEmpty() -> encoded += EncodedAudio(bytes, base + bufferInfo.presentationTimeUs)
                    }
                    codec.releaseOutputBuffer(index, false)
                }

                index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED ->
                    header = header ?: codec.outputFormat.getByteBuffer(CSD_0)?.let { csd ->
                        flacStreamHeader(ByteArray(csd.remaining()).also { csd.duplicate().get(it) })
                    }

                else -> return
            }
        }
    }

    private fun micros(bytes: Int): Long = bytes.toLong() / format.bytesPerFrame * MICROS_PER_SECOND / format.sampleRate

    override fun release() {
        codec?.let {
            runCatching {
                it.stop()
                it.release()
            }
        }
        codec = null
    }

    internal companion object {
        const val BIT_DEPTH = 16
        private const val COMPRESSION_LEVEL = 5
        private const val TIMEOUT_US = 10_000L
        private const val MAX_INPUT_RETRIES = 3
        private const val MICROS_PER_SECOND = 1_000_000L
        private const val CSD_0 = "csd-0"
    }
}

private const val FLAC_MARKER_BYTES = 4
private const val BLOCK_HEADER_BYTES = 4
private const val STREAMINFO_BYTES = 34
private const val LAST_BLOCK_FLAG = 0x80
private val FLAC_MARKER = byteArrayOf(0x66, 0x4C, 0x61, 0x43) // "fLaC"

/**
 * The `codec_header` of the source role from the encoder's codec config: the `fLaC`
 * marker and the STREAMINFO block, marked as the last metadata block. The platform
 * encoder appends more blocks (a vendor comment), and some give the bare STREAMINFO.
 * Null when [config] is neither.
 */
internal fun flacStreamHeader(config: ByteArray): ByteArray? {
    val headerBytes = FLAC_MARKER_BYTES + BLOCK_HEADER_BYTES + STREAMINFO_BYTES
    val hasMarker = config.size >= headerBytes && config.copyOf(FLAC_MARKER_BYTES).contentEquals(FLAC_MARKER)
    return when {
        hasMarker -> config.copyOf(headerBytes).also { it[FLAC_MARKER_BYTES] = LAST_BLOCK_FLAG.toByte() }
        config.size == STREAMINFO_BYTES ->
            FLAC_MARKER + byteArrayOf(LAST_BLOCK_FLAG.toByte(), 0, 0, STREAMINFO_BYTES.toByte()) + config
        else -> null
    }
}
