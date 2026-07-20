import Foundation
import MediaPlayer
import AVFoundation
import ComposeApp

/// Owns every write to Apple's system-media surfaces (Control Center, lock
/// screen, CarPlay's now-playing state) by subscribing to the three Kotlin
/// now-playing channels: track metadata, transport anchors, and queue modes.
///
/// Each surface has exactly one writer: the track and transport handlers feed
/// disjoint key groups of `NowPlayingInfoStore` (the sole `nowPlayingInfo`
/// assigner), and the modes handler is the sole writer of the shuffle/repeat
/// command state. Kotlin decides *what* changed; the handlers just render it.
///
/// Initialization happens in two idempotent phases because audio-session and
/// remote-command setup must not wait for the Kotlin graph:
///   1. Singleton creation (before `bootstrapKmp()`): session category and
///      remote-command targets.
///   2. `startObserving()` (right after `bootstrapKmp()`): channel
///      subscriptions.
///
/// Audio playback is handled by NativeAudioController via AudioQueue.
final class NowPlayingCoordinator {

    typealias CommandHandler = (String) -> Void

    static let shared = NowPlayingCoordinator()

    /// Sole writer of `MPNowPlayingInfoCenter.default().nowPlayingInfo`; all
    /// dictionary mutations in this class go through it.
    ///
    /// The sink logs every actual info-center assignment. The store coalesces
    /// writes, so these lines are the complete record of what the system UI
    /// was told — the first thing to read when the lock screen or media
    /// center misbehaves. Rare by construction (at most one per main-loop
    /// pass, and only when something changed), so info level costs nothing.
    private let infoStore = NowPlayingInfoStore(
        sink: { info in
            NativeLog.shared.info(
                tag: NowPlayingCoordinator.logTag,
                message: NowPlayingCoordinator.describeAssignment(info)
            )
            MPNowPlayingInfoCenter.default().nowPlayingInfo = info
        }
    )

    private static func describeAssignment(_ info: [String: Any]?) -> String {
        guard let info else { return "Info center assign: nil" }
        let title = info[MPMediaItemPropertyTitle] as? String ?? "-"
        let rate = (info[MPNowPlayingInfoPropertyPlaybackRate] as? Double)
            .map { String(format: "%.2f", $0) } ?? "-"
        let elapsed = (info[MPNowPlayingInfoPropertyElapsedPlaybackTime] as? Double)
            .map { String(format: "%.1f", $0) } ?? "-"
        return "Info center assign: \(info.count) keys title=\(title) rate=\(rate) elapsed=\(elapsed)"
    }

    private var commandHandler: CommandHandler?

    // MARK: - Channel subscriptions

    private var isObserving = false
    private var trackSubscription: Cancellable?
    private var transportSubscription: Cancellable?
    private var modesSubscription: Cancellable?

    // MARK: - Track / artwork state

    /// Content identity of the currently presented track. Drives new-track
    /// detection and proves artwork-load completions still belong on screen.
    private var currentTrackId: String?

    /// Non-nil while a new track's first write is deferred pending artwork.
    /// The previous track's metadata stays on screen until the art is ready.
    private var artworkWaitTrackId: String?

    /// One-entry artwork cache keyed by URL, so a same-art track change
    /// (album playback) presents instantly without a refetch.
    private var cachedArtworkUrl: String?
    private var cachedArtwork: MPMediaItemArtwork?
    private var currentArtworkLoad: Cancellable?

    /// Identifies the newest artwork request. Cancellation can't stop an
    /// already-dispatched completion, and the same track can issue several
    /// requests (corrected URLs), so completions compare against this token
    /// and drop themselves when superseded.
    private var artworkRequestToken: UInt64 = 0

    // MARK: - Transport state

    /// The latest transport value, re-stamped with this side's clock at
    /// arrival. Kept because a rebuilding track write wipes the store's
    /// transport anchor, which must then be restored (projected forward) —
    /// the channel dedupes steady playback, so no fresh anchor would come.
    ///
    /// `mediaItemId` is the anchor's correlation stamp: the track and
    /// transport channels have no cross-channel ordering guarantee, so an
    /// anchor is only ever rendered against the track it belongs to. An
    /// early arrival (transport before its track) waits here until the
    /// matching track write restores it; a stale one is never re-applied.
    private struct TransportArrival {
        var mediaItemId: String
        var elapsedSec: Double?
        var stampNs: UInt64
        var rate: Double
    }
    private var lastTransport: TransportArrival?

    // MARK: - Command profile / session state

    private var currentIsLongFormContent: Bool?
    private var currentLongFormSeekBackSeconds: Int64?
    private var currentLongFormSeekForwardSeconds: Int64?
    private var currentAudioSessionMode: AVAudioSession.Mode?

