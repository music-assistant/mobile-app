import ComposeApp
import Foundation
import Network
import UIKit

/// Probes the Local Network permission with a listener+browser pair: only seeing our own
/// advertised service in the browse results proves access — `.ready` alone fires within
/// milliseconds even while the permission prompt is still unanswered.
final class LocalNetworkProbe: NSObject, LocalNetworkPermissionProber {
    /// Must be listed under `NSBonjourServices` in Info.plist. Chosen to collide with
    /// nothing real — nothing browses for it and nothing (but us) advertises it.
    private static let bonjourType = "_ma-preflight._tcp"

    /// Policy-denied as reported through `.waiting`. DNSServiceErrorType has no exported
    /// member for it, so the raw kDNSServiceErr_PolicyDenied constant is spelled out.
    private static let kDNSServiceErr_PolicyDenied: Int32 = -65570

    private static func log(_ message: String) {
        NativeLog.shared.info(tag: "LocalNetworkProbe", message: message)
    }

    /// The external label is required: Kotlin exports this method as the selector
    /// `probeTimeoutMs:completion:`, which `probe(timeoutMs:_:)` would not satisfy.
    func probe(timeoutMs: Int64, completion: @escaping (KotlinBoolean?) -> Void) {
        let browserParameters = NWParameters()
        browserParameters.includePeerToPeer = true
        let browser = NWBrowser(
            for: .bonjour(type: Self.bonjourType, domain: nil),
            using: browserParameters
        )

        let listener: NWListener?
        do {
            let l = try NWListener(using: NWParameters(tls: nil, tcp: NWProtocolTCP.Options()))
            l.service = NWListener.Service(
                name: "ma-probe-" + UUID().uuidString,
                type: Self.bonjourType
            )
            // Must be set or the listener errors with POSIX 22 without ever advertising.
            l.newConnectionHandler = { _ in }
            listener = l
        } catch {
            NativeLog.shared.warn(tag: "LocalNetworkProbe", message: "listener create failed: \(error)")
            listener = nil
        }

        var finished = false
        var backgroundObserver: NSObjectProtocol?
        // Handlers retain `finish`, which retains browser/listener: nil them on finish
        // or the whole cluster leaks for the app's lifetime.
        let finish: (KotlinBoolean?, String) -> Void = { result, reason in
            guard !finished else { return }
            finished = true
            browser.browseResultsChangedHandler = nil
            browser.stateUpdateHandler = nil
            listener?.stateUpdateHandler = nil
            if let observer = backgroundObserver {
                NotificationCenter.default.removeObserver(observer)
            }
            browser.cancel()
            listener?.cancel()
            let resultText = result.map { String(describing: $0.boolValue) } ?? "nil"
            Self.log("finish(\(resultText)) via \(reason)")
            completion(result)
        }

        // A suspended main queue cannot fire the timeout; end the probe deterministically
        // instead of resolving stale state on resume.
        backgroundObserver = NotificationCenter.default.addObserver(
            forName: UIApplication.didEnterBackgroundNotification,
            object: nil,
            queue: .main
        ) { _ in
            finish(nil, "backgrounded")
        }

        // Seeing any browse result means our own advertisement looped back through the
        // local network — only possible with permission granted.
        browser.browseResultsChangedHandler = { results, _ in
            if !results.isEmpty {
                finish(KotlinBoolean(bool: true), "own listener visible in browse results")
            }
        }

        browser.stateUpdateHandler = { state in
            switch state {
            case .ready:
                Self.log("browser .ready (browsing; waiting for results)")
            case .waiting(let error):
                Self.log("browser .waiting: \(String(describing: error))")
                if Self.isPolicyDenied(error) {
                    finish(KotlinBoolean(bool: false), "policy denied")
                }
                // Other `.waiting` causes (prompt up, interface settling) keep probing
                // until the timeout decides.
            case .failed(let error):
                NativeLog.shared.warn(tag: "LocalNetworkProbe", message: "browser .failed: \(String(describing: error))")
                finish(nil, "browser .failed")
            case .cancelled:
                break
            @unknown default:
                NativeLog.shared.warn(tag: "LocalNetworkProbe", message: "browser state \(state)")
            }
        }

        listener?.stateUpdateHandler = { state in
            switch state {
            case .ready:
                Self.log("listener .ready (advertising)")
            case .failed(let error):
                NativeLog.shared.warn(tag: "LocalNetworkProbe", message: "listener .failed: \(String(describing: error))")
                finish(nil, "listener .failed")
            default:
                break
            }
        }

        // Bound the whole probe: mDNS-blocked networks never loop results back even when
        // permission is granted, so the caller must not wait forever.
        DispatchQueue.main.asyncAfter(deadline: .now() + .milliseconds(Int(timeoutMs))) {
            finish(nil, "timeout")
        }
        Self.log("probe started (type=\(Self.bonjourType), timeoutMs=\(timeoutMs))")
        listener?.start(queue: .main)
        browser.start(queue: .main)
    }

    private static func isPolicyDenied(_ error: Error?) -> Bool {
        guard let error else { return false }
        if let nwError = error as? NWError, case .dns(let code) = nwError {
            return code == DNSServiceErrorType(kDNSServiceErr_PolicyDenied)
        }
        let nsError = error as NSError
        return nsError.code == Int(kDNSServiceErr_PolicyDenied)
    }
}
