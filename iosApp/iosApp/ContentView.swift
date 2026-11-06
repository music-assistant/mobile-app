import UIKit
import SwiftUI
import ComposeApp

struct ComposeView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        MainViewControllerKt.MainViewController()
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}

struct ContentView: View {
    var body: some View {
        ComposeView()
                .ignoresSafeArea(.keyboard) // Compose has own keyboard handler
                .ignoresSafeArea(.container) // Extend to screen edges (removes white areas)
                .background(DeviceChromeProbe().ignoresSafeArea())
    }
}

/// Reads the system geometry that Compose cannot see from inside the edge-to-edge host.
struct DeviceChromeProbe: UIViewRepresentable {
    func makeUIView(context: Context) -> DeviceChromeProbeView { DeviceChromeProbeView() }
    func updateUIView(_ uiView: DeviceChromeProbeView, context: Context) {}
}

// The foldable APIs come with the iOS 27.1 SDK: reserved regions, the bar layout region, the vertical
// bar edge and the hinge. UIKit in that SDK has the module version 9127.0.85.32.903. UIKit in iOS 27.0
// has the version 9127.0.84.1.116. A check of the Swift version does not work, because Xcode 27.0
// and Xcode 27.1 both have Swift 6.4.
#if canImport(UIKit, _version: 9127.0.85)
final class DeviceChromeProbeView: UIView {
    /// How long the probe reads the geometry after it moves to a window or the app comes back to
    /// the foreground. On a foldable the regions can be missing for the first moments after launch.
    private static let startupReadSeconds: TimeInterval = 10
    /// How long the probe reads the geometry after a change that UIKit reports.
    private static let changeReadSeconds: TimeInterval = 2
    /// The nominal interval of the backup read, which runs in a window that had a hinge or a vertical
    /// bar. UIKit sends no notification when the reserved regions change, so a missed signal would keep
    /// an old layout until the next signal. The backup read runs at an eighth of the rate of the reads
    /// after a signal. The real delay also depends on the timer tolerance (0.5 s) and on the main thread.
    private static let backupReadSeconds: TimeInterval = 2

    private var pollTimer: Timer?
    private var backupTimer: Timer?
    private var pollUntil = Date.distantPast
    private var observing = false
    /// What Kotlin last got. Nil sends every value again.
    private var published: Geometry?
    /// True after one read of this window found a hinge or a vertical bar. A later read without them
    /// does not stop the backup read. The reserved regions can be missing for a moment, for example
    /// while the system moves the app to the other display.
    private var windowHasFoldableGeometry = false

    deinit {
        pollTimer?.invalidate()
        backupTimer?.invalidate()
    }