    // MARK: - Logging

    private static let logTag = "NowPlayingCoordinator"
    private func logInfo(_ message: String) { NativeLog.shared.info(tag: Self.logTag, message: message) }
    private func logError(_ message: String) { NativeLog.shared.error(tag: Self.logTag, message: message) }
    private func logDebug(_ message: String) { NativeLog.shared.debug(tag: Self.logTag, message: message) }

    init() {
        logDebug("Initializing")
        configureAudioSession()
        setupRemoteCommands() // Setup commands once
    }

    /// Second init phase: subscribes to the Kotlin now-playing channels.
    /// Requires the Kotlin graph, so it runs right after `bootstrapKmp()`.
    /// Idempotent because a CarPlay-only cold launch and a normal launch
    /// reach app init on different paths.
    func startObserving() {
        dispatchPrecondition(condition: .onQueue(.main))
        guard !isObserving else { return }
        isObserving = true
        logDebug("Starting channel observation")
        trackSubscription = KmpHelper.shared.observeNowPlayingTrack { [weak self] track in
            self?.handleTrack(track)
        }
        transportSubscription = KmpHelper.shared.observeNowPlayingTransport { [weak self] transport in
            self?.handleTransport(transport)
        }
        modesSubscription = KmpHelper.shared.observeNowPlayingModes { [weak self] modes in
            self?.handleModes(modes)
        }
    }

    // MARK: - Track handler

    /// Applies track metadata: hands the track key group to the store, runs
    /// the artwork load, and derives session mode + command profile from the
    /// long-form flag. A nil track means "nothing to present" — the store
    /// empties and profile/session return to defaults, while remote-command
    /// targets stay installed.
    private func handleTrack(_ track: NowPlayingTrack?) {
        dispatchPrecondition(condition: .onQueue(.main))
        guard let track else {
            clearPresentation()
            return
        }
        let isNewTrack = track.mediaItemId != currentTrackId
        currentTrackId = track.mediaItemId

        configureAudioSession(mode: track.isLongFormContent ? .spokenAudio : .default)
        updateRemoteCommandMode(isLongFormContent: track.isLongFormContent)

        // No artwork: present immediately. Rebuild even for a same-identity
        // track — this write carries the complete presentation state, and a
        // merge would keep stale keys (e.g. a live stream whose artwork URL
        // just disappeared would keep showing the old art).
        guard let urlString = track.artworkUrl, !urlString.isEmpty else {
            supersedeArtworkLoad()
            artworkWaitTrackId = nil
            cachedArtworkUrl = nil
            cachedArtwork = nil
            applyTrackKeys(track, artwork: nil, rebuildingGroup: true)
            return
        }

        // Cache hit (album playback reusing one cover): present immediately.
        // Complete state, so rebuild (see the no-artwork branch).
        if urlString == cachedArtworkUrl {
            supersedeArtworkLoad()
            artworkWaitTrackId = nil
            applyTrackKeys(track, artwork: cachedArtwork, rebuildingGroup: true)
            return
        }

        // Artwork must be fetched. For a new track — or a correction arriving
        // while that track's first write is still deferred — keep the previous
        // metadata on screen until the art is ready, so the system UI never
        // flickers through a text-only frame.
        let deferWrite = isNewTrack || artworkWaitTrackId == track.mediaItemId
        if deferWrite {
            artworkWaitTrackId = track.mediaItemId
            logDebug("New track \(track.mediaItemId) — deferring write until artwork is ready")
        } else {
            // Same already-presented track with a changed artwork URL:
            // metadata now, art when it lands.
            applyTrackKeys(track, artwork: nil, rebuildingGroup: false)
        }

        supersedeArtworkLoad()
        let token = artworkRequestToken
        currentArtworkLoad = loadArtwork(urlString: urlString) { [weak self] artwork in
            guard let self = self else { return }
            // Only the newest request may present its result — anything else
            // is a superseded load whose cancellation arrived too late.
            guard self.artworkRequestToken == token else {
                self.logDebug("Ignoring superseded artwork for \(track.mediaItemId)")
                return
            }
            // Cache only successes. A nil result may be a transient fetch
            // failure; caching it would suppress retries for this URL for
            // the rest of the session (e.g. a whole album with shared art).
            if artwork != nil {
                self.cachedArtworkUrl = urlString
                self.cachedArtwork = artwork
            }
            self.artworkWaitTrackId = nil
            // Final write for this track: full state, so rebuild — clears any
            // interim keys (or a previous track's) that no longer apply.
            self.applyTrackKeys(track, artwork: artwork, rebuildingGroup: true)
            self.logDebug("Artwork loaded, metadata updated")
        }
    }

