import Foundation
import MediaPlayer

/// Sole writer of `MPNowPlayingInfoCenter.default().nowPlayingInfo`.
///
/// Two disjoint key groups: track metadata and transport (elapsed/rate). Position is
/// held as an anchor and projected at flush; mutations coalesce into one dictionary
/// assignment per main-loop pass. Main-thread-only (dispatchPrecondition-enforced).
final class NowPlayingInfoStore {

    /// `elapsedSec` nil = position unknown; rate is still published.
    private struct TransportAnchor {
        var elapsedSec: Double?
        var anchorNs: UInt64
        var rate: Double
    }

    /// Monotonic ns that keep ticking during device sleep (unlike CLOCK_UPTIME_RAW).
    static func continuousNowNs() -> UInt64 {
        clock_gettime_nsec_np(CLOCK_MONOTONIC)
    }

    private static let nsPerSecond = 1_000_000_000.0

    private let now: () -> UInt64
    private let scheduleFlush: (@escaping () -> Void) -> Void
    private let sink: ([String: Any]?) -> Void

    private var trackKeys: [String: Any] = [:]
    private var transport: TransportAnchor?
    private var flushScheduled = false
    /// Bumped by `clear()` so an already-scheduled flush closure becomes a no-op.
    private var generation: UInt64 = 0

    /// Dependencies are injectable for tests; defaults target the real info center.
    init(
        now: @escaping () -> UInt64 = NowPlayingInfoStore.continuousNowNs,
        scheduleFlush: @escaping (@escaping () -> Void) -> Void = { DispatchQueue.main.async(execute: $0) },
        sink: @escaping ([String: Any]?) -> Void = { MPNowPlayingInfoCenter.default().nowPlayingInfo = $0 }
    ) {
        self.now = now
        self.scheduleFlush = scheduleFlush
        self.sink = sink
    }

    /// nil params keep the stored value; `replacingExisting` rebuilds the group and
    /// resets the transport anchor.
    func setTrackKeys(
        title: String?,
        artist: String?,
        album: String?,
        artwork: MPMediaItemArtwork?,
        duration: Double?,
        contentId: String?,
        replacingExisting: Bool
    ) {
        dispatchPrecondition(condition: .onQueue(.main))
        if replacingExisting {
            trackKeys.removeAll()
            transport = nil
        }
        if let title { trackKeys[MPMediaItemPropertyTitle] = title }
        if let artist { trackKeys[MPMediaItemPropertyArtist] = artist }
        if let album { trackKeys[MPMediaItemPropertyAlbumTitle] = album }
        if let artwork { trackKeys[MPMediaItemPropertyArtwork] = artwork }
        if let duration { trackKeys[MPMediaItemPropertyPlaybackDuration] = duration }
        if let contentId { trackKeys[MPNowPlayingInfoPropertyExternalContentIdentifier] = contentId }
        markDirty()
    }

    /// nil `elapsedSec` updates rate only, keeping the last known anchor.
    /// No-op while no track is set (a late write must not resurrect cleared state).
    func setTransport(elapsedSec: Double?, rate: Double) {
        dispatchPrecondition(condition: .onQueue(.main))
        guard !trackKeys.isEmpty else { return }
        if let elapsedSec {
            transport = TransportAnchor(elapsedSec: elapsedSec, anchorNs: now(), rate: rate)
        } else if var existing = transport {
            existing.rate = rate
            transport = existing
        } else {
            transport = TransportAnchor(elapsedSec: nil, anchorNs: now(), rate: rate)
        }
        markDirty()
    }

    /// Empties both groups, cancels any scheduled flush, assigns nil once.
    func clear() {
        dispatchPrecondition(condition: .onQueue(.main))
        trackKeys.removeAll()
        transport = nil
        generation &+= 1
        flushScheduled = false
        sink(nil)
    }

    private func markDirty() {
        guard !flushScheduled else { return }
        flushScheduled = true
        let scheduledGeneration = generation
        scheduleFlush { [weak self] in
            guard let self = self,
                  self.flushScheduled,
                  self.generation == scheduledGeneration else { return }
            self.flushNow()
        }
    }

    /// Composes both groups, projecting the anchor to now. Tests call this directly.
    func flushNow() {
        dispatchPrecondition(condition: .onQueue(.main))
        flushScheduled = false
        var info = trackKeys
        if let transport {
            info[MPNowPlayingInfoPropertyPlaybackRate] = transport.rate
            if let elapsedSec = transport.elapsedSec {
                let secondsSinceAnchor = Double(now() &- transport.anchorNs) / Self.nsPerSecond
                let projected = elapsedSec + secondsSinceAnchor * transport.rate
                let duration = (trackKeys[MPMediaItemPropertyPlaybackDuration] as? Double)
                    ?? .greatestFiniteMagnitude
                info[MPNowPlayingInfoPropertyElapsedPlaybackTime] = max(0, min(projected, duration))
            }
        }
        sink(info)
    }
}
