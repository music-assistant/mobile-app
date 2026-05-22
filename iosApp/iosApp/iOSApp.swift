import SwiftUI
import ComposeApp
import UIKit
import CarPlay
import Intents
import os.log
import os.lock

private let siriLog = OSLog(
    subsystem: Bundle.main.bundleIdentifier ?? "io.music-assistant.client",
    category: "Siri"
)

/// Single-bit flag indicating whether `bootstrapKmp()` has run and Koin is
/// usable. Exists because SiriKit intent handlers can be invoked before any
/// scene connects (cold "Hey Siri, play X"), so they need a way to bail
/// rather than dereference an uninitialized Koin graph. Written once on the
/// main thread from `iOSApp.init()`; read from intent-handler queues (which
/// the system does not pin to main). Wrapped in `OSAllocatedUnfairLock` to
/// make the cross-thread read formally data-race-free — uncontested reads,
/// negligible cost.
enum KmpState {
    private static let _isReady = OSAllocatedUnfairLock(initialState: false)
    static var isReady: Bool {
        get { _isReady.withLock { $0 } }
        set { _isReady.withLock { $0 = newValue } }
    }
    static let readyNotification = Notification.Name("KMPReadyNotification")
}

/// Buffers an OAuth callback URL when it arrives before Koin is initialized
/// (cold-launch-from-deep-link). Replayed by ContentView.onAppear once
/// KmpState.isReady == true. Accessed only from the main thread.
enum PendingOAuthCallback {
    static var url: URL?
}

/// Parses a musicassistant://auth/callback?code=... URL and hands the token
/// to AuthenticationManager. Matches the shape Android's MainActivity parses
/// in handleOAuthCallback (MainActivity.kt:64-80). Silently returns for URLs
/// that don't match — we can't assume this app is the only handler of the
/// scheme.
func handleOAuthCallback(_ url: URL) {
    guard url.scheme == "musicassistant",
          url.host == "auth",
          url.path == "/callback",
          let components = URLComponents(url: url, resolvingAgainstBaseURL: false),
          let code = components.queryItems?.first(where: { $0.name == "code" })?.value
    else { return }

    KmpHelper.shared.authManager.handleOAuthCallback(token: code)
}

class AppDelegate: NSObject, UIApplicationDelegate {
    func application(
        _ application: UIApplication,
        configurationForConnecting connectingSceneSession: UISceneSession,
        options: UIScene.ConnectionOptions
    ) -> UISceneConfiguration {
        if connectingSceneSession.role.rawValue == "CPTemplateApplicationSceneSessionRoleApplication" {
            let config = UISceneConfiguration(name: "CarPlay", sessionRole: connectingSceneSession.role)
            config.delegateClass = CarPlaySceneDelegate.self
            return config
        }
        let config = UISceneConfiguration(name: "Default", sessionRole: connectingSceneSession.role)
        return config
    }

    func application(_ application: UIApplication,
                     handlerFor intent: INIntent) -> Any? {
        os_log("AppDelegate.handlerFor: intent class=%{public}@",
               log: siriLog, type: .info, String(describing: type(of: intent)))
        // The same handler conforms to all three media-domain intents:
        // play, affinity (like/dislike), and search. See SiriIntentHandler.swift.
        if intent is INPlayMediaIntent
            || intent is INUpdateMediaAffinityIntent
            || intent is INSearchForMediaIntent {
            os_log("AppDelegate.handlerFor: returning SiriIntentHandler", log: siriLog, type: .info)
            return SiriIntentHandler()
        }
        os_log("AppDelegate.handlerFor: no handler for this intent — returning nil", log: siriLog, type: .error)
        return nil
    }
}

@main
struct iOSApp: App {
    @UIApplicationDelegateAdaptor(AppDelegate.self) var appDelegate

    // Keep a strong reference to the player
    // Using NativeAudioController with swift-opus and libFLAC for decoding
    private let player = NativeAudioController()

    init() {
        // Register the Swift implementation with Kotlin
        PlatformPlayerProvider.shared.player = player

        // Initialize NowPlayingManager early to configure AudioSession
        _ = NowPlayingManager.shared

        // KMP/Koin init MUST run here, not in any SwiftUI lifecycle callback.
        // A CarPlay-only cold launch (head unit tap) connects only the
        // `CPTemplateApplicationScene` — SwiftUI's `WindowGroup` never
        // connects, so `ContentView.onAppear` never fires. Anything tied to
        // SwiftUI for app-wide setup is therefore unreachable on that path.
        // `bootstrapKmp()` is idempotent, so the SwiftUI path's call from
        // `MainViewController()` is safe.
        MainViewControllerKt.bootstrapKmp()
        KmpState.isReady = true
        NotificationCenter.default.post(name: KmpState.readyNotification, object: nil)

        // Required for apps to appear in Control Center
        // Must be called for remote control events to work
        UIApplication.shared.beginReceivingRemoteControlEvents()

        // Surface the system "allow Siri" prompt on first launch so the
        // user gets it as a tap-once dialog instead of a setting they
        // have to find. iOS 18+ exposes the resulting state as the "Use
        // with Siri Requests" toggle under Settings → Apple Intelligence
        // & Siri → Apps → Music Assistant; on fresh installs the toggle
        // defaults to off, and our intent handlers never run until it
        // flips on (verified in real-world testing). Calling on every
        // launch is safe: the system only presents the alert when status
        // is .notDetermined; subsequent calls return the cached value
        // immediately. Requires NSSiriUsageDescription in Info.plist
        // (already present) — without it, this call would crash.
        INPreferences.requestSiriAuthorization { status in
            let statusString: String
            switch status {
            case .notDetermined: statusString = "notDetermined"
            case .restricted:    statusString = "restricted"
            case .denied:        statusString = "denied"
            case .authorized:    statusString = "authorized"
            @unknown default:    statusString = "unknown(\(status.rawValue))"
            }
            os_log("Siri authorization status: %{public}@",
                   log: siriLog, type: .info, statusString)
        }
    }

    var body: some Scene {
        WindowGroup {
            ContentView()
                .onAppear {
                    // OAuthHandler presents `ASWebAuthenticationSession`;
                    // requires a live scene, so can't move to `init()`.
                    KmpHelper.shared.authManager.oauthHandler = OAuthHandler()

                    if let pending = PendingOAuthCallback.url {
                        PendingOAuthCallback.url = nil
                        handleOAuthCallback(pending)
                    }
                }
                .onOpenURL { url in
                    if KmpState.isReady {
                        handleOAuthCallback(url)
                    } else {
                        PendingOAuthCallback.url = url
                    }
                }
        }
    }
}
