import Foundation
import MediaPlayer
import AVFoundation
import ComposeApp

/// Manages iOS Now Playing info (Control Center, Lock Screen)
/// and remote command handling (play/pause/next/prev buttons)
///
/// Audio playback is handled by NativeAudioController via AudioQueue.
class NowPlayingManager {

    typealias CommandHandler = (String) -> Void

    static let shared = NowPlayingManager()

    /// Sole writer of `MPNowPlayingInfoCenter.default().nowPlayingInfo`; all
    /// dictionary mutations in this class go through it.
    private let infoStore = NowPlayingInfoStore()

    private var commandHandler: CommandHandler?

    // State for caching and flicker prevention
    private var cachedArtwork: MPMediaItemArtwork?
    private var currentArtworkLoad: Cancellable?

    // Track current metadata state to determine if we need to fetch new artwork
    private var currentTitle: String?
    private var currentArtist: String?
    private var currentAlbum: String?
    private var currentIsLongFormContent: Bool?
    private var currentLongFormSeekBackSeconds: Int64?
    private var currentLongFormSeekForwardSeconds: Int64?
    private var currentAudioSessionMode: AVAudioSession.Mode?

    // MARK: - Logging
    private static let logTag = "NowPlayingManager"
    private func logInfo(_ message: String) { NativeLog.shared.info(tag: Self.logTag, message: message) }
    private func logError(_ message: String) { NativeLog.shared.error(tag: Self.logTag, message: message) }
    private func logDebug(_ message: String) { NativeLog.shared.debug(tag: Self.logTag, message: message) }

    init() {
        logDebug("Initializing")
        configureAudioSession()
        setupRemoteCommands() // Setup commands once
    }

    /// Sets the category only — does NOT activate. Activation interrupts other
    /// apps, so doing it at launch claims audio from whatever is already playing.
    /// Deferred to `activatePlayback()`, driven by real playback.
    private func configureAudioSession(mode: AVAudioSession.Mode = .default) {
        guard currentAudioSessionMode != mode else { return }
        do {
            let session = AVAudioSession.sharedInstance()
            try session.setCategory(.playback, mode: mode, options: [])
            currentAudioSessionMode = mode
            logDebug("Audio session category configured: mode=\(mode.rawValue)")
        } catch {
            logError("Failed to configure audio session: \(error)")
        }
    }

    /// Call this when playback starts to ensure we become the Now Playing app
    func activatePlayback() {
        do {
            let session = AVAudioSession.sharedInstance()
            // Re-assert exclusive (non-mixing) playback before activating. The
            // volume-button observer may have switched the shared session to
            // .mixWithOthers while only a remote player was being viewed; when
            // local playback actually starts we must reclaim exclusive focus so we
            // become the Now Playing app.
            try session.setCategory(.playback, mode: .default, options: [])
            try session.setActive(true, options: .notifyOthersOnDeactivation)
        } catch {
            logError("Failed to activate playback: \(error)")
        }
    }

    /// Sets the handler for remote commands
    /// We now support dynamic handler updates without re-registering commands
    func setCommandHandler(_ handler: @escaping CommandHandler) {
        self.commandHandler = handler
        logDebug("Command handler updated")
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
        playbackRate: Double,
        isLongFormContent: Bool
    ) {
        configureAudioSession(mode: isLongFormContent ? .spokenAudio : .default)
        updateRemoteCommandMode(isLongFormContent: isLongFormContent)

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

        logDebug("Detected new track — waiting for artwork to prevent flicker")

        // Mark this as the pending update
        self.pendingIdentifier = newIdentifier

        // IMMEDIATE PAUSE FEEDBACK:
        // If the user paused (rate == 0), update the OLD metadata's rate immediately
        // so the UI stops ticking/shows pause state, even while we load new art. We
        // intentionally only touch rate (and elapsed if known) — title/artist stay
        // on the previous track until artwork arrives.
        if abs(playbackRate) < 0.001 {
            DispatchQueue.main.async { [weak self] in
                self?.infoStore.setTransport(elapsedSec: elapsedTime, rate: 0.0)
            }
        }

        // Cancel any previous pending load
        currentArtworkLoad?.cancel()
        currentArtworkLoad = nil

        // If no artwork URL, update immediately with nil artwork
        guard let urlString = artworkUrl, !urlString.isEmpty else {
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

        // Load artwork asynchronously via KmpHelper (handles mawebrtc:// + http(s)://)
        self.currentArtworkLoad = loadArtwork(urlString: urlString) { [weak self] artwork in
            guard let self = self else { return }

            // Check if this result is still relevant
            if self.pendingIdentifier != newIdentifier {
                logDebug("Ignoring stale artwork load for \(newIdentifier)")
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
                self.logDebug("Artwork loaded, metadata updated")
            }
        }
    }

    private func updateCurrentState(title: String?, artist: String?, album: String?) {
        self.currentTitle = title
        self.currentArtist = artist
        self.currentAlbum = album
    }

    /// One update = track-group + transport-group write. `isNewTrack` rebuilds the
    /// track group (else nil fields keep last-known values, which would pin stale
    /// artwork across a track change). A stable `contentId` prevents mediaremoted
    /// firing `PlaybackQueueInvalidation` on every position tick.
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
        DispatchQueue.main.async { [weak self] in
            guard let self = self else { return }
            self.infoStore.setTrackKeys(
                title: title,
                artist: artist,
                album: album,
                artwork: artwork,
                duration: duration,
                contentId: contentId,
                replacingExisting: isNewTrack
            )
            self.infoStore.setTransport(elapsedSec: elapsedTime, rate: playbackRate)
        }
    }

