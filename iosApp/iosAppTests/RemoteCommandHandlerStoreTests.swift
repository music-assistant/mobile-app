import Foundation
import XCTest

private final class LockedFlag {
    private let lock = NSLock()
    private var storage = false

    func set(_ value: Bool) {
        lock.lock()
        storage = value
        lock.unlock()
    }

    var value: Bool {
        lock.lock()
        defer { lock.unlock() }
        return storage
    }
}

private final class ReentrantOnDeinit {
    private let beginReentry: DispatchSemaphore
    private let completion: DispatchSemaphore
    private let completedBeforeReturn: LockedFlag

    init(
        beginReentry: DispatchSemaphore,
        completion: DispatchSemaphore,
        completedBeforeReturn: LockedFlag
    ) {
        self.beginReentry = beginReentry
        self.completion = completion
        self.completedBeforeReturn = completedBeforeReturn
    }

    deinit {
        beginReentry.signal()
        completedBeforeReturn.set(
            completion.wait(timeout: .now() + 5) == .success
        )
    }
}

private final class SnapshotValue {
    let id: String

    init(id: String) {
        self.id = id
    }
}

final class RemoteCommandHandlerStoreTests: XCTestCase {
    func testReplacementAndInvocationUseCurrentSnapshot() {
        let store = RemoteCommandHandlerStore<(() -> Void)?>(nil)
        var calls: [String] = []

        store.replace { calls.append("first") }
        store.withSnapshot { $0?() }
        store.replace { calls.append("second") }
        store.withSnapshot { $0?() }

        XCTAssertEqual(calls, ["first", "second"])
    }

    func testReplacementFromHandlerCanReenterStore() {
        let store = RemoteCommandHandlerStore<(() -> Void)?>(nil)
        var calls: [String] = []

        store.replace {
            calls.append("first")
            store.replace { calls.append("second") }
        }

        store.withSnapshot { $0?() }
        store.withSnapshot { $0?() }

        XCTAssertEqual(calls, ["first", "second"])
    }

    func testReplacementDoesNotDestroyOldValueWhileLockIsHeld() {
        let workerReady = DispatchSemaphore(value: 0)
        let beginReentry = DispatchSemaphore(value: 0)
        let completion = DispatchSemaphore(value: 0)
        let workerFinished = expectation(description: "Reentry worker finished")
        let completedBeforeReturn = LockedFlag()
        let store = RemoteCommandHandlerStore<ReentrantOnDeinit?>(nil)
        let queue = DispatchQueue(label: "RemoteCommandHandlerStoreTests.deinit-reentry")

        queue.async {
            workerReady.signal()
            if beginReentry.wait(timeout: .now() + 15) == .success {
                store.withSnapshot { _ in }
                completion.signal()
            }
            workerFinished.fulfill()
        }
        guard workerReady.wait(timeout: .now() + 5) == .success else {
            beginReentry.signal()
            wait(for: [workerFinished], timeout: 10)
            XCTFail("Reentry worker did not start")
            return
        }

        store.replace(
            ReentrantOnDeinit(
                beginReentry: beginReentry,
                completion: completion,
                completedBeforeReturn: completedBeforeReturn
            )
        )
        store.replace(nil)

        XCTAssertTrue(completedBeforeReturn.value, "Old value was destroyed before the store unlocked")
        wait(for: [workerFinished], timeout: 10)
    }

    func testReplacementDuringSnapshotLeavesInFlightSnapshotUntouched() {
        let snapshotStarted = DispatchSemaphore(value: 0)
        let releaseSnapshot = DispatchSemaphore(value: 0)
        let snapshotFinished = DispatchSemaphore(value: 0)
        let store = RemoteCommandHandlerStore(SnapshotValue(id: "first"))

        DispatchQueue.global().async {
            store.withSnapshot { snapshot in
                XCTAssertEqual(snapshot.id, "first")
                snapshotStarted.signal()
                XCTAssertEqual(releaseSnapshot.wait(timeout: .now() + 1), .success)
                XCTAssertEqual(snapshot.id, "first")
            }
            snapshotFinished.signal()
        }

        XCTAssertEqual(snapshotStarted.wait(timeout: .now() + 1), .success)
        store.replace(SnapshotValue(id: "second"))
        releaseSnapshot.signal()
        XCTAssertEqual(snapshotFinished.wait(timeout: .now() + 1), .success)

        var currentID: String?
        store.withSnapshot { currentID = $0.id }
        XCTAssertEqual(currentID, "second")
    }

    func testConcurrentReplacementAndSnapshotAccess() {
        let callsLock = NSLock()
        var callCount = 0
        let store = RemoteCommandHandlerStore<(() -> Void)?>( {
            callsLock.lock()
            callCount += 1
            callsLock.unlock()
        })

        DispatchQueue.concurrentPerform(iterations: 1_000) { index in
            if index.isMultiple(of: 2) {
                store.replace {
                    callsLock.lock()
                    callCount += 1
                    callsLock.unlock()
                }
            } else {
                store.withSnapshot { $0?() }
            }
        }

        store.withSnapshot { $0?() }
        XCTAssertEqual(callCount, 501)
    }
}