    override func didMoveToWindow() {
        super.didMoveToWindow()
        stopPolling()
        stopBackupRead()
        published = nil
        windowHasFoldableGeometry = false
        guard window != nil else { return }
        if #available(iOS 27.1, *) {
            startObserving()
        }
        read(for: Self.startupReadSeconds)
    }

    override func layoutSubviews() {
        super.layoutSubviews()
        read(for: Self.changeReadSeconds)
    }

    override func safeAreaInsetsDidChange() {
        super.safeAreaInsetsDidChange()
        read(for: Self.changeReadSeconds)
    }

    /// UIKit sends no notification when the reserved regions change. These signals come with the
    /// changes that move them: the hinge moves, the vertical bar moves, or the window changes.
    @available(iOS 27.1, *)
    private func startObserving() {
        guard !observing else { return }
        observing = true
        addInteraction(UIHingeInteraction { [weak self] _, _ in
            self?.read(for: Self.changeReadSeconds)
        })
        registerForTraitChanges(
            UITraitCollection.systemTraitsAffectingVerticalBarEdge,
            action: #selector(traitsDidChange)
        )
        let center = NotificationCenter.default
        center.addObserver(
            self,
            selector: #selector(sceneWillEnterForeground),
            name: UIScene.willEnterForegroundNotification,
            object: nil
        )
        center.addObserver(
            self,
            selector: #selector(sceneDidActivate),
            name: UIScene.didActivateNotification,
            object: nil
        )
        center.addObserver(
            self,
            selector: #selector(sceneDidEnterBackground),
            name: UIScene.didEnterBackgroundNotification,
            object: nil
        )
    }

    @objc private func traitsDidChange() {
        read(for: Self.changeReadSeconds)
    }

    @objc private func sceneWillEnterForeground(_ notification: Notification) {
        guard notification.object as? UIScene === window?.windowScene else { return }
        // The device can fold while the app is in the background. The scene can still report the
        // background state here, so this read does not wait for the foreground state.
        read(for: Self.startupReadSeconds, enteringForeground: true)
    }

    @objc private func sceneDidActivate(_ notification: Notification) {
        guard notification.object as? UIScene === window?.windowScene else { return }
        read(for: Self.changeReadSeconds)
    }

    @objc private func sceneDidEnterBackground(_ notification: Notification) {
        guard notification.object as? UIScene === window?.windowScene else { return }
        stopPolling()
        stopBackupRead()
    }

    /// Publishes the geometry now and reads it again 4 times a second for the given time, because the
    /// regions can change shortly after the signal. Each new signal makes this time longer. In the
    /// background the layout is not visible, so the probe does not read there. The one exception is the
    /// read when the scene starts to enter the foreground (`enteringForeground`).
    private func read(for seconds: TimeInterval, enteringForeground: Bool = false) {
        guard enteringForeground || isInForeground else {
            stopPolling()
            stopBackupRead()
            return
        }
        report()
        pollUntil = max(pollUntil, Date().addingTimeInterval(seconds))
        guard pollTimer == nil else { return }
        let timer = Timer(timeInterval: 0.25, repeats: true) { [weak self] _ in self?.tick() }
        RunLoop.main.add(timer, forMode: .common)
        pollTimer = timer
    }

    private func tick() {
        guard isInForeground else {
            stopPolling()
            stopBackupRead()
            return
        }
        report()
        if Date() >= pollUntil {
            stopPolling()
        }
    }

    private func stopPolling() {
        pollTimer?.invalidate()
        pollTimer = nil
        pollUntil = .distantPast
    }

    /// Starts the backup read once. `report()` calls it for a window that had a hinge or a vertical bar.
    /// The scene can still report the background state when this happens (`enteringForeground`), but
    /// `backupTick()` reads only in the foreground.
    private func startBackupRead() {
        guard backupTimer == nil else { return }
        let timer = Timer(timeInterval: Self.backupReadSeconds, repeats: true) { [weak self] _ in
            self?.backupTick()
        }
        timer.tolerance = 0.5
        RunLoop.main.add(timer, forMode: .common)
        backupTimer = timer
    }

    private func backupTick() {
        guard isInForeground else {
            stopBackupRead()
            return
        }
        // While the reads after a signal run, they already read the geometry.
        guard pollTimer == nil else { return }
        report()
    }

    private func stopBackupRead() {
        backupTimer?.invalidate()
        backupTimer = nil
    }

    private var isInForeground: Bool {
        switch window?.windowScene?.activationState {
        case .foregroundActive?, .foregroundInactive?: true
        default: false
        }
    }

    /// Gives Kotlin the parts of the geometry that changed.
    private func report() {
        guard let window, #available(iOS 27.1, *) else { return }
        let geometry = Geometry(window: window, traits: traitCollection)
        let last = published
        published = geometry
        // A window that never had a hinge or a vertical bar gets no backup read.
        if geometry.isFoldable {
            windowHasFoldableGeometry = true
        }
        if windowHasFoldableGeometry {
            startBackupRead()
        }
        if last == nil || last?.verticalBar != geometry.verticalBar {
            if let bar = geometry.verticalBar {
                DeviceChromeBridge.shared.setVerticalBarArea(
                    trailingInset: bar.trailingInset,
                    top: bar.top,
                    bottom: bar.bottom,
                    end: bar.end
                )
            } else {
                DeviceChromeBridge.shared.clearVerticalBarArea()
            }
        }
        if last == nil || last?.pageSplit != geometry.pageSplit {
            if let split = geometry.pageSplit {
                DeviceChromeBridge.shared.setPageSplit(start: split.lowerBound, end: split.upperBound)
            } else {
                DeviceChromeBridge.shared.clearPageSplit()
            }
        }
        if last == nil || last?.fold != geometry.fold {
            switch geometry.fold {
            case .vertical(let band):
                DeviceChromeBridge.shared.setFoldRegion(start: band.lowerBound, end: band.upperBound)
            case .horizontal(let band):
                DeviceChromeBridge.shared.setHorizontalFoldRegion(top: band.lowerBound, bottom: band.upperBound)
            case nil:
                DeviceChromeBridge.shared.clearFoldRegion()
            }
        }
    }
}

/// The window geometry in points, as `DeviceChrome` in Kotlin holds it.
private struct Geometry: Equatable {
    struct VerticalBar: Equatable {
        let trailingInset: CGFloat
        let top: CGFloat
        let bottom: CGFloat
        let end: CGFloat
    }

    enum Fold: Equatable {
        case vertical(ClosedRange<CGFloat>)
        case horizontal(ClosedRange<CGFloat>)
    }

    let verticalBar: VerticalBar?
    let pageSplit: ClosedRange<CGFloat>?
    let fold: Fold?
    /// True if the window has a hinge region or a vertical bar.
    let isFoldable: Bool

    @available(iOS 27.1, *)
    @MainActor
    init(window: UIWindow, traits: UITraitCollection) {
        // The system tells where it puts its vertical bar. The probe handles only the right edge. In a
        // right-to-left layout the trailing edge is the left one, which has no right inset. So the
        // window then keeps the usual layout.
        if traits.verticalBarEdge == .trailing, window.safeAreaInsets.right > 0 {
            let bar = window.edgeInsets(for: .bar(onEdge: UIRectEdge.right, extent: 56))
            verticalBar = VerticalBar(
                trailingInset: window.safeAreaInsets.right,
                top: bar.top,
                bottom: bar.bottom,
                end: bar.right
            )
        } else {
            verticalBar = nil
        }
        // The hinge is there also while the device is open flat or closed.
        let hinge = window.reservedRegions(kind: .division, options: .includeInactive).first?.frame
        isFoldable = hinge != nil || verticalBar != nil
        if let hinge, hinge.height > hinge.width, window.bounds.width > window.bounds.height {
            pageSplit = hinge.minX...hinge.maxX
        } else {
            pageSplit = nil
        }
        // Only a partially folded device has an active division region.
        if let frame = window.reservedRegions(kind: .division).first?.frame {
            fold = frame.height > frame.width
                ? .vertical(frame.minX...frame.maxX)
                : .horizontal(frame.minY...frame.maxY)
        } else {
            fold = nil
        }
    }
}
#else
/// Without the iOS 27.1 SDK the probe reports nothing, so the app keeps the usual layout.
final class DeviceChromeProbeView: UIView {}
#endif