    /// Clears the Now Playing info
    func clearNowPlayingInfo() {
        logInfo("Clearing Now Playing info")
        configureAudioSession(mode: .default)
        DispatchQueue.main.async { [weak self] in
            self?.infoStore.clear()
            self?.cachedArtwork = nil
            self?.updateCurrentState(title: nil, artist: nil, album: nil)
            self?.updateRemoteCommandMode(isLongFormContent: false)
        }
    }

    // MARK: - Private

    private func applyRemoteSeekPosition(_ position: TimeInterval) {
        DispatchQueue.main.async { [weak self] in
            // Rate 0 stops iOS interpolation immediately; KMP will publish the
            // confirmed rate once the seek lands.
            self?.infoStore.setTransport(elapsedSec: position, rate: 0.0)
        }
    }

    private func setupRemoteCommands() {
        let commandCenter = MPRemoteCommandCenter.shared()
        logDebug("Setting up remote commands (once)")

        // Helper to attach targets
        func addTarget(_ command: MPRemoteCommand, cmd: String) {
            command.isEnabled = true
            command.addTarget { [weak self] _ in
                guard let self = self else { return .commandFailed }
                let resolvedCommand = self.resolveRemoteCommand(cmd)
                self.logDebug("Remote command received: \(resolvedCommand)")
                self.commandHandler?(resolvedCommand)
                return .success
            }
        }

        addTarget(commandCenter.playCommand, cmd: "play")
        addTarget(commandCenter.pauseCommand, cmd: "pause")
        addTarget(commandCenter.togglePlayPauseCommand, cmd: "toggle_play_pause")
        addTarget(commandCenter.nextTrackCommand, cmd: "next")
        addTarget(commandCenter.previousTrackCommand, cmd: "previous")

        addTarget(commandCenter.skipBackwardCommand, cmd: "seek_back")
        addTarget(commandCenter.skipForwardCommand, cmd: "seek_forward")
        updateRemoteCommandMode(isLongFormContent: false)

        commandCenter.changePlaybackPositionCommand.isEnabled = true
        commandCenter.changePlaybackPositionCommand.addTarget { [weak self] event in
            guard let positionEvent = event as? MPChangePlaybackPositionCommandEvent else { return .commandFailed }
            // Floor to the same whole-second target KMP freezes at; otherwise the
            // lock-screen thumb can correct backward when the KMP snapshot lands.
            let seekPosition = positionEvent.positionTime.rounded(.down)
            self?.logInfo("Remote seek command received: \(seekPosition)")
            self?.applyRemoteSeekPosition(seekPosition)
            self?.commandHandler?("seek:\(seekPosition)")
            return .success
        }
    }

    private func resolveRemoteCommand(_ command: String) -> String {
        switch command {
        case "seek_back":
            guard let seconds = currentLongFormSeekBackSeconds else { return command }
            return "seek_by:-\(seconds)"
        case "seek_forward":
            guard let seconds = currentLongFormSeekForwardSeconds else { return command }
            return "seek_by:\(seconds)"
        default:
            return command
        }
    }

    private func updateRemoteCommandMode(isLongFormContent: Bool) {
        DispatchQueue.main.async { [weak self] in
            guard let self = self,
                  self.currentIsLongFormContent != isLongFormContent else { return }
            self.currentIsLongFormContent = isLongFormContent

            let commandCenter = MPRemoteCommandCenter.shared()
            commandCenter.previousTrackCommand.isEnabled = !isLongFormContent
            commandCenter.nextTrackCommand.isEnabled = !isLongFormContent
            commandCenter.skipBackwardCommand.isEnabled = isLongFormContent
            commandCenter.skipForwardCommand.isEnabled = isLongFormContent
        }
    }

    func setLongFormSeekIntervals(backSeconds: Int64, forwardSeconds: Int64) {
        DispatchQueue.main.async { [weak self] in
            guard let self = self,
                  self.currentLongFormSeekBackSeconds != backSeconds ||
                  self.currentLongFormSeekForwardSeconds != forwardSeconds else { return }
            self.currentLongFormSeekBackSeconds = backSeconds
            self.currentLongFormSeekForwardSeconds = forwardSeconds

            let commandCenter = MPRemoteCommandCenter.shared()
            commandCenter.skipBackwardCommand.preferredIntervals = [NSNumber(value: backSeconds)]
            commandCenter.skipForwardCommand.preferredIntervals = [NSNumber(value: forwardSeconds)]
        }
    }

    private func loadArtwork(urlString: String, completion: @escaping (MPMediaItemArtwork?) -> Void) -> Cancellable {
        return KmpHelper.shared.loadArtworkBytes(urlString: urlString) { data in
            guard let data = data as Data?, let image = UIImage(data: data) else {
                completion(nil)
                return
            }
            let artwork = MPMediaItemArtwork(boundsSize: image.size) { _ in image }
            completion(artwork)
        }
    }
}
