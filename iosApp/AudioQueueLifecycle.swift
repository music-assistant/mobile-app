import Foundation
import AudioToolbox

/// Serializes AudioQueue ownership across the audio consumer, interruption
/// callbacks, and remote commands. Queue construction runs outside the lock;
/// a generation token prevents stale publication after pause or stop.
final class AudioQueueLifecycle {
    struct StartToken: Equatable {
        fileprivate let generation: UInt64
    }

    private let lock = NSLock()
    private var generation: UInt64 = 0
    private var shouldPlay = true
    private var streamStarted = false
    private var queue: AudioQueueRef?
    private var playing = false

    var isRenderingAudio: Bool {
        lock.withLock { streamStarted || playing }
    }

    func prepareToPlay() {
        lock.withLock {
            generation &+= 1
            shouldPlay = true
            streamStarted = false
        }
    }

    func resume(start: (AudioQueueRef) -> Bool) {
        lock.withLock {
            shouldPlay = true
            if let queue {
                streamStarted = true
                playing = start(queue)
            } else {
                generation &+= 1
                streamStarted = false
                playing = false
            }
        }
    }

    func beginStartIfNeeded() -> StartToken? {
        lock.withLock {
            guard shouldPlay, !streamStarted else { return nil }
            streamStarted = true
            return StartToken(generation: generation)
        }
    }

    func acceptsAudio() -> Bool {
        lock.withLock { shouldPlay }
    }

    var hasStartedStream: Bool {
        lock.withLock { streamStarted }
    }

    var isPlaying: Bool {
        lock.withLock { playing }
    }

    func cancelStart(_ token: StartToken) {
        lock.withLock {
            guard generation == token.generation, queue == nil else { return }
            streamStarted = false
            playing = false
        }
    }

    /// Publishes and starts `newQueue` only if `token` remains current.
    /// `start` runs under the lock to prevent concurrent disposal; failure
    /// clears the reservation so the next packet can retry.
    func installIfCurrent(
        _ newQueue: AudioQueueRef,
        token: StartToken,
        start: (AudioQueueRef) -> Bool
    ) -> Bool {
        lock.withLock {
            guard shouldPlay, streamStarted, generation == token.generation else { return false }
            guard start(newQueue) else {
                streamStarted = false
                playing = false
                return false
            }
            queue = newQueue
            playing = true
            return true
        }
    }

    func withQueue(_ body: (AudioQueueRef) -> Void) {
        lock.withLock {
            guard let queue else { return }
            body(queue)
        }
    }

    /// Detaches under the lock but disposes afterward, avoiding lock inversion
    /// with an AudioQueue callback being drained by synchronous disposal.
    func detachQueue(allowFutureStart: Bool) -> (queue: AudioQueueRef?, wasRendering: Bool) {
        lock.withLock {
            let wasRendering = streamStarted || playing
            generation &+= 1
            shouldPlay = allowFutureStart
            streamStarted = false
            playing = false
            defer { queue = nil }
            return (queue, wasRendering)
        }
    }
}

private extension NSLock {
    func withLock<T>(_ body: () -> T) -> T {
        lock()
        defer { unlock() }
        return body()
    }
}
