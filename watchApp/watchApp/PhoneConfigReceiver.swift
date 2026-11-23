import Foundation
import WatchConnectivity

/// What the iPhone app said about the server, read from the WatchConnectivity application
/// context.
///
/// The phone writes the context in `WatchConnectivityLink` (composeApp, iosMain), from the
/// map of `WatchLinkContract`. The keys below must match that contract.
enum PhoneConfig: Equatable {
    case configured(host: String, port: Int, isTls: Bool, basePath: String, token: String)
    case remoteOnly
    case signedOut
    case unknown

    init(context: [String: Any]) {
        switch context["status"] as? String {
        case "configured":
            guard let host = context["host"] as? String, !host.isEmpty,
                  let port = (context["port"] as? NSNumber)?.intValue,
                  let token = context["token"] as? String, !token.isEmpty
            else {
                self = .unknown
                return
            }
            self = .configured(
                host: host,
                port: port,
                isTls: (context["tls"] as? NSNumber)?.boolValue ?? false,
                basePath: context["basePath"] as? String ?? "",
                token: token
            )
        case "remote_only":
            self = .remoteOnly
        case "signed_out":
            self = .signedOut
        default:
            self = .unknown
        }
    }
}

/// Receives the server and the token from the iPhone app.
///
/// The system keeps the last application context on the watch, so a watch app that starts
/// while the phone is away still reads the last server on activation. That stored context is
/// the only copy of the token on the watch. Each value reaches `onConfig` on the main thread.
final class PhoneConfigReceiver: NSObject, WCSessionDelegate {
    private let onConfig: (PhoneConfig) -> Void

    init(onConfig: @escaping (PhoneConfig) -> Void) {
        self.onConfig = onConfig
        super.init()
        guard WCSession.isSupported() else { return }
        WCSession.default.delegate = self
        WCSession.default.activate()
    }

    func session(
        _ session: WCSession,
        activationDidCompleteWith activationState: WCSessionActivationState,
        error: Error?
    ) {
        deliver(session.receivedApplicationContext)
    }

    func session(_ session: WCSession, didReceiveApplicationContext applicationContext: [String: Any]) {
        deliver(applicationContext)
    }

    private func deliver(_ context: [String: Any]) {
        let config = PhoneConfig(context: context)
        DispatchQueue.main.async { [onConfig] in onConfig(config) }
    }
}
