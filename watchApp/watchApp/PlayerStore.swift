import Foundation
import MaWatchKit
import Observation

/// Binds the Kotlin controller to SwiftUI, and carries the playback position forward between
/// the server's reports.
///
/// `WatchPlayerController.observe` delivers on the main thread, so a snapshot is written
/// straight into observable state with no hop of its own.
@Observable
final class PlayerStore {
    private let controller = WatchPlayerController()
    private var subscription: WatchSubscription?

    /// The server and the token from the iPhone app. Held for the life of the store.
    @ObservationIgnored private var phoneConfig: PhoneConfigReceiver?

    /// Everything the screens render.
    private(set) var state: WatchUiState

    /// When the server last reported a position, on this watch's own clock.
    ///
    /// `queue_time_updated` arrives about once a second and is the whole position model; the
    /// anchor is what lets the progress bar keep moving between two reports, and through a
    /// burst of missed events, without ever consulting the server's wall clock — which a
    /// watch cannot be trusted to agree with.
    private var positionAnchor: Date = .now

    init() {
        state = controller.currentState
        phoneConfig = PhoneConfigReceiver { [weak self] config in
            self?.apply(config)
        }
    }

    /// Hands the server from the phone to the controller, which reconnects if it changed.
    private func apply(_ config: PhoneConfig) {
        switch config {
        case let .configured(host, port, isTls, basePath, token):
            controller.configure(
                host: host,
                port: Int32(port),
                isTls: isTls,
                basePath: basePath,
                token: token
            )
        case .remoteOnly:
            controller.setRemoteOnly()
        case .signedOut:
            controller.clearServer()
        case .unknown:
            break
        }
    }

    // MARK: - Lifecycle

    /// Opens the session and starts delivering snapshots. Calling it again does nothing.
    func start() {
        guard subscription == nil else { return }
        subscription = controller.observe { [weak self] snapshot in
            self?.apply(snapshot)
        }
        controller.start()
    }

    /// Closes the session. The watch holds no socket while the app is off screen.
    func stop() {
        subscription?.cancel()
        subscription = nil
        controller.stop()
    }

    private func apply(_ snapshot: WatchUiState) {
        let wasAt = state.nowPlaying?.positionSeconds
        let nowAt = snapshot.nowPlaying?.positionSeconds
        if wasAt != nowAt || state.nowPlaying?.title != snapshot.nowPlaying?.title {
            positionAnchor = .now
        }
        state = snapshot
    }

    // MARK: - What the screens read

    var nowPlaying: WatchNowPlaying? { state.nowPlaying }

    var selected: WatchPlayer? { state.selected }

    var isConnected: Bool { state.isConnected }

    var volume: Int? { state.selected?.volume?.intValue }

    var canSetVolume: Bool { state.selected?.canSetVolume ?? false }

    var canSkip: Bool { state.selected?.canNextPrevious ?? false }

    var isPlaying: Bool { state.nowPlaying?.isPlaying ?? false }

    /// The position the current track is at by [date], carried forward from the last report
    /// while the track is playing. A paused track stays where the server put it.
    func position(at date: Date) -> Double {
        guard let nowPlaying else { return 0 }
        guard nowPlaying.isPlaying else { return nowPlaying.positionSeconds }
        return nowPlaying.positionSeconds + max(0, date.timeIntervalSince(positionAnchor))
    }

    /// The fraction played by [date], or nil for a track with no known length — a radio
    /// stream — so the bar can stand down rather than sit at zero.
    func progress(at date: Date) -> Double? {
        guard let fraction = nowPlaying?.progressAt(seconds: position(at: date)) else { return nil }
        return Double(fraction.floatValue)
    }

    // MARK: - Commands

    func togglePlayPause() { controller.togglePlayPause() }

    func next() { controller.next() }

    func previous() { controller.previous() }

    func setVolume(_ level: Int) { controller.setVolume(level: Int32(level)) }

    func selectPlayer(_ playerId: String) { controller.selectPlayer(playerId: playerId) }

    func transferPlayback(to playerId: String) { controller.transferPlayback(playerId: playerId) }

    func playQueueItem(_ itemId: String) { controller.playQueueItem(itemId: itemId) }

    func removeQueueItem(_ itemId: String) { controller.removeQueueItem(itemId: itemId) }
}
