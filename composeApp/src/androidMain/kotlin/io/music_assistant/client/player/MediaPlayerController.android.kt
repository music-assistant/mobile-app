@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")

package io.music_assistant.client.player

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Build
import co.touchlab.kermit.Logger

import io.music_assistant.client.player.sendspin.model.AudioCodec

/**
 * MediaPlayerController - Sendspin audio player
 *
 * Handles raw PCM audio streaming for Sendspin protocol.
 * Built-in player (ExoPlayer) has been removed - Sendspin is now the only playback method.
 */
actual class MediaPlayerController actual constructor(platformContext: PlatformContext) {
    
    // Callback for remote commands - currently unused on Android (handled via different mechanism if needed)
    actual var onRemoteCommand: ((String) -> Unit)? = null
    private val logger = Logger.withTag("MediaPlayerController")
    private val context: Context = platformContext.applicationContext
    private val audioManager: AudioManager =
        context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    // AudioTrack for raw PCM streaming (Sendspin)
    private var audioTrack: AudioTrack? = null
    private var audioTrackCreationTime: Long = 0 // Timestamp when AudioTrack was created
    private var currentListener: MediaPlayerListener? = null // Track listener to signal errors

    // AudioFocus management for Android Auto
    private var audioFocusRequest: AudioFocusRequest? = null
    private var hasAudioFocus = false

    // Playback state - controls whether we should write audio data
    private var shouldPlayAudio = false

    // Volume state (0-100)
    private var currentVolume: Int = 100
    private var isMuted: Boolean = false

    // BroadcastReceiver for detecting audio becoming noisy (headphone disconnection)
    // Backup mechanism in addition to audio focus handling
    private val noisyAudioReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) {
                logger.w { "Audio becoming noisy (headphones unplugged) - stopping playback" }
                handleAudioOutputDisconnected()
            }
        }
    }
    private var isNoisyReceiverRegistered = false

    // AudioFocus listener for handling focus changes (Android Auto, phone calls, etc.)
    private val audioFocusChangeListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
        when (focusChange) {
            AudioManager.AUDIOFOCUS_GAIN -> {
                logger.i { "AudioFocus gained" }
                hasAudioFocus = true
                shouldPlayAudio = true

                // Resume playback if it was paused
                audioTrack?.let { track ->
                    if (track.playState != AudioTrack.PLAYSTATE_PLAYING) {
                        logger.i { "Resuming AudioTrack playback after focus gain" }
                        // Flush any stale data that was written while paused
                        track.flush()
                        track.play()
                    }
                }
                // Restore volume if it was ducked
                applyVolume()
            }

            AudioManager.AUDIOFOCUS_LOSS -> {
                logger.w { "AudioFocus lost permanently (Android Auto connected, another app took focus, etc.)" }
                hasAudioFocus = false
                // Stop playback completely when we permanently lose audio focus
                // This happens when Android Auto connects or another app takes over audio
                handleAudioOutputDisconnected()
            }

            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                // Check if AudioTrack was just created (track transition)
                // If so, ignore transient focus loss to prevent interrupting new track
                val timeSinceCreation = System.currentTimeMillis() - audioTrackCreationTime
                if (timeSinceCreation < 1000) {
                    logger.i { "AudioFocus lost temporarily, but ignoring (track was just created ${timeSinceCreation}ms ago)" }
                    return@OnAudioFocusChangeListener
                }

                logger.i { "AudioFocus lost temporarily" }
                hasAudioFocus = false
                shouldPlayAudio = false
                // Pause playback
                audioTrack?.pause()
            }

            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                logger.i { "AudioFocus lost temporarily (can duck)" }
                // Lower volume (duck) but continue playing
                audioTrack?.setVolume(0.2f)
            }
        }
    }

    // Request audio focus for playback (critical for Android Auto)
    private fun requestAudioFocus(): Boolean {
        // Always request focus to ensure we're in sync with the system
        // Even if we think we have it, re-requesting ensures proper state
        logger.d { "Requesting audio focus (hasAudioFocus=$hasAudioFocus)" }

        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .build()

        // Reuse existing request if we have one, or create new one
        val request = audioFocusRequest ?: AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(audioAttributes)
            .setOnAudioFocusChangeListener(audioFocusChangeListener)
            .build()
        audioFocusRequest = request
        val result = audioManager.requestAudioFocus(request)

        hasAudioFocus = result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        logger.i { "Audio focus request result: ${if (hasAudioFocus) "GRANTED" else "DENIED"}" }
        return hasAudioFocus
    }

    // Release audio focus
    private fun releaseAudioFocus() {
        if (!hasAudioFocus) {
            return
        }

        audioFocusRequest?.let {
            audioManager.abandonAudioFocusRequest(it)
        }

        hasAudioFocus = false
        audioFocusRequest = null
        logger.i { "Audio focus released" }
    }

    // Sendspin raw PCM streaming methods

    /**
     * Handle audio output disconnection (Android Auto, headphones, Bluetooth).
     * Stop playback locally and signal error to stop the sendspin stream.
     */
    private fun handleAudioOutputDisconnected() {
        logger.w { "Handling audio output disconnection - stopping sendspin stream" }

        // Stop playback
        shouldPlayAudio = false

        // Pause the AudioTrack
        audioTrack?.let { track ->
            try {
                if (track.playState == AudioTrack.PLAYSTATE_PLAYING) {
                    track.pause()
                    track.flush() // Clear buffer
                    logger.i { "AudioTrack paused and flushed due to output disconnection" }
                }
            } catch (e: Exception) {
                logger.e(e) { "Error pausing AudioTrack on disconnection" }
            }
        }

        // Release audio focus - we don't need it anymore since output is disconnected
        releaseAudioFocus()

        // Signal error to stop the sendspin stream
        // This will propagate up to AudioStreamManager which will call stopStream()
        currentListener?.onError(Exception("Audio output disconnected (Android Auto, headphones, or Bluetooth)"))

        logger.i { "Sent error signal to stop sendspin stream. User should press play to resume on phone speakers." }
    }

    actual fun prepareStream(
        codec: AudioCodec,
        sampleRate: Int,
        channels: Int,
        bitDepth: Int,
        codecHeader: String?,
        listener: MediaPlayerListener
    ) {
        // Android ignores codecHeader - it's only for iOS/MPV pass-through
        logger.i { "Preparing raw PCM stream: ${sampleRate}Hz, ${channels}ch, ${bitDepth}bit" }

        // Store listener so we can signal errors (e.g., audio output disconnection)
        currentListener = listener

        // Request audio focus before creating AudioTrack
        if (!requestAudioFocus()) {
            logger.w { "Failed to gain audio focus, but continuing anyway" }
        }

        // Register noisy audio receiver (headphone unplug detection)
        registerNoisyAudioReceiver()

        // Release existing AudioTrack if any
        audioTrack?.release()

        // Convert parameters to Android AudioFormat constants
        val channelConfig = when (channels) {
            1 -> AudioFormat.CHANNEL_OUT_MONO
            2 -> AudioFormat.CHANNEL_OUT_STEREO
            else -> {
                logger.w { "Unsupported channel count: $channels, using stereo" }
                AudioFormat.CHANNEL_OUT_STEREO
            }
        }

        val encoding = when {
            bitDepth == 8 -> AudioFormat.ENCODING_PCM_8BIT
            bitDepth == 16 -> AudioFormat.ENCODING_PCM_16BIT
            bitDepth == 24 && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> AudioFormat.ENCODING_PCM_24BIT_PACKED
            bitDepth == 32 && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> AudioFormat.ENCODING_PCM_32BIT
            else -> {
                logger.w { "Unsupported bit depth: $bitDepth, using 16-bit" }
                AudioFormat.ENCODING_PCM_16BIT
            }
        }

        // Calculate buffer size
        val minBufferSize = AudioTrack.getMinBufferSize(sampleRate, channelConfig, encoding)
        val bufferSize = minBufferSize * 4 // Use 4x min buffer for smoother playback

        logger.i { "AudioTrack config: sampleRate=$sampleRate, channels=$channels, bitDepth=$bitDepth" }
        logger.i { "AudioTrack buffer: $bufferSize bytes (min: $minBufferSize)" }

        try {
            audioTrack = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setSampleRate(sampleRate)
                        .setChannelMask(channelConfig)
                        .setEncoding(encoding)
                        .build()
                )
                .setBufferSizeInBytes(bufferSize)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()

            // Record creation time to help ignore spurious focus changes during transitions
            audioTrackCreationTime = System.currentTimeMillis()

            val state = audioTrack?.state
            val playState = audioTrack?.playState
            logger.i { "AudioTrack created: state=$state (${if (state == AudioTrack.STATE_INITIALIZED) "INITIALIZED" else "UNINITIALIZED"}), playState=$playState" }

            audioTrack?.play()

            val newPlayState = audioTrack?.playState
            logger.i { "AudioTrack started: playState=$newPlayState (${if (newPlayState == AudioTrack.PLAYSTATE_PLAYING) "PLAYING" else "NOT_PLAYING"})" }

            // Set playback state to true since we're starting playback
            shouldPlayAudio = true

            // Apply current volume/mute state
            applyVolume()

            listener.onReady()

        } catch (e: Exception) {
            logger.e(e) { "Failed to create AudioTrack" }
            listener.onError(e)
        }
    }

    actual fun writeRawPcm(data: ByteArray): Int {
        val track = audioTrack
        if (track == null) {
            logger.w { "AudioTrack not initialized" }
            return 0
        }

        // Don't write audio if we've lost focus or been paused
        if (!shouldPlayAudio) {
            logger.d { "Skipping audio write - shouldPlayAudio=false (audio focus lost or paused)" }
            // Return the full size to indicate we "handled" the data
            // This prevents the sendspin buffer from backing up
            return data.size
        }

        return try {
            val playState = track.playState
            val state = track.state

            if (playState != AudioTrack.PLAYSTATE_PLAYING) {
                logger.w { "AudioTrack not playing! playState=$playState, state=$state" }
            }

            val written = track.write(data, 0, data.size)
            if (written < 0) {
                val errorName = when (written) {
                    AudioTrack.ERROR_INVALID_OPERATION -> "ERROR_INVALID_OPERATION"
                    AudioTrack.ERROR_BAD_VALUE -> "ERROR_BAD_VALUE"
                    AudioTrack.ERROR_DEAD_OBJECT -> "ERROR_DEAD_OBJECT"
                    else -> "UNKNOWN_ERROR($written)"
                }
                logger.w { "AudioTrack write error: $errorName" }
                0
            } else {
                logger.d { "AudioTrack wrote $written/${data.size} bytes" }
                written
            }
        } catch (e: Exception) {
            logger.e(e) { "Error writing PCM data" }
            0
        }
    }

    actual fun pauseSink() {
        audioTrack?.pause()
    }

    actual fun resumeSink() {
        if (shouldPlayAudio) audioTrack?.play()
    }

    actual fun flush() {
        audioTrack?.flush()
    }

    actual fun stopRawPcmStream() {
        logger.i { "Stopping raw PCM stream" }

        shouldPlayAudio = false
        currentListener = null // Clear listener reference

        audioTrack?.let { track ->
            try {
                if (track.playState == AudioTrack.PLAYSTATE_PLAYING) {
                    track.pause()
                }
                track.flush()
                track.stop()
                track.release()
            } catch (e: Exception) {
                logger.e(e) { "Error stopping AudioTrack" }
            }
        }

        audioTrack = null
        // Don't release audio focus here - keep it during track transitions
        // Only release in release() when truly stopping playback
    }

    actual fun setVolume(volume: Int) {
        currentVolume = volume.coerceIn(0, 100)
        logger.i { "Setting volume to $currentVolume" }
        applyVolume()
    }

    actual fun setMuted(muted: Boolean) {
        isMuted = muted
        logger.i { "Setting muted to $muted (audioTrack=${if (audioTrack != null) "initialized" else "null"})" }
        applyVolume()
    }

    actual fun getCurrentSystemVolume(): Int {
        val currentSystemVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val volumePercent = if (maxVolume > 0) {
            (currentSystemVolume * 100 / maxVolume).coerceIn(0, 100)
        } else {
            logger.w { "AudioManager returned max volume 0, defaulting to 0%" }
            0
        }
        logger.d { "System volume: $currentSystemVolume/$maxVolume = $volumePercent%" }
        return volumePercent
    }

    private fun applyVolume() {
        val track = audioTrack ?: return

        // AudioTrack uses 0.0 to 1.0 scale
        val volumeFloat = if (isMuted) {
            0f
        } else {
            (currentVolume / 100f).coerceIn(0f, 1f)
        }

        try {
            track.setVolume(volumeFloat)
            logger.d { "Applied volume: $volumeFloat (volume=$currentVolume, muted=$isMuted)" }
        } catch (e: Exception) {
            logger.e(e) { "Error setting volume" }
        }
    }

    private fun registerNoisyAudioReceiver() {
        if (!isNoisyReceiverRegistered) {
            val filter = IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
            context.registerReceiver(noisyAudioReceiver, filter)
            isNoisyReceiverRegistered = true
            logger.d { "Registered noisy audio receiver" }
        }
    }

    private fun unregisterNoisyAudioReceiver() {
        if (isNoisyReceiverRegistered) {
            try {
                context.unregisterReceiver(noisyAudioReceiver)
                isNoisyReceiverRegistered = false
                logger.d { "Unregistered noisy audio receiver" }
            } catch (e: Exception) {
                logger.e(e) { "Error unregistering noisy audio receiver" }
            }
        }
    }
    
    // Now Playing - no-op on Android (uses MediaSession instead)
    actual fun updateNowPlaying(
        title: String?,
        artist: String?,
        album: String?,
        artworkUrl: String?,
        duration: Double,
        elapsedTime: Double,
        playbackRate: Double
    ) {
        // Android handles Now Playing via MediaSession, not implemented here
    }
    
    actual fun clearNowPlaying() {
        // Android handles Now Playing via MediaSession, not implemented here
    }

    actual fun release() {
        logger.i { "Releasing MediaPlayerController" }
        unregisterNoisyAudioReceiver()
        stopRawPcmStream()
        releaseAudioFocus()
    }
}

actual class PlatformContext(val applicationContext: Context)