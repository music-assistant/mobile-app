package io.music_assistant.client.player

import platform.Foundation.NSData

/**
 * Interface for platform-specific audio player implementation.
 * This allows Swift (or other iOS logic) to provide the actual player.
 */
interface PlatformAudioPlayer {
    fun prepareStream(
        codec: String,
        sampleRate: Int,
        channels: Int,
        bitDepth: Int,
        codecHeader: String?,
        listener: MediaPlayerListener
    )
    fun writeRawPcm(data: ByteArray)
    /**
     * Efficient variant called from Kotlin: data is already converted to NSData using
     * usePinned bulk-copy, avoiding a byte-by-byte Swift interop loop.
     */
    fun writeRawPcmNSData(data: NSData)
    fun stopRawPcmStream()
    fun setVolume(volume: Int)
    fun setMuted(muted: Boolean)
    fun dispose()
    
    // Now Playing (Control Center / Lock Screen)
    fun updateNowPlaying(
        title: String?,
        artist: String?,
        album: String?,
        artworkUrl: String?,
        duration: Double,
        elapsedTime: Double,
        playbackRate: Double
    )
    fun clearNowPlaying()
    
    // Remote command handler (set by Kotlin to receive play/pause/next/prev events)
    fun setRemoteCommandHandler(handler: RemoteCommandHandler?)
}

/**
 * Handler for remote commands from Control Center/Lock Screen
 */
interface RemoteCommandHandler {
    fun onCommand(command: String)
}

/**
 * Singleton provider to bridge Kotlin and Swift.
 * Swift should assign its implementation to `player` at startup.
 */
object PlatformPlayerProvider {
    var player: PlatformAudioPlayer? = null
}
