@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")

package io.music_assistant.client.player

import io.music_assistant.client.player.sendspin.model.AudioCodec

/**
 * MediaPlayerController - iOS stub for Sendspin
 *
 * Handles raw PCM audio streaming for Sendspin protocol.
 * TODO: Implement using AVAudioEngine or AudioQueue
 */
actual class MediaPlayerController actual constructor(platformContext: PlatformContext) {
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
        listener: MediaPlayerListener
    ) {
        val player = PlatformPlayerProvider.player
        if (player != null) {
            player.prepareStream(codec.name.lowercase(), sampleRate, channels, bitDepth, codecHeader, listener)
            isPrepared = true
            
            // Set up remote command handler for Control Center buttons
            player.setRemoteCommandHandler(object : RemoteCommandHandler {
                override fun onCommand(command: String) {
                    println("🎵 MediaPlayerController: Remote command received: $command")
                    onRemoteCommand?.invoke(command)
                }
            })
        } else {
            println("MediaPlayerController: No PlatformAudioPlayer registered!")
            listener.onError(Exception("Audio Player implementation missing"))
        }
    }

    actual fun writeRawPcm(data: ByteArray): Int {
        val player = PlatformPlayerProvider.player
        if (player != null) {
            player.writeRawPcm(data)
            return data.size
        }
        return 0
    }

    actual fun pauseSink() { /* no-op on iOS */ }
    actual fun resumeSink() { /* no-op on iOS */ }
    actual fun flush() { /* no-op on iOS */ }

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
        // TODO: Add getVolume to interface if needed, for now return dummy
        return 100
    }
    
    // Now Playing (Control Center / Lock Screen)
    actual fun updateNowPlaying(
        title: String?,
        artist: String?,
        album: String?,
        artworkUrl: String?,
        duration: Double,
        elapsedTime: Double,
        playbackRate: Double
    ) {
        PlatformPlayerProvider.player?.updateNowPlaying(
            title, artist, album, artworkUrl, duration, elapsedTime, playbackRate
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
