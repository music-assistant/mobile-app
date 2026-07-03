import Foundation
import MediaPlayer
import AVFoundation
import CarPlay
import ComposeApp

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
    private var currentArtworkLoad: Cancellable?

    // Track current metadata state to determine if we need to fetch new artwork
    private var currentTitle: String?
    private var currentArtist: String?
    private var currentAlbum: String?
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
        setupRemoteCommands()
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

    private struct RemoteCommandState: Equatable {
        let isLongFormContent: Bool
        let shuffleEnabled: Bool
        let repeatMode: RepeatMode?
    }

    private var pendingIdentifier: String?
    private var currentRemoteCommandState: RemoteCommandState?

    /// Stable item identity for artwork deduping and iOS Now Playing updates.
    private func contentIdentifier(
        title: String?,
        artist: String?,
        album: String?,
        duration: Double?
    ) -> String {
        let durStr = duration.map { String(format: "%.0f", $0) } ?? ""
        return "\(title ?? "")|\(artist ?? "")|\(album ?? "")|\(durStr)"
    }

    /// Updates Control Center, Lock Screen, and CarPlay Now Playing metadata.
    func updateNowPlayingInfo(
        title: String?,
        artist: String?,
        album: String?,
        artworkUrl: String?,
        duration: Double?,
        elapsedTime: Double?,
        playbackRate: Double,
        isLongFormContent: Bool,
        shuffleEnabled: Bool,
        repeatMode: RepeatMode?
    ) {
        configureAudioSession(mode: isLongFormContent ? .spokenAudio : .default)
        updateRemoteCommands(
            isLongFormContent: isLongFormContent,
            shuffleEnabled: shuffleEnabled,
            repeatMode: repeatMode
        )

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

        // Write metadata immediately; artwork may arrive later.
        self.pendingIdentifier = newIdentifier
        currentArtworkLoad?.cancel()
        currentArtworkLoad = nil
        self.cachedArtwork = nil
        self.updateCurrentState(title: title, artist: artist, album: album)
        self.applyMergedUpdate(
            title: title, artist: artist, album: album,
            artwork: nil,
            duration: duration, elapsedTime: elapsedTime, playbackRate: playbackRate,
            contentId: newIdentifier,
            isNewTrack: true
        )

        guard let urlString = artworkUrl, !urlString.isEmpty else { return }

        // Load artwork asynchronously via KmpHelper (handles mawebrtc:// + http(s)://)
        self.currentArtworkLoad = loadArtwork(urlString: urlString) { [weak self] artwork in
            guard let self = self else { return }

            if self.pendingIdentifier != newIdentifier {
                logDebug("Ignoring stale artwork load for \(newIdentifier)")
                return
            }

            DispatchQueue.main.async {
                self.cachedArtwork = artwork
                self.applyMergedUpdate(
                    title: title, artist: artist, album: album,
                    artwork: artwork,
                    duration: nil, elapsedTime: nil, playbackRate: nil,
                    contentId: newIdentifier,
                    isNewTrack: false
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

    /// Merges Now Playing fields while preserving same-track values that are temporarily unknown.
    private func applyMergedUpdate(
        title: String?,
        artist: String?,
        album: String?,
        artwork: MPMediaItemArtwork?,
        duration: Double?,
        elapsedTime: Double?,
        playbackRate: Double?,
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
            if let playbackRate = playbackRate {
                info[MPNowPlayingInfoPropertyPlaybackRate] = playbackRate
            }
            if let artwork = artwork {
                info[MPMediaItemPropertyArtwork] = artwork
            }
            info[MPNowPlayingInfoPropertyExternalContentIdentifier] = contentId

            MPNowPlayingInfoCenter.default().nowPlayingInfo = info
        }
    }

    /// Clears the Now Playing info
    func clearNowPlayingInfo() {
        logInfo("Clearing Now Playing info")
        configureAudioSession(mode: .default)
        DispatchQueue.main.async { [weak self] in
            MPNowPlayingInfoCenter.default().nowPlayingInfo = nil
            self?.cachedArtwork = nil
            self?.updateCurrentState(title: nil, artist: nil, album: nil)
            self?.updateRemoteCommands(
                isLongFormContent: false,
                shuffleEnabled: false,
                repeatMode: nil
            )
        }
    }


    // MARK: - Private

    private func applyRemoteSeekPosition(_ position: TimeInterval) {
        DispatchQueue.main.async {
            var info = MPNowPlayingInfoCenter.default().nowPlayingInfo ?? [:]
            let duration = (info[MPMediaItemPropertyPlaybackDuration] as? Double) ?? .greatestFiniteMagnitude
            info[MPNowPlayingInfoPropertyElapsedPlaybackTime] = max(0, min(position, duration))
            // Stop iOS interpolation immediately; KMP will publish the confirmed rate.
            info[MPNowPlayingInfoPropertyPlaybackRate] = 0.0
            MPNowPlayingInfoCenter.default().nowPlayingInfo = info
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
        addTarget(commandCenter.changeShuffleModeCommand, cmd: "toggle_shuffle")
        addTarget(commandCenter.changeRepeatModeCommand, cmd: "toggle_repeat")
        updateRemoteCommands(
            isLongFormContent: false,
            shuffleEnabled: false,
            repeatMode: nil
        )

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

    private func updateRemoteCommands(
        isLongFormContent: Bool,
        shuffleEnabled: Bool,
        repeatMode: RepeatMode?
    ) {
        DispatchQueue.main.async { [weak self] in
            guard let self = self else { return }
            let state = RemoteCommandState(
                isLongFormContent: isLongFormContent,
                shuffleEnabled: shuffleEnabled,
                repeatMode: repeatMode
            )
            guard self.currentRemoteCommandState != state else { return }
            self.currentRemoteCommandState = state

            let commandCenter = MPRemoteCommandCenter.shared()

            if isLongFormContent {
                self.configureLongFormRemoteCommands(commandCenter)
            } else {
                self.configureMusicRemoteCommands(
                    commandCenter,
                    shuffleEnabled: shuffleEnabled,
                    repeatMode: repeatMode
                )
            }
        }
    }

    private func configureLongFormRemoteCommands(_ commandCenter: MPRemoteCommandCenter) {
        commandCenter.previousTrackCommand.isEnabled = false
        commandCenter.nextTrackCommand.isEnabled = false
        commandCenter.skipBackwardCommand.isEnabled = true
        commandCenter.skipForwardCommand.isEnabled = true
        commandCenter.changeShuffleModeCommand.isEnabled = false
        commandCenter.changeRepeatModeCommand.isEnabled = false
        CPNowPlayingTemplate.shared.updateNowPlayingButtons([])
    }

    private func configureMusicRemoteCommands(
        _ commandCenter: MPRemoteCommandCenter,
        shuffleEnabled: Bool,
        repeatMode: RepeatMode?
    ) {
        commandCenter.previousTrackCommand.isEnabled = true
        commandCenter.nextTrackCommand.isEnabled = true
        commandCenter.skipBackwardCommand.isEnabled = false
        commandCenter.skipForwardCommand.isEnabled = false
        commandCenter.changeShuffleModeCommand.isEnabled = true
        commandCenter.changeShuffleModeCommand.currentShuffleType = shuffleEnabled ? .items : .off
        commandCenter.changeRepeatModeCommand.isEnabled = true
        commandCenter.changeRepeatModeCommand.currentRepeatType = Self.remoteRepeatType(repeatMode)
        CPNowPlayingTemplate.shared.updateNowPlayingButtons(
            carPlayMusicButtons(shuffleEnabled: shuffleEnabled, repeatMode: repeatMode)
        )
    }

    private func carPlayMusicButtons(
        shuffleEnabled: Bool,
        repeatMode: RepeatMode?
    ) -> [CPNowPlayingButton] {
        let shuffleButton = CPNowPlayingShuffleButton { [weak self] _ in
            self?.commandHandler?("toggle_shuffle")
        }
        shuffleButton.isSelected = shuffleEnabled

        let repeatButton = CPNowPlayingRepeatButton { [weak self] _ in
            self?.commandHandler?("toggle_repeat")
        }
        repeatButton.isSelected = repeatMode != nil && repeatMode != .off

        return [shuffleButton, repeatButton]
    }

    private static func remoteRepeatType(_ repeatMode: RepeatMode?) -> MPRepeatType {
        switch repeatMode {
        case .all: return .all
        case .one: return .one
        default: return .off
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
