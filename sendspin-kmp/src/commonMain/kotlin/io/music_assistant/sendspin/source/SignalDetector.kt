package io.music_assistant.sendspin.source

import io.music_assistant.sendspin.api.CaptureFormat
import io.music_assistant.sendspin.api.CapturedAudio
import io.music_assistant.sendspin.api.SourceSignal

/**
 * Line sense on the captured PCM, before encoding. The signal is [SourceSignal.Absent] once
 * every sample stayed at or below [THRESHOLD] for [HOLD_MICROS] of capture time, and
 * [SourceSignal.Present] again on the first block above it. Time comes from the capture
 * timestamps, so the detector has no clock of its own.
 *
 * Only the top 16 bits of each sample count, so 16, 24 and 32-bit input share one threshold.
 * Other depths are always [SourceSignal.Present].
 */
internal class SignalDetector(private val format: CaptureFormat) {
    private val bytesPerSample = format.bitDepth / Byte.SIZE_BITS
    private val senses = format.bitDepth % Byte.SIZE_BITS == 0 && bytesPerSample >= 2

    /** The capture time of the first quiet block of the current quiet run; null while loud. */
    private var quietSinceMicros: Long? = null

    var signal: SourceSignal = SourceSignal.Present
        private set

    fun onAudio(audio: CapturedAudio): SourceSignal {
        if (!senses) return signal
        if (isLoud(audio.pcm)) {
            quietSinceMicros = null
            signal = SourceSignal.Present
        } else {
            val since = quietSinceMicros ?: audio.captureTimeMicros.also { quietSinceMicros = it }
            if (audio.captureTimeMicros + durationMicros(audio.pcm) - since >= HOLD_MICROS) signal = SourceSignal.Absent
        }
        return signal
    }

    private fun isLoud(pcm: ByteArray): Boolean {
        // The most significant two bytes of a little-endian sample are its last two.
        var i = bytesPerSample - 2
        while (i + 1 < pcm.size) {
            val top = (pcm[i + 1].toInt() shl Byte.SIZE_BITS) or (pcm[i].toInt() and BYTE_MASK)
            if (top > THRESHOLD || top < -THRESHOLD) return true
            i += bytesPerSample
        }
        return false
    }

    private fun durationMicros(pcm: ByteArray): Long =
        pcm.size.toLong() / format.bytesPerFrame * MICROS_PER_SECOND / format.sampleRate

    internal companion object {
        /** About -70 dBFS of 16-bit full scale (32768): above dither, below any quiet passage. */
        const val THRESHOLD = 10

        /** Gaps between tracks and short pauses stay [SourceSignal.Present]. */
        const val HOLD_MICROS = 5_000_000L

        private const val BYTE_MASK = 0xFF
        private const val MICROS_PER_SECOND = 1_000_000L
    }
}
