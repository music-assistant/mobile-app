@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")

package io.music_assistant.client.player

import co.touchlab.kermit.Logger
import io.music_assistant.client.player.sendspin.model.AudioCodec

/**
 * MediaPlayerController - iOS implementation for Sendspin
 *
 * Delegates to NativeAudioController (Swift) via PlatformPlayerProvider.
 * NativeAudioController uses AudioQueue for playback and supports FLAC/Opus/PCM
 * via libFLAC, swift-opus, and PCM passthrough.
 */
actual class MediaPlayerController actual constructor(platformContext: PlatformContext) {
    private val log = Logger.withTag("MediaPlayerController")
    private var isPrepared: Boolean = false

    // Callback for remote commands from Control Center
    actual var onRemoteCommand: ((String) -> Unit)? = null

    // Sendspin streaming methods
    actual fun prepareStream(
        codec: AudioCodec,
        sampleRate: Int,
        channels: Int,
        bitDepth: Int,
        codecHeader: String?,
        listener: MediaPlayerListener,
    ) {
        val player = PlatformPlayerProvider.player
        if (player != null) {
            player.prepareStream(codec.name.lowercase(), sampleRate, channels, bitDepth, codecHeader, listener)
            isPrepared = true

            // Set up remote command handler for iOS-originated commands
            player.setRemoteCommandHandler(object : RemoteCommandHandler {
                override fun onCommand(command: String, source: String) {
                    log.i { "iOS command [$source]: $command" }
                    onRemoteCommand?.invoke(command)
                }
            })
        } else {
            println("MediaPlayerController: No PlatformAudioPlayer registered!")
            listener.onError(Exception("Audio Player implementation missing"))
        }
    }

    actual fun writeRawPcm(data: ByteArray): Int {
        val player = PlatformPlayerProvider.player ?: return 0
        if (data.isNotEmpty()) {
            player.writeRawPcm(data)
        }
        return data.size
    }

    actual fun pauseSink() { PlatformPlayerProvider.player?.pauseSink() }
    actual fun resumeSink() { PlatformPlayerProvider.player?.resumeSink() }
    actual fun flush() { PlatformPlayerProvider.player?.flush() }

    actual fun stopRawPcmStream() {
        PlatformPlayerProvider.player?.stopRawPcmStream()
        isPrepared = false
    }

    actual fun setVolume(volume: Int) {
        PlatformPlayerProvider.player?.setVolume(volume)
    }

    actual fun setMuted(muted: Boolean) {
        PlatformPlayerProvider.player?.setMuted(muted)
    }

    actual fun release() {
        PlatformPlayerProvider.player?.dispose()
        isPrepared = false
    }

    actual fun getCurrentSystemVolume(): Int {
        // Return a default volume value - actual system volume control is managed by iOS
        return 100
    }

    // Now Playing (Control Center / Lock Screen)
    actual fun updateNowPlaying(
        title: String?,
        artist: String?,
        album: String?,
        artworkUrl: String?,
        duration: Double?,
        elapsedTime: Double?,
        playbackRate: Double,
    ) {
        PlatformPlayerProvider.player?.updateNowPlaying(
            title,
            artist,
            album,
            artworkUrl,
            duration,
            elapsedTime,
            playbackRate,
        )
    }

    actual fun clearNowPlaying() {
        PlatformPlayerProvider.player?.clearNowPlaying()
    }

    fun setRemoteCommandHandler(handler: RemoteCommandHandler?) {
        PlatformPlayerProvider.player?.setRemoteCommandHandler(handler)
    }
}

actual class PlatformContext
