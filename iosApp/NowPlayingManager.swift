import Foundation
import MediaPlayer
import AVFoundation

/// Manages iOS Now Playing info (Control Center, Lock Screen)
/// and remote command handling (play/pause/next/prev buttons)
///
/// Audio playback is handled by NativeAudioController via AudioQueue.
class NowPlayingManager {
    
    typealias CommandHandler = (String) -> Void
    
    static let shared = NowPlayingManager()
    
    private var commandHandler: CommandHandler?
    
    // State for caching and flicker prevention
    private var lastTrackIdentifier: String?
    private var cachedArtwork: MPMediaItemArtwork?
    private var currentTask: URLSessionDataTask?
    
    // Track current metadata state to determine if we need to fetch new artwork
    private var currentTitle: String?
    private var currentArtist: String?
    private var currentAlbum: String?
    
    init() {
        print("🎵 NowPlayingManager: Initializing...")
        configureAudioSession()
        setupRemoteCommands() // Setup commands once
        printDebugState("After init")
    }
    
    /// Configures the audio session for background playback
    private func configureAudioSession() {
        do {
            let session = AVAudioSession.sharedInstance()
            try session.setCategory(.playback, mode: .default, options: [])
            try session.setActive(true)
            print("🎵 NowPlayingManager: Audio session configured")
        } catch {
            print("🎵 NowPlayingManager: ❌ Failed to configure audio session: \(error)")
        }
    }
    
    /// Call this when playback starts to ensure we become the Now Playing app
    func activatePlayback() {
        do {
            let session = AVAudioSession.sharedInstance()
            try session.setActive(true, options: .notifyOthersOnDeactivation)
        } catch {
            print("🎵 NowPlayingManager: ❌ Failed to activate playback: \(error)")
        }
    }
    
    /// Sets the handler for remote commands
    /// We now support dynamic handler updates without re-registering commands
    func setCommandHandler(_ handler: @escaping CommandHandler) {
        self.commandHandler = handler
        print("🎵 NowPlayingManager: Command handler updated")
    }
    
    // Track pending update to handle race conditions
    private var pendingIdentifier: String?

    /// Stable string identifier derived from track metadata. Used for two purposes:
    ///   1. `pendingIdentifier` (dedup of in-flight artwork loads when the user
    ///      changes track twice quickly — drop the older fetch's result).
    ///   2. `MPNowPlayingInfoPropertyExternalContentIdentifier`, telling
    ///      `MediaRemote` "this is the *same* item across position-tick updates."
    ///      Without it, every `setNowPlayingInfo` call gets a fresh auto-assigned
    ///      ContentItemIdentifier, which causes `mediaremoted` to fire
    ///      `PlaybackQueueInvalidation` on every 500 ms tick — the bar visibly
    ///      thrashes and CarPlay treats each tick as a queue change.
    ///
    /// Duration is rounded to whole seconds because the value occasionally jitters
    /// between near-equivalent doubles across queue updates (e.g. 199.99987 vs.
    /// 200.000); we don't want that to look like a different item.
    private func contentIdentifier(
        title: String?,
        artist: String?,
        album: String?,
        duration: Double?
    ) -> String {
        let durStr = duration.map { String(format: "%.0f", $0) } ?? ""
        return "\(title ?? "")|\(artist ?? "")|\(album ?? "")|\(durStr)"
    }