    /// Cancels any in-flight artwork load and invalidates its completion.
    private func supersedeArtworkLoad() {
        artworkRequestToken &+= 1
        currentArtworkLoad?.cancel()
        currentArtworkLoad = nil
    }

    private func applyTrackKeys(_ track: NowPlayingTrack, artwork: MPMediaItemArtwork?, rebuildingGroup: Bool) {
        logDebug(
            "Track keys applied: id=\(track.mediaItemId) rebuild=\(rebuildingGroup) " +
                "artwork=\(artwork != nil) restoreCandidate=\(lastTransport?.mediaItemId ?? "none")"
        )
        infoStore.setTrackKeys(
            title: track.title,
            artist: track.artist,
            album: track.album,
            artwork: artwork,
            duration: track.duration?.doubleValue,
            contentId: track.mediaItemId,
            replacingExisting: rebuildingGroup
        )
        // Rebuilding wipes the store's transport anchor. Restore the latest
        // one, projected forward on this side's clock so the bar lands where
        // playback actually is — the channel won't re-send it (steady
        // playback dedupes as "same anchor"). Only an anchor belonging to
        // this track is restored: a previous track's would render a wrong
        // position, and this track's own anchor always arrives (identity
        // change forces a fresh emission), briefly leaving the position
        // unset rather than wrong.
        if rebuildingGroup, let last = lastTransport, last.mediaItemId == track.mediaItemId {
            let elapsed = last.elapsedSec.map {
                $0 + Double(NowPlayingInfoStore.continuousNowNs() &- last.stampNs) / 1_000_000_000.0 * last.rate
            }
            infoStore.setTransport(elapsedSec: elapsed, rate: last.rate)
        }
    }

    private func clearPresentation() {
        logInfo("Clearing Now Playing presentation")
        currentTrackId = nil
        artworkWaitTrackId = nil
        supersedeArtworkLoad()
        cachedArtworkUrl = nil
        cachedArtwork = nil
        lastTransport = nil
        infoStore.clear()
        configureAudioSession(mode: .default)
        updateRemoteCommandMode(isLongFormContent: false)
    }

    // MARK: - Transport handler

    private func handleTransport(_ transport: NowPlayingTransport?) {
        dispatchPrecondition(condition: .onQueue(.main))
        guard let transport else {
            // The track channel going null owns the clear; nothing to write.
            lastTransport = nil
            return
        }
        // Kotlin's anchorMs never crosses the bridge: it is millisecond-
        // truncated and the K/N boundary adds delivery latency, so re-stamp
        // arrival with this side's clock — same main-loop turn, so the skew
        // is negligible and the projection math stays in one time base.
        let arrival = TransportArrival(
            mediaItemId: transport.mediaItemId,
            elapsedSec: transport.elapsedSec?.doubleValue,
            stampNs: NowPlayingInfoStore.continuousNowNs(),
            rate: transport.rate
        )
        lastTransport = arrival
        guard arrival.mediaItemId == currentTrackId else {
            // The channels are independent, so this anchor's track hasn't
            // been presented yet (early) or is no longer current (stale).
            // Cached above: if the matching track write comes, its rebuild
            // restores this anchor; if not, it is simply never rendered.
            // Info level: this only fires on out-of-order channel delivery,
            // so each line is a meaningful anomaly marker, never chatter.
            logInfo("Transport for \(arrival.mediaItemId) held — presenting \(currentTrackId ?? "none")")
            return
        }
        logDebug(
            "Transport applied: id=\(arrival.mediaItemId) " +
                "elapsed=\(arrival.elapsedSec.map { String(format: "%.1f", $0) } ?? "-") " +
                "rate=\(arrival.rate) deferredRateOnly=\(artworkWaitTrackId != nil)"
        )
        if artworkWaitTrackId != nil {
            // This track's first write is still deferred pending artwork, so
            // the previous track's metadata is on screen; flip the rate
            // (instant pause/play feedback) but keep the old position anchor.
            // The real anchor lands with the deferred track write's restore.
            infoStore.setTransport(elapsedSec: nil, rate: arrival.rate)
        } else {
            infoStore.setTransport(elapsedSec: arrival.elapsedSec, rate: arrival.rate)
        }
    }

    // MARK: - Modes handler

