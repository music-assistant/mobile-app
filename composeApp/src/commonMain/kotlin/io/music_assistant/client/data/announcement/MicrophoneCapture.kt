package io.music_assistant.client.data.announcement

import kotlinx.coroutines.flow.Flow

/** The platform microphone, as the raw PCM that a live announcement streams. */
interface MicrophoneCapture {
    /**
     * Mono s16le PCM in short frames, tens of milliseconds each. Collecting records, and the
     * microphone is freed when collection stops. Throws when the microphone cannot start, for
     * example without the record permission.
     */
    fun frames(): Flow<PcmFrame>
}

/** The rate rides on each frame: iOS knows it only once the hardware runs, and Bluetooth lowers it. */
class PcmFrame(val sampleRate: Int, val bytes: ByteArray) {
    val durationMillis: Long get() = bytes.size / BYTES_PER_SAMPLE * MILLIS_PER_SECOND / sampleRate

    private companion object {
        const val BYTES_PER_SAMPLE = 2
        const val MILLIS_PER_SECOND = 1_000L
    }
}
