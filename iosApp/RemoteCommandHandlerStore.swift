import Foundation

final class RemoteCommandHandlerStore<Value> {
    private let lock = NSLock()
    private var value: Value

    init(_ value: Value) {
        self.value = value
    }

    func replace(_ value: Value) {
        lock.lock()
        let oldValue = self.value
        self.value = value
        withExtendedLifetime(oldValue) {
            lock.unlock()
        }
    }

    func withSnapshot(_ body: (Value) -> Void) {
        lock.lock()
        let snapshot = value
        lock.unlock()
        body(snapshot)
    }
}