    /// Sole writer of the shuffle/repeat command state. CarPlay renders its
    /// buttons' selected state from `currentShuffleType`/`currentRepeatType`,
    /// so writing here keeps every surface in agreement.
    private func handleModes(_ modes: NowPlayingModes?) {
        dispatchPrecondition(condition: .onQueue(.main))
        let commandCenter = MPRemoteCommandCenter.shared()
        guard let modes else {
            commandCenter.changeShuffleModeCommand.isEnabled = false
            commandCenter.changeRepeatModeCommand.isEnabled = false
            // Reset selected state too: a disabled control can still render
            // its last selection on some surfaces (CarPlay).
            commandCenter.changeShuffleModeCommand.currentShuffleType = .off
            commandCenter.changeRepeatModeCommand.currentRepeatType = .off
            return
        }
        commandCenter.changeShuffleModeCommand.currentShuffleType = modes.shuffleEnabled ? .items : .off
        if modes.repeatMode == RepeatMode.one {
            commandCenter.changeRepeatModeCommand.currentRepeatType = .one
        } else if modes.repeatMode == RepeatMode.all {
            commandCenter.changeRepeatModeCommand.currentRepeatType = .all
        } else {
            // OFF, or the player reports no repeat mode at all.
            commandCenter.changeRepeatModeCommand.currentRepeatType = .off
        }
        commandCenter.changeShuffleModeCommand.isEnabled = modes.togglesEnabled
        commandCenter.changeRepeatModeCommand.isEnabled = modes.togglesEnabled
        logDebug(
            "Modes applied: shuffle=\(modes.shuffleEnabled) repeat=\(modes.repeatMode?.name ?? "none") togglesEnabled=\(modes.togglesEnabled)"
        )
    }

    // MARK: - Audio session

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
            // become the Now Playing app. Keep the mode the track handler chose
            // (.spokenAudio for long-form content) rather than forcing .default,
            // which would silently clobber it and desync `currentAudioSessionMode`.
            try session.setCategory(.playback, mode: currentAudioSessionMode ?? .default, options: [])
            try session.setActive(true, options: .notifyOthersOnDeactivation)
            // Info level: fires only when playback (re)starts, and an absent
            // line here is the tell when iOS shows "Not Playing" despite
            // healthy info-center assignments.
            logInfo("Playback activated: mode=\((currentAudioSessionMode ?? .default).rawValue)")
        } catch {
            logError("Failed to activate playback: \(error)")
        }
    }

    // MARK: - Remote commands

    /// Sets the handler for remote commands
    /// We now support dynamic handler updates without re-registering commands
    func setCommandHandler(_ handler: @escaping CommandHandler) {
        self.commandHandler = handler
        logDebug("Command handler updated")
    }

    private func setupRemoteCommands() {
        let commandCenter = MPRemoteCommandCenter.shared()
        logDebug("Setting up remote commands (once)")

        // Helper to attach targets
        func addTarget(_ command: MPRemoteCommand, cmd: String, enabled: Bool = true) {
            command.isEnabled = enabled
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

        // Shuffle/repeat: targets only dispatch — state rendering (selected
        // type, enablement) belongs to the modes handler alone. Disabled
        // until a queue actually provides modes.
        addTarget(commandCenter.changeShuffleModeCommand, cmd: "toggle_shuffle", enabled: false)
        addTarget(commandCenter.changeRepeatModeCommand, cmd: "toggle_repeat", enabled: false)

        commandCenter.changePlaybackPositionCommand.isEnabled = true
        commandCenter.changePlaybackPositionCommand.addTarget { [weak self] event in
            guard let positionEvent = event as? MPChangePlaybackPositionCommandEvent else { return .commandFailed }
            // Floor to the same whole-second target KMP freezes at; otherwise the
            // lock-screen thumb can correct backward when the KMP anchor lands.
            let seekPosition = positionEvent.positionTime.rounded(.down)
            self?.logInfo("Remote seek command received: \(seekPosition)")
            self?.commandHandler?("seek:\(seekPosition)")
            return .success
        }
    }

    /// Expands the abstract skip commands into concrete `seek_by:` offsets.
    /// The intervals are set from `MainDataSource.init` (its first statement),
    /// which runs during `bootstrapKmp()` — before any UI exists — so the
    /// bare-string fallback below is unreachable in practice; if it ever
    /// fires, Kotlin logs the unmapped command and drops it.
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

    /// Swaps the transport-command profile: music uses prev/next, long-form
    /// content uses skip ±N. Never touches the shuffle/repeat commands —
    /// those belong to the modes handler.
    private func updateRemoteCommandMode(isLongFormContent: Bool) {
        guard currentIsLongFormContent != isLongFormContent else { return }
        currentIsLongFormContent = isLongFormContent

        let commandCenter = MPRemoteCommandCenter.shared()
        commandCenter.previousTrackCommand.isEnabled = !isLongFormContent
        commandCenter.nextTrackCommand.isEnabled = !isLongFormContent
        commandCenter.skipBackwardCommand.isEnabled = isLongFormContent
        commandCenter.skipForwardCommand.isEnabled = isLongFormContent
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

    // MARK: - Artwork loading

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