    /// Updates the Now Playing info displayed in Control Center and Lock Screen.
    ///
    /// `duration` and `elapsedTime` are optional: nil means "value unknown — leave
    /// the corresponding `MPNowPlayingInfoCenter` field alone." The same-track path
    /// merges into the existing dict rather than replacing it, so a nil value here
    /// preserves whatever iOS last had instead of pinning a field to 0. See the
    /// position-tracker overlay in `MainDataSource` (Kotlin) for why upstream needs
    /// to send nils across transient gaps in server data — without this, a queue
    /// event arriving with `elapsed_time = null` (which MA does mid-pause) would
    /// reset the playback bar to 0.
    func updateNowPlayingInfo(
        title: String?,
        artist: String?,
        album: String?,
        artworkUrl: String?,
        duration: Double?,
        elapsedTime: Double?,
        playbackRate: Double
    ) {
        let newIdentifier = contentIdentifier(
            title: title, artist: artist, album: album, duration: duration
        )
        // New-track detection has to mirror what `contentIdentifier` keys on,
        // otherwise the lock-screen artwork pins to whichever cover loaded
        // first. Two tracks with the same title and artist on different albums
        // (a single vs. the album cut, a remaster vs. the original) are
        // legitimately different items and need a fresh artwork load.
        let isNewTrack = (
            title != currentTitle ||
            artist != currentArtist ||
            album != currentAlbum
        )

        // If it's the same track, merge into the existing dict so nil-valued
        // fields preserve iOS's last-known state.
        if !isNewTrack {
            self.applyMergedUpdate(
                title: title, artist: artist, album: album,
                artwork: self.cachedArtwork,
                duration: duration, elapsedTime: elapsedTime, playbackRate: playbackRate,
                contentId: newIdentifier,
                isNewTrack: false
            )
            return
        }

        // If it's a new track, we want to PREVENT FLICKER.
        // Strategy: Keep showing OLD metadata until NEW artwork is ready.

        print("🎵 NowPlayingManager: Detected new track. Waiting for artwork to prevent flicker...")

        // Mark this as the pending update
        self.pendingIdentifier = newIdentifier

        // IMMEDIATE PAUSE FEEDBACK:
        // If the user paused (rate == 0), update the OLD metadata's rate immediately
        // so the UI stops ticking/shows pause state, even while we load new art. We
        // intentionally only touch rate (and elapsed if known) — title/artist stay
        // on the previous track until artwork arrives.
        if abs(playbackRate) < 0.001 {
            var currentInfo = MPNowPlayingInfoCenter.default().nowPlayingInfo ?? [:]
            currentInfo[MPNowPlayingInfoPropertyPlaybackRate] = 0.0
            if let elapsed = elapsedTime {
                let dur = duration
                    ?? (currentInfo[MPMediaItemPropertyPlaybackDuration] as? Double)
                    ?? .greatestFiniteMagnitude
                currentInfo[MPNowPlayingInfoPropertyElapsedPlaybackTime] = max(0, min(elapsed, dur))
            }
            MPNowPlayingInfoCenter.default().nowPlayingInfo = currentInfo
        }

        // Cancel any previous pending load
        currentTask?.cancel()

        // If no artwork URL, update immediately with nil artwork
        guard let urlString = artworkUrl, let url = URL(string: urlString) else {
            self.cachedArtwork = nil
            self.updateCurrentState(title: title, artist: artist, album: album)
            self.applyMergedUpdate(
                title: title, artist: artist, album: album,
                artwork: nil,
                duration: duration, elapsedTime: elapsedTime, playbackRate: playbackRate,
                contentId: newIdentifier,
                isNewTrack: true
            )
            return
        }

        // Load artwork asynchronously
        self.currentTask = loadArtwork(from: url) { [weak self] artwork in
            guard let self = self else { return }

            // Check if this result is still relevant
            if self.pendingIdentifier != newIdentifier {
                print("🎵 NowPlayingManager: Ignoring stale artwork load for \(newIdentifier)")
                return
            }

            // On main thread, apply the FULL update (Text + New Art)
            DispatchQueue.main.async {
                self.cachedArtwork = artwork
                self.updateCurrentState(title: title, artist: artist, album: album)

                self.applyMergedUpdate(
                    title: title, artist: artist, album: album,
                    artwork: artwork,
                    duration: duration, elapsedTime: elapsedTime, playbackRate: playbackRate,
                    contentId: newIdentifier,
                    isNewTrack: true
                )
                print("🎵 NowPlayingManager: Artwork loaded. Metadata updated.")
            }
        }
    }

    private func updateCurrentState(title: String?, artist: String?, album: String?) {
        self.currentTitle = title
        self.currentArtist = artist
        self.currentAlbum = album
    }

