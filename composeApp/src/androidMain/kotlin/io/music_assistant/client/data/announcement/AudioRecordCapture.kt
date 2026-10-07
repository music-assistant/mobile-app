package io.music_assistant.client.data.announcement

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive

/**
 * [MicrophoneCapture] over [AudioRecord]. The voice-communication source brings the platform's
 * echo cancellation, noise suppression and gain control, as the web does in the browser.
 */
class AudioRecordCapture(
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) : MicrophoneCapture {
    // The UI asks for RECORD_AUDIO first; without it the recorder does not initialize, and that throws.
    @SuppressLint("MissingPermission")
    override fun frames(): Flow<PcmFrame> = flow {
        val sampleRate = SAMPLE_RATES.firstOrNull { minBufferSize(it) > 0 }
            ?: error("No supported microphone sample rate")
        val frameBytes = sampleRate / FRAMES_PER_SECOND * BYTES_PER_SAMPLE
        val record = AudioRecord(
            MediaRecorder.AudioSource.VOICE_COMMUNICATION,
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            maxOf(minBufferSize(sampleRate), frameBytes * BUFFERED_FRAMES),
        )
        try {
            check(record.state == AudioRecord.STATE_INITIALIZED) { "Microphone did not initialize" }
            record.startRecording()
            check(record.recordingState == AudioRecord.RECORDSTATE_RECORDING) { "Microphone did not start" }
            while (currentCoroutineContext().isActive) {
                val frame = ByteArray(frameBytes)
                var filled = 0
                while (filled < frameBytes) {
                    val read = record.read(frame, filled, frameBytes - filled)
                    check(read >= 0) { "Microphone read failed: $read" }
                    filled += read
                }
                emit(PcmFrame(sampleRate, frame))
            }
        } finally {
            runCatching { record.stop() }
            record.release()
        }
    }.flowOn(dispatcher)

    private fun minBufferSize(sampleRate: Int) =
        AudioRecord.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)

    private companion object {
        /** 48 kHz first for clear speech on music speakers; 16 kHz is the floor every device has. */
        val SAMPLE_RATES = listOf(48_000, 44_100, 16_000)
        const val BYTES_PER_SAMPLE = 2
        const val FRAMES_PER_SECOND = 50 // 20 ms frames
        const val BUFFERED_FRAMES = 4
    }
}
