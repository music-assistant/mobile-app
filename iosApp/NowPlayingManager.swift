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
    
    /// Updates the Now Playing info displayed in Control Center and Lock Screen
    func updateNowPlayingInfo(
        title: String?,
        artist: String?,
        album: String?,
        artworkUrl: String?,
        duration: Double,
        elapsedTime: Double,
        playbackRate: Double
    ) {
        let newIdentifier = "\(title ?? "")-\(artist ?? "")-\(album ?? "")"
        let isNewTrack = (title != currentTitle || artist != currentArtist)
        
        // If it's the same track, update immediately with existing/cached art
        if !isNewTrack {
            self.performUpdate(
                title: title, artist: artist, album: album,
                artwork: self.cachedArtwork,
                duration: duration, elapsedTime: elapsedTime, playbackRate: playbackRate
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
        // so the UI stops ticking/shows pause state, even while we load new art.
        if abs(playbackRate) < 0.001 {
             var currentInfo = MPNowPlayingInfoCenter.default().nowPlayingInfo ?? [:]
             currentInfo[MPNowPlayingInfoPropertyPlaybackRate] = 0.0
             currentInfo[MPNowPlayingInfoPropertyElapsedPlaybackTime] = max(0, min(elapsedTime, duration))
             MPNowPlayingInfoCenter.default().nowPlayingInfo = currentInfo
        }
        
        // Cancel any previous pending load
        currentTask?.cancel()
        
        // If no artwork URL, update immediately with nil artwork
        guard let urlString = artworkUrl, let url = URL(string: urlString) else {
            self.cachedArtwork = nil
            self.updateCurrentState(title: title, artist: artist, album: album)
            self.performUpdate(
                title: title, artist: artist, album: album,
                artwork: nil,
                duration: duration, elapsedTime: elapsedTime, playbackRate: playbackRate
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
                
                self.performUpdate(
                    title: title, artist: artist, album: album,
                    artwork: artwork,
                    duration: duration, elapsedTime: elapsedTime, playbackRate: playbackRate
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
    
    private func performUpdate(
        title: String?,
        artist: String?,
        album: String?,
        artwork: MPMediaItemArtwork?,
        duration: Double,
        elapsedTime: Double,
        playbackRate: Double
    ) {
        DispatchQueue.main.async {
            var nowPlayingInfo = [String: Any]()
            
            if let title = title { nowPlayingInfo[MPMediaItemPropertyTitle] = title }
            if let artist = artist { nowPlayingInfo[MPMediaItemPropertyArtist] = artist }
            if let album = album { nowPlayingInfo[MPMediaItemPropertyAlbumTitle] = album }
            
            nowPlayingInfo[MPMediaItemPropertyPlaybackDuration] = duration
            let clampedElapsed = max(0, min(elapsedTime, duration))
            nowPlayingInfo[MPNowPlayingInfoPropertyElapsedPlaybackTime] = clampedElapsed
            nowPlayingInfo[MPNowPlayingInfoPropertyPlaybackRate] = playbackRate
            
            if let artwork = artwork {
                nowPlayingInfo[MPMediaItemPropertyArtwork] = artwork
            }
            
            MPNowPlayingInfoCenter.default().nowPlayingInfo = nowPlayingInfo
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