    /// Merges new fields into the existing `MPNowPlayingInfoCenter.nowPlayingInfo`
    /// dict.
    ///
    /// When `isNewTrack` is `false` (same-track tick): nil-valued fields are
    /// skipped (preserving iOS's last-known value) and non-nil fields overwrite.
    /// This is the right semantics for position-tick / pause-rate updates where
    /// upstream legitimately doesn't know elapsed.
    ///
    /// When `isNewTrack` is `true`: previous-track fields are explicitly cleared
    /// before the merge — otherwise transitioning to a track that has, say, no
    /// artwork URL would leave the previous track's cover pinned in the dict
    /// because the skip-on-nil rule preserves it. The merge then writes whatever
    /// values the caller has; missing values become absent rather than
    /// previous-track holdovers.
    ///
    /// The stable `contentId` is always set so iOS doesn't auto-assign a fresh
    /// `ContentItemIdentifier` per call (which would make `mediaremoted` fire
    /// `PlaybackQueueInvalidation` on every position tick).
    private func applyMergedUpdate(
        title: String?,
        artist: String?,
        album: String?,
        artwork: MPMediaItemArtwork?,
        duration: Double?,
        elapsedTime: Double?,
        playbackRate: Double,
        contentId: String,
        isNewTrack: Bool
    ) {
        DispatchQueue.main.async {
            var info = MPNowPlayingInfoCenter.default().nowPlayingInfo ?? [:]

            if isNewTrack {
                // Wipe previous-track holdovers so a missing field on the new
                // track doesn't render as a pinned stale value.
                info.removeValue(forKey: MPMediaItemPropertyTitle)
                info.removeValue(forKey: MPMediaItemPropertyArtist)
                info.removeValue(forKey: MPMediaItemPropertyAlbumTitle)
                info.removeValue(forKey: MPMediaItemPropertyArtwork)
                info.removeValue(forKey: MPMediaItemPropertyPlaybackDuration)
                info.removeValue(forKey: MPNowPlayingInfoPropertyElapsedPlaybackTime)
            }

            if let title = title { info[MPMediaItemPropertyTitle] = title }
            if let artist = artist { info[MPMediaItemPropertyArtist] = artist }
            if let album = album { info[MPMediaItemPropertyAlbumTitle] = album }
            if let duration = duration { info[MPMediaItemPropertyPlaybackDuration] = duration }
            if let elapsed = elapsedTime {
                // Clamp against the freshly supplied duration if we have one,
                // otherwise the duration already cached on iOS, otherwise unbounded.
                let dur = duration
                    ?? (info[MPMediaItemPropertyPlaybackDuration] as? Double)
                    ?? .greatestFiniteMagnitude
                info[MPNowPlayingInfoPropertyElapsedPlaybackTime] = max(0, min(elapsed, dur))
            }
            info[MPNowPlayingInfoPropertyPlaybackRate] = playbackRate
            if let artwork = artwork {
                info[MPMediaItemPropertyArtwork] = artwork
            }
            info[MPNowPlayingInfoPropertyExternalContentIdentifier] = contentId

            MPNowPlayingInfoCenter.default().nowPlayingInfo = info
        }
    }
    
    /// Clears the Now Playing info
    func clearNowPlayingInfo() {
        print("🎵 NowPlayingManager: Clearing Now Playing info")
        DispatchQueue.main.async { [weak self] in
            MPNowPlayingInfoCenter.default().nowPlayingInfo = nil
            self?.cachedArtwork = nil
            self?.updateCurrentState(title: nil, artist: nil, album: nil)
        }
    }
    
    // MARK: - Debug
    private func printDebugState(_ context: String) {
        // ... (Keep existing debug logic if needed, or remove for brevity)
    }
    
    // MARK: - Private
    
    private func setupRemoteCommands() {
        let commandCenter = MPRemoteCommandCenter.shared()
        print("🎵 NowPlayingManager: Setting up remote commands (Once)")
        
        // Helper to attach targets
        func addTarget(_ command: MPRemoteCommand, cmd: String) {
            command.isEnabled = true
            command.addTarget { [weak self] _ in
                print("🎵 NowPlayingManager: Remote command received: \(cmd)")
                self?.commandHandler?(cmd)
                return .success
            }
        }
        
        addTarget(commandCenter.playCommand, cmd: "play")
        addTarget(commandCenter.pauseCommand, cmd: "pause")
        addTarget(commandCenter.togglePlayPauseCommand, cmd: "toggle_play_pause")
        addTarget(commandCenter.nextTrackCommand, cmd: "next")
        addTarget(commandCenter.previousTrackCommand, cmd: "previous")
        
        commandCenter.changePlaybackPositionCommand.isEnabled = true
        commandCenter.changePlaybackPositionCommand.addTarget { [weak self] event in
            guard let positionEvent = event as? MPChangePlaybackPositionCommandEvent else { return .commandFailed }
            self?.commandHandler?("seek:\(positionEvent.positionTime)")
            return .success
        }
        
        commandCenter.skipForwardCommand.isEnabled = false
        commandCenter.skipBackwardCommand.isEnabled = false
    }
    
    private func loadArtwork(from url: URL, completion: @escaping (MPMediaItemArtwork?) -> Void) -> URLSessionDataTask {
        let task = URLSession.shared.dataTask(with: url) { data, _, _ in
            guard let data = data, let image = UIImage(data: data) else {
                completion(nil)
                return
            }
            let artwork = MPMediaItemArtwork(boundsSize: image.size) { _ in image }
            completion(artwork)
        }
        task.resume()
        return task
    }
}
