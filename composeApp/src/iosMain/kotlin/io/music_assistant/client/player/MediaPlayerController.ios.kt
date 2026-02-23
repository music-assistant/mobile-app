@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")

package io.music_assistant.client.player

import io.music_assistant.client.player.sendspin.model.AudioCodec
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.AVFAudio.AVAudioSession
import platform.Foundation.NSData
import platform.Foundation.NSMutableData

/**
 * MediaPlayerController - iOS implementation for Sendspin
 *
 * Delegates to NativeAudioController (Swift) via PlatformPlayerProvider.
 * NativeAudioController uses AudioQueue for playback and supports FLAC/Opus/PCM
 * via libFLAC, swift-opus, and PCM passthrough.
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

    @OptIn(ExperimentalForeignApi::class)
    actual fun writeRawPcm(data: ByteArray): Int {
        val player = PlatformPlayerProvider.player ?: return 0
        // Bulk-copy ByteArray → NSData via usePinned, avoiding a per-byte Swift interop loop
        val nsData: NSData = if (data.isEmpty()) {
            NSData()
        } else {
            val mutableData = NSMutableData()
            data.usePinned { pinned ->
                mutableData.appendBytes(pinned.addressOf(0), data.size.toULong())
            }
            mutableData
        }
        player.writeRawPcmNSData(nsData)
        return data.size
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
        return (AVAudioSession.sharedInstance().outputVolume.toFloat() * 100).toInt()
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
