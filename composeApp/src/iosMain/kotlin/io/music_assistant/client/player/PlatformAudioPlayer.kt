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
        listener: MediaPlayerListener,
    )
    fun writeRawPcm(data: ByteArray)

    /**
     * Efficient variant called from Kotlin: data is already converted to NSData using
     * usePinned bulk-copy, avoiding a byte-by-byte Swift interop loop.
     */
    fun writeRawPcmNSData(data: NSData)

    /** Pause output (user pause / interruption / re-phase). */
    fun pauseSink()

    /** Resume output, reactivating the audio session to reclaim it from other apps. */
    fun resumeSink()

    /** Drop buffered PCM (track transition / playback-delay re-phase). */
    fun flush()

    fun stopRawPcmStream()
    fun setVolume(volume: Int)
    fun setMuted(muted: Boolean)
    fun dispose()

    // Now Playing (Control Center / Lock Screen).
    //
    // Nullable `duration` / `elapsedTime` means "unknown — leave the iOS field alone."
    // The Swift-side adapter merges into the existing `MPNowPlayingInfoCenter` dict
    // rather than replacing it, so a nil here preserves whatever iOS last had.
    fun updateNowPlaying(
        title: String?,
        artist: String?,
        album: String?,
        artworkUrl: String?,
        duration: Double?,
        elapsedTime: Double?,
        playbackRate: Double,
    )
    fun clearNowPlaying()

    // Remote command handler (set by Kotlin to receive play/pause/next/prev events)
    fun setRemoteCommandHandler(handler: RemoteCommandHandler?)
}

/**
 * Handler for player commands originating on the iOS side.
 *
 * `source` identifies who issued the command so logs can distinguish a genuine
 * user action from an automatic one: "remote" (Control Center / lock screen),
 * "interruption" (audio-session interruption began/ended), or "route_loss"
 * (output device disappeared). It is diagnostic only — `command` alone drives
 * playback.
 */
interface RemoteCommandHandler {
    fun onCommand(command: String, source: String)
}

/**
 * Singleton provider to bridge Kotlin and Swift.
 * Swift should assign its implementation to `player` at startup.
 */
object PlatformPlayerProvider {
    var player: PlatformAudioPlayer? = null
}
