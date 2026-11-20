package io.music_assistant.sendspin.source

import co.touchlab.kermit.Logger
import io.music_assistant.sendspin.api.AudioCodec
import io.music_assistant.sendspin.api.AudioEncoder
import io.music_assistant.sendspin.api.CaptureFormat
import io.music_assistant.sendspin.api.CapturedAudio
import io.music_assistant.sendspin.api.EncodedAudio
import io.music_assistant.sendspin.wire.ClientStreamSource
import io.music_assistant.sendspin.wire.ClientStreamStartMessage
import io.music_assistant.sendspin.wire.ClientStreamStartPayload
import kotlin.concurrent.Volatile
import kotlin.io.encoding.Base64

/** The format that one `client-stream/start` announces. Chunks refer to it by identity. */
internal class SourceStreamFormat(
    private val codec: AudioCodec,
    private val capture: CaptureFormat,
    private val header: ByteArray?,
) {
    override fun toString(): String =
        "${codec.name} ${capture.sampleRate} Hz ${capture.channels} ch ${capture.bitDepth} bit"

    fun startMessage() = ClientStreamStartMessage(
        payload = ClientStreamStartPayload(
            ClientStreamSource(
                codec = codec.name.lowercase(),
                channels = capture.channels,
                sampleRate = capture.sampleRate,
                bitDepth = capture.bitDepth,
                codecHeader = header?.let { Base64.encode(it) },
            ),
        ),
    )
}

/**
 * Every chunk carries its format, so the sender announces a format exactly when the
 * first chunk of it leaves. A chunk that the backlog drops takes no announcement with it.
 */
internal class SourceChunk(val format: SourceStreamFormat, val audio: EncodedAudio)

/** Whether FLAC still works. One per enabled player: after a failure, every later stream is PCM. */
internal class SourceCodecHealth {
    @Volatile
    var flacFailed = false
}

/**
 * Encodes one stream: FLAC while [flac] works, PCM from its first failure on. A FLAC
 * encoder that gives no header in [HEADER_TIMEOUT_MICROS] of audio counts as failed,
 * because the server waits only a few seconds for the stream. Single-threaded.
 */
internal class SourceEncoding(
    private val capture: CaptureFormat,
    private var flac: AudioEncoder?,
    private val health: SourceCodecHealth,
) {
    private val logger = Logger.withTag("Sendspin.Source")
    private val pcm = PcmChunker(capture)
    private val pcmFormat = SourceStreamFormat(AudioCodec.PCM, capture, header = null)
    private var flacFormat: SourceStreamFormat? = null

    /** FLAC output from before the header is known. */
    private val held = ArrayList<EncodedAudio>()
    private var firstInputMicros: Long? = null

    fun encode(audio: CapturedAudio): List<SourceChunk> {
        val encoder = flac ?: return pcmChunks(audio)
        val first = firstInputMicros ?: audio.captureTimeMicros.also { firstInputMicros = it }
        val encoded = try {
            encoder.encode(audio)
        } catch (e: Exception) {
            return fallBack(audio, "encoder failed: ${e.message}")
        }
        val format = flacFormat
            ?: encoder.header?.let { SourceStreamFormat(AudioCodec.FLAC, capture, it) }?.also { flacFormat = it }
            ?: run {
                held += encoded
                val overdue = audio.captureTimeMicros - first > HEADER_TIMEOUT_MICROS
                return if (overdue) fallBack(audio, "no codec header") else emptyList()
            }
        return (held + encoded).map { SourceChunk(format, it) }.also { held.clear() }
    }

    fun release() {
        flac?.release()
        flac = null
    }

    private fun pcmChunks(audio: CapturedAudio) = pcm.chunk(audio).map { SourceChunk(pcmFormat, it) }

    private fun fallBack(audio: CapturedAudio, reason: String): List<SourceChunk> {
        logger.w { "FLAC $reason. The source continues with PCM" }
        health.flacFailed = true
        held.clear()
        release()
        return pcmChunks(audio)
    }

    private companion object {
        const val HEADER_TIMEOUT_MICROS = 2_000_000L
    }
}

/** Cuts captured PCM into chunks of [chunkMillis], each with the capture time of its first frame. */
internal class PcmChunker(private val format: CaptureFormat, chunkMillis: Int = CHUNK_MILLIS) {
    private val chunkBytes = format.sampleRate * chunkMillis / MILLIS_PER_SECOND * format.bytesPerFrame
    private var pending = ByteArray(0)
    private var pendingMicros = 0L

    fun chunk(audio: CapturedAudio): List<EncodedAudio> {
        val startMicros = if (pending.isEmpty()) audio.captureTimeMicros else pendingMicros
        val data = if (pending.isEmpty()) audio.pcm else pending + audio.pcm
        if (data.size == chunkBytes) {
            pending = ByteArray(0)
            return listOf(EncodedAudio(data, startMicros))
        }
        val chunks = ArrayList<EncodedAudio>(data.size / chunkBytes)
        var offset = 0
        while (data.size - offset >= chunkBytes) {
            chunks += EncodedAudio(data.copyOfRange(offset, offset + chunkBytes), startMicros + micros(offset))
            offset += chunkBytes
        }
        pending = data.copyOfRange(offset, data.size)
        pendingMicros = startMicros + micros(offset)
        return chunks
    }

    private fun micros(bytes: Int): Long = bytes.toLong() / format.bytesPerFrame * MICROS_PER_SECOND / format.sampleRate

    private companion object {
        const val CHUNK_MILLIS = 20
        const val MILLIS_PER_SECOND = 1_000
        const val MICROS_PER_SECOND = 1_000_000L
    }
}
