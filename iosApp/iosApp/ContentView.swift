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

final class DeviceChromeProbeView: UIView {
    private var pollTimer: Timer?
    private var quietTicks = 0

    override func didMoveToWindow() {
        super.didMoveToWindow()
        pollTimer?.invalidate()
        pollTimer = nil
        guard window != nil else { return }
        #if compiler(>=6.4) // The foldable APIs need the iOS 27.1 SDK.
        if #available(iOS 27.1, *) {
            quietTicks = 0
            // Reserved regions change without a layout pass.
            let timer = Timer(timeInterval: 0.25, repeats: true) { [weak self] _ in self?.tick() }
            RunLoop.main.add(timer, forMode: .common)
            pollTimer = timer
        }
        #endif
        report()
    }

    override func layoutSubviews() {
        super.layoutSubviews()
        report()
    }

    override func safeAreaInsetsDidChange() {
        super.safeAreaInsetsDidChange()
        report()
    }

    private func tick() {
        // On a foldable the regions can be missing for the first moments after launch, so give up
        // only after ten quiet seconds. Other devices then stop polling.
        quietTicks = report() ? 0 : quietTicks + 1
        if quietTicks >= 40 {
            pollTimer?.invalidate()
            pollTimer = nil
        }
    }

    /// Publishes the current geometry. Returns whether the window shows any sign of a foldable.
    @discardableResult
    private func report() -> Bool {
        #if compiler(>=6.4)
        guard let window else { return false }
        guard #available(iOS 27.1, *) else { return false }
        let hinge = window.reservedRegions(kind: .division, options: .includeInactive).first?.frame
        let closed = traitCollection.horizontalSizeClass == .compact
            && traitCollection.verticalSizeClass == .regular
        if (hinge != nil || closed), window.safeAreaInsets.right > 0 {
            let bar = window.edgeInsets(for: .bar(onEdge: UIRectEdge.right, extent: 56))
            DeviceChromeBridge.shared.setVerticalBarArea(
                trailingInset: window.safeAreaInsets.right,
                top: bar.top,
                bottom: bar.bottom,
                end: bar.right
            )
        } else {
            DeviceChromeBridge.shared.clearVerticalBarArea()
        }
        if let hinge, hinge.height > hinge.width, window.bounds.width > window.bounds.height {
            DeviceChromeBridge.shared.setPageSplit(start: hinge.minX, end: hinge.maxX)
        } else {
            DeviceChromeBridge.shared.clearPageSplit()
        }
        if let fold = window.reservedRegions(kind: .division).first?.frame {
            if fold.height > fold.width {
                DeviceChromeBridge.shared.setFoldRegion(start: fold.minX, end: fold.maxX)
            } else {
                DeviceChromeBridge.shared.setHorizontalFoldRegion(top: fold.minY, bottom: fold.maxY)
            }
        } else {
            DeviceChromeBridge.shared.clearFoldRegion()
        }
        return hinge != nil || (closed && window.safeAreaInsets.right > 0)
        #else
        return false
        #endif
    }
}
