import XCTest
import MediaPlayer

/// Exercises `NowPlayingInfoStore` with an injected clock, flush scheduler, and dict
/// sink so coalescing and anchor projection are fully deterministic.
final class NowPlayingInfoStoreTests: XCTestCase {

    /// Test double bundling the injectable seams: a manually advanced clock, a queue
    /// of captured flush closures, and a log of every dict assignment.
    private final class Harness {
        static let nsPerSecond = 1_000_000_000.0
        var nowNs: UInt64 = 1_000_000_000  // arbitrary nonzero start
        var scheduledFlushes: [() -> Void] = []
        var sinkCalls: [[String: Any]?] = []
        let store: NowPlayingInfoStore

        init() {
            var capturedSelf: Harness?
            store = NowPlayingInfoStore(
                now: { capturedSelf!.nowNs },
                scheduleFlush: { capturedSelf!.scheduledFlushes.append($0) },
                sink: { capturedSelf!.sinkCalls.append($0) }
            )
            capturedSelf = self
        }

        func advanceClock(seconds: Double) {
            nowNs += UInt64(seconds * Self.nsPerSecond)
        }

        /// Runs every captured flush closure in order, like the main loop draining
        /// its queue.
        func runScheduledFlushes() {
            let pending = scheduledFlushes
            scheduledFlushes = []
            pending.forEach { $0() }
        }
    }

    private func setSampleTrack(_ harness: Harness, title: String = "Song") {
        harness.store.setTrackKeys(
            title: title,
            artist: "Artist",
            album: "Album",
            artwork: nil,
            duration: 300.0,
            contentId: "item-1",
            replacingExisting: true
        )
    }

    func testCoalescesMutationsIntoOneAssignment() {
        let harness = Harness()

        setSampleTrack(harness)
        harness.store.setTransport(elapsedSec: 10.0, rate: 1.0)
        harness.store.setTrackKeys(
            title: nil, artist: nil, album: nil, artwork: nil,
            duration: nil, contentId: nil, replacingExisting: false
        )
        harness.store.setTransport(elapsedSec: 12.0, rate: 1.0)

        XCTAssertEqual(harness.sinkCalls.count, 0, "nothing assigned before the flush runs")
        XCTAssertEqual(harness.scheduledFlushes.count, 1, "many mutations schedule one flush")

        harness.runScheduledFlushes()

        XCTAssertEqual(harness.sinkCalls.count, 1, "one main-loop pass, one assignment")
        let info = try! XCTUnwrap(harness.sinkCalls[0])
        XCTAssertEqual(info[MPMediaItemPropertyTitle] as? String, "Song")
        XCTAssertEqual(info[MPNowPlayingInfoPropertyElapsedPlaybackTime] as? Double, 12.0)
        XCTAssertEqual(info[MPNowPlayingInfoPropertyPlaybackRate] as? Double, 1.0)
    }

    func testProjectsAnchorAtFlush() {
        let harness = Harness()

        setSampleTrack(harness)
        harness.store.setTransport(elapsedSec: 100.0, rate: 1.0)
        harness.runScheduledFlushes()

        // Playback runs on for 5 s, then a metadata-only correction arrives. The
        // flush must publish the position projected to flush time, never the stale
        // raw elapsed captured 5 s ago.
        harness.advanceClock(seconds: 5.0)
        harness.store.setTrackKeys(
            title: "Song (corrected)", artist: nil, album: nil, artwork: nil,
            duration: nil, contentId: nil, replacingExisting: false
        )
        harness.runScheduledFlushes()

        let info = try! XCTUnwrap(harness.sinkCalls.last!)
        XCTAssertEqual(info[MPMediaItemPropertyTitle] as? String, "Song (corrected)")
        let elapsed = try! XCTUnwrap(info[MPNowPlayingInfoPropertyElapsedPlaybackTime] as? Double)
        XCTAssertEqual(elapsed, 105.0, accuracy: 0.001)
    }

    func testProjectionClampsToDuration() {
        let harness = Harness()

        setSampleTrack(harness)  // duration 300
        harness.store.setTransport(elapsedSec: 295.0, rate: 1.0)
        harness.advanceClock(seconds: 60.0)
        harness.runScheduledFlushes()

        let info = try! XCTUnwrap(harness.sinkCalls.last!)
        XCTAssertEqual(info[MPNowPlayingInfoPropertyElapsedPlaybackTime] as? Double, 300.0)
    }

