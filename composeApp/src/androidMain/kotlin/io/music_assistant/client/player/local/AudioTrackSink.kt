package io.music_assistant.client.player.local

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTimestamp
import android.media.AudioTrack
import android.os.Build
import co.touchlab.kermit.Logger
import io.music_assistant.sendspin.api.AudioSink
import io.music_assistant.sendspin.api.MonotonicClock
import io.music_assistant.sendspin.api.SinkEvent
import io.music_assistant.sendspin.api.SinkFormat
import io.music_assistant.sendspin.api.SinkHandle
import io.music_assistant.sendspin.api.SinkPosition
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow

/**
 * [AudioSink] over [AudioTrack]. Every [open] builds a fresh track: audio
 * routing goes stale silently while paused (Bluetooth, Android Auto), and a
 * rebuild makes that bug class impossible by construction.
 *
 * Interruptions are reported, not handled: focus loss, an incoming call, or
 * unplugged headphones emit [SinkEvent.FocusLost]; the player ends the stream
 * and the app pauses the server. Ducking is the track's own gain.
 */
class AudioTrackSink(
    context: Context,
    private val clock: MonotonicClock,
) : AudioSink {
    private val logger = Logger.withTag("AudioTrackSink")
    private val context = context.applicationContext
    private val audioManager = this.context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    override fun open(format: SinkFormat): SinkHandle {
        require(format.isPcm) { "AudioTrackSink plays PCM only, got ${format.codec}" }
        val channelMask = when (format.channels) {
            1 -> AudioFormat.CHANNEL_OUT_MONO
            2 -> AudioFormat.CHANNEL_OUT_STEREO
            else -> throw IllegalArgumentException("unsupported channel count ${format.channels}")
        }
        val encoding = when {
            format.bitDepth == 16 -> AudioFormat.ENCODING_PCM_16BIT
            format.bitDepth == 24 && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> AudioFormat.ENCODING_PCM_24BIT_PACKED
            format.bitDepth == 32 && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> AudioFormat.ENCODING_PCM_32BIT
            else -> throw IllegalArgumentException(
                "unsupported bit depth ${format.bitDepth} on API ${Build.VERSION.SDK_INT}",
            )
        }
        val minBuffer = AudioTrack.getMinBufferSize(format.sampleRate, channelMask, encoding)
        check(minBuffer > 0) { "audio configuration not supported by the device ($minBuffer)" }

        val track = AudioTrack.Builder()
            .setAudioAttributes(MEDIA_ATTRIBUTES)
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(format.sampleRate)
                    .setChannelMask(channelMask)
                    .setEncoding(encoding)
                    .build(),
            )
            // 4x minimum absorbs scheduling jitter while keeping the steady-state fill low.
            .setBufferSizeInBytes(minBuffer * BUFFER_MULTIPLIER)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
            .build()
        if (track.state != AudioTrack.STATE_INITIALIZED) {
            track.release()
            error("AudioTrack failed to initialize")
        }
        // The default start threshold is the whole buffer: hundreds of ms of pure latency.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            runCatching { track.setStartThresholdInFrames(1) }
        }
        track.play()
        logger.i {
            "AudioTrack opened: ${format.sampleRate}Hz ${format.channels}ch ${format.bitDepth}bit, buffer ${minBuffer * BUFFER_MULTIPLIER}"
        }
        return Handle(track, format)
    }

    private inner class Handle(private val track: AudioTrack, private val format: SinkFormat) : SinkHandle {
        private val sinkEvents = MutableSharedFlow<SinkEvent>(extraBufferCapacity = 8)
        private val timestamp = AudioTimestamp()
        private var interrupted = false

        override val events: Flow<SinkEvent> = sinkEvents
        override val latencyMicros: Long? = null

        private val focusListener = AudioManager.OnAudioFocusChangeListener { change ->
            when (change) {
                AudioManager.AUDIOFOCUS_GAIN -> {
                    runCatching { track.setVolume(1f) }
                    if (interrupted) {
                        interrupted = false
                        sinkEvents.tryEmit(SinkEvent.FocusRegained)
                    }
                }

                AudioManager.AUDIOFOCUS_LOSS -> interrupt(resumable = false)
                AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> interrupt(resumable = true)
                AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> runCatching { track.setVolume(DUCK_GAIN) }
            }
        }
        private val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(MEDIA_ATTRIBUTES)
            .setOnAudioFocusChangeListener(focusListener)
            .build()

        // Headphones unplugged or Bluetooth gone: the output is no longer what the user hears.
        private val noisyReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) interrupt(resumable = false)
            }
        }

        // Some Bluetooth and Android Auto routings deliver no focus loss on a call.
        private val modeListener = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            AudioManager.OnModeChangedListener { mode ->
                if (mode == AudioManager.MODE_IN_CALL || mode == AudioManager.MODE_IN_COMMUNICATION) {
                    interrupt(
                        resumable = true,
                    )
                }
            }
        } else {
            null
        }

        init {
            val granted = audioManager.requestAudioFocus(focusRequest) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
            if (!granted) logger.w { "Audio focus not granted; playing anyway" }
            context.registerReceiver(noisyReceiver, IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY))
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                modeListener?.let { runCatching { audioManager.addOnModeChangedListener(context.mainExecutor, it) } }
            }
        }

        private fun interrupt(resumable: Boolean) {
            interrupted = resumable
            runCatching {
                track.pause()
                track.flush()
            }
            sinkEvents.tryEmit(SinkEvent.FocusLost)
        }

        override fun write(pcm: ByteArray, offset: Int, length: Int): Int {
            val written = try {
                track.write(pcm, offset, length)
            } catch (e: IllegalStateException) {
                logger.w(e) { "AudioTrack write on a released track" }
                return -1
            }
            return if (written < 0) {
                logger.w { "AudioTrack write error $written" }
                -1
            } else {
                written
            }
        }

        override fun pause() {
            runCatching { track.pause() }
        }

        override fun resume() {
            runCatching { track.play() }
        }

        override fun flush() {
            runCatching { track.flush() }
        }

        override fun position(): SinkPosition? {
            if (track.getTimestamp(timestamp)) {
                val ageMicros = (System.nanoTime() - timestamp.nanoTime) / 1_000
                return SinkPosition(timestamp.framePosition, clock.nowMicros() - ageMicros)
            }
            val head = track.playbackHeadPosition.toLong() and 0xFFFF_FFFFL
            return SinkPosition(head, clock.nowMicros())
        }

        override fun underrunCount(): Int = runCatching { track.underrunCount }.getOrDefault(0)

        override fun close() {
            runCatching { context.unregisterReceiver(noisyReceiver) }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                modeListener?.let { runCatching { audioManager.removeOnModeChangedListener(it) } }
            }
            runCatching {
                track.pause()
                track.flush()
                track.stop()
            }
            track.release()
            audioManager.abandonAudioFocusRequest(focusRequest)
            logger.i { "AudioTrack closed (${format.sampleRate}Hz)" }
        }
    }

    private companion object {
        const val BUFFER_MULTIPLIER = 4
        const val DUCK_GAIN = 0.2f
        val MEDIA_ATTRIBUTES: AudioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .build()
    }
}
