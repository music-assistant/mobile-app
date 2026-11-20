package io.music_assistant.client.share

import android.annotation.SuppressLint
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioPlaybackCaptureConfiguration
import android.media.AudioRecord
import android.media.AudioTimestamp
import android.media.projection.MediaProjection
import android.os.Build
import android.os.Process
import androidx.annotation.RequiresApi
import co.touchlab.kermit.Logger
import io.music_assistant.sendspin.api.AudioCapture
import io.music_assistant.sendspin.api.CaptureFormat
import io.music_assistant.sendspin.api.CapturedAudio
import io.music_assistant.sendspin.api.MonotonicClock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import kotlin.math.abs

/**
 * The audio that other apps play, through the playback capture of [projection]. The capture
 * leaves out the audio of this app, so a local player in the group never feeds the source.
 * An app that forbids capture, and every call, gives silence.
 *
 * [clock] must be the clock of the Sendspin player, on the `BOOTTIME` base: the player turns
 * each capture time into server time with it.
 */
@RequiresApi(Build.VERSION_CODES.Q)
internal class PlaybackCapture(
    private val projection: MediaProjection,
    private val clock: MonotonicClock,
) : AudioCapture {
    private val log = Logger.withTag("PlaybackCapture")

    override val format = CaptureFormat(sampleRate = SAMPLE_RATE, channels = CHANNELS, bitDepth = BIT_DEPTH)

    // The record permission is a precondition of the share: the launcher asks for it first.
    @SuppressLint("MissingPermission")
    override fun frames(): Flow<CapturedAudio> = flow {
        val blockBytes = SAMPLE_RATE * BLOCK_MILLIS / MILLIS_PER_SECOND * format.bytesPerFrame
        val record = AudioRecord.Builder()
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_IN_STEREO)
                    .build(),
            )
            .setBufferSizeInBytes(maxOf(minBufferBytes() * 2, blockBytes * BUFFER_BLOCKS))
            .setAudioPlaybackCaptureConfig(
                AudioPlaybackCaptureConfiguration.Builder(projection)
                    .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
                    .addMatchingUsage(AudioAttributes.USAGE_GAME)
                    .addMatchingUsage(AudioAttributes.USAGE_UNKNOWN)
                    .excludeUid(Process.myUid())
                    .build(),
            )
            .build()
        val thread = Process.myTid()
        val priority = Process.getThreadPriority(thread)
        try {
            check(record.state == AudioRecord.STATE_INITIALIZED) { "The playback capture did not start" }
            Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)
            record.startRecording()
            log.i { "Playback capture started: recording=${record.recordingState == AudioRecord.RECORDSTATE_RECORDING}" }
            val timeline = CaptureTimeline()
            while (currentCoroutineContext().isActive) {
                val block = ByteArray(blockBytes)
                val read = record.read(block, 0, blockBytes, AudioRecord.READ_BLOCKING)
                check(read >= 0) { "The playback capture failed: $read" }
                val frames = read / format.bytesPerFrame
                if (frames == 0) continue
                val pcm = if (read == blockBytes) block else block.copyOf(frames * format.bytesPerFrame)
                emit(CapturedAudio(pcm, timeline.timeOf(record, frames)))
            }
        } finally {
            // The thread goes back to the shared pool.
            Process.setThreadPriority(thread, priority)
            runCatching { record.stop() }
            record.release()
        }
    }.flowOn(Dispatchers.IO)

    private fun minBufferBytes(): Int =
        AudioRecord.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_IN_STEREO, AudioFormat.ENCODING_PCM_16BIT)
            .coerceAtLeast(0)

    /**
     * The capture time of each block. The times follow the frame count, so they have no jitter
     * of the reader thread. The anchor is the audio timestamp of the record, or the read time
     * where the device has none. If the count and the clock disagree by more than
     * [RE_ANCHOR_MICROS], frames were lost (the reader stalled), and the anchor moves.
     */
    private inner class CaptureTimeline {
        private val timestamp = AudioTimestamp()
        private var framesRead = 0L

        /** The capture time of frame 0. */
        private var anchorMicros: Long? = null

        /** The capture time of the first of the [frames] just read. */
        fun timeOf(record: AudioRecord, frames: Int): Long {
            val observed = observedAnchor(record, frames)
            val anchor = anchorMicros
                ?.takeIf { abs(it - observed) <= RE_ANCHOR_MICROS }
                ?: observed.also { moved ->
                    anchorMicros?.let { log.w { "Capture timeline moved by ${moved - it} us" } }
                    anchorMicros = moved
                }
            return (anchor + micros(framesRead)).also { framesRead += frames }
        }

        /**
         * Where frame 0 is on the clock, as this read sees it. The read time is always right
         * within a block. The audio timestamp is more exact, but a device can report one that
         * does not fit the frames that were read, and such a time makes the player drop every
         * chunk as too old. So the timestamp counts only while it agrees with the read time.
         */
        private fun observedAnchor(record: AudioRecord, frames: Int): Long {
            val byReadTime = clock.nowMicros() - micros(framesRead + frames)
            val byTimestamp = timestamp
                .takeIf { record.getTimestamp(it, AudioTimestamp.TIMEBASE_BOOTTIME) == AudioRecord.SUCCESS }
                ?.let { it.nanoTime / NANOS_PER_MICRO - micros(it.framePosition) }
                ?.takeIf { abs(it - byReadTime) <= RE_ANCHOR_MICROS }
            return byTimestamp ?: byReadTime
        }

        private fun micros(frames: Long): Long = frames * MICROS_PER_SECOND / SAMPLE_RATE
    }

    private companion object {
        const val SAMPLE_RATE = 48_000
        const val CHANNELS = 2
        const val BIT_DEPTH = 16
        const val BLOCK_MILLIS = 20
        const val BUFFER_BLOCKS = 4
        const val MILLIS_PER_SECOND = 1_000
        const val MICROS_PER_SECOND = 1_000_000L
        const val NANOS_PER_MICRO = 1_000L
        const val RE_ANCHOR_MICROS = 100_000L
    }
}