    func testKeyGroupsAreIsolated() {
        let harness = Harness()

        setSampleTrack(harness)
        harness.store.setTransport(elapsedSec: 50.0, rate: 1.0)
        harness.runScheduledFlushes()

        // A same-item track write must not disturb transport keys.
        harness.store.setTrackKeys(
            title: "Retitled", artist: nil, album: nil, artwork: nil,
            duration: nil, contentId: nil, replacingExisting: false
        )
        harness.runScheduledFlushes()
        var info = try! XCTUnwrap(harness.sinkCalls.last!)
        XCTAssertEqual(info[MPMediaItemPropertyTitle] as? String, "Retitled")
        XCTAssertEqual(info[MPNowPlayingInfoPropertyElapsedPlaybackTime] as? Double, 50.0)
        XCTAssertEqual(info[MPNowPlayingInfoPropertyPlaybackRate] as? Double, 1.0)

        // A transport write must not disturb track keys.
        harness.store.setTransport(elapsedSec: 60.0, rate: 0.0)
        harness.runScheduledFlushes()
        info = try! XCTUnwrap(harness.sinkCalls.last!)
        XCTAssertEqual(info[MPMediaItemPropertyTitle] as? String, "Retitled")
        XCTAssertEqual(info[MPMediaItemPropertyArtist] as? String, "Artist")
        XCTAssertEqual(info[MPMediaItemPropertyPlaybackDuration] as? Double, 300.0)
        XCTAssertEqual(info[MPNowPlayingInfoPropertyElapsedPlaybackTime] as? Double, 60.0)
        XCTAssertEqual(info[MPNowPlayingInfoPropertyPlaybackRate] as? Double, 0.0)
    }

    func testNilElapsedKeepsAnchorAndUpdatesRate() {
        let harness = Harness()

        setSampleTrack(harness)
        harness.store.setTransport(elapsedSec: 40.0, rate: 1.0)
        harness.runScheduledFlushes()

        // Position unknown, only the rate changes: the previous anchor is kept.
        // Pins parity-with-main behavior: the new rate applies retroactively from
        // the old anchor, so a nil-elapsed pause 5 s in snaps back to 40, not 45.
        // Revisit when rate changes re-anchor at the projected position.
        harness.advanceClock(seconds: 5.0)
        harness.store.setTransport(elapsedSec: nil, rate: 0.0)
        harness.runScheduledFlushes()

        let info = try! XCTUnwrap(harness.sinkCalls.last!)
        XCTAssertEqual(info[MPNowPlayingInfoPropertyElapsedPlaybackTime] as? Double, 40.0)
        XCTAssertEqual(info[MPNowPlayingInfoPropertyPlaybackRate] as? Double, 0.0)
    }

    func testReplacingTrackResetsTransport() {
        let harness = Harness()

        setSampleTrack(harness)
        harness.store.setTransport(elapsedSec: 250.0, rate: 1.0)
        harness.runScheduledFlushes()

        // New track: the old anchor must not leak into the new item's dict.
        setSampleTrack(harness, title: "Next Song")
        harness.runScheduledFlushes()

        let info = try! XCTUnwrap(harness.sinkCalls.last!)
        XCTAssertEqual(info[MPMediaItemPropertyTitle] as? String, "Next Song")
        XCTAssertNil(info[MPNowPlayingInfoPropertyElapsedPlaybackTime])
        XCTAssertNil(info[MPNowPlayingInfoPropertyPlaybackRate])
    }

    func testProjectionClampsToZero() {
        let harness = Harness()

        setSampleTrack(harness)
        harness.store.setTransport(elapsedSec: 2.0, rate: -1.0)
        harness.advanceClock(seconds: 10.0)
        harness.runScheduledFlushes()

        let info = try! XCTUnwrap(harness.sinkCalls.last!)
        XCTAssertEqual(info[MPNowPlayingInfoPropertyElapsedPlaybackTime] as? Double, 0.0)
    }

    func testProjectsNonUnitaryRate() {
        let harness = Harness()

        setSampleTrack(harness)
        harness.store.setTransport(elapsedSec: 100.0, rate: 2.0)
        harness.advanceClock(seconds: 5.0)
        harness.runScheduledFlushes()

        let info = try! XCTUnwrap(harness.sinkCalls.last!)
        let elapsed = try! XCTUnwrap(info[MPNowPlayingInfoPropertyElapsedPlaybackTime] as? Double)
        XCTAssertEqual(elapsed, 110.0, accuracy: 0.001)
    }

    func testRejectsTransportAfterClear() {
        let harness = Harness()

        setSampleTrack(harness)
        harness.store.setTransport(elapsedSec: 10.0, rate: 1.0)
        harness.runScheduledFlushes()

        harness.store.clear()
        XCTAssertNil(harness.sinkCalls.last!)
        let callsAfterClear = harness.sinkCalls.count

        // A straggler position write with no track present must be dropped.
        harness.store.setTransport(elapsedSec: 20.0, rate: 1.0)
        harness.runScheduledFlushes()

        XCTAssertEqual(harness.sinkCalls.count, callsAfterClear,
                       "transport write without a track must not publish anything")
    }

    func testClearAssignsNilOnceAndCancelsFlush() {
        let harness = Harness()

        setSampleTrack(harness)
        XCTAssertEqual(harness.scheduledFlushes.count, 1)

        // Clear lands before the scheduled flush runs.
        harness.store.clear()
        XCTAssertEqual(harness.sinkCalls.count, 1)
        XCTAssertNil(harness.sinkCalls[0])

        // The stale scheduled closure must now be a no-op.
        harness.runScheduledFlushes()
        XCTAssertEqual(harness.sinkCalls.count, 1, "cancelled flush must not publish")
    }
}
