import SwiftUI
import ComposeApp
import UIKit
import CarPlay
import Intents

enum KmpState {
    static var isReady = false
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
        if intent is INPlayMediaIntent {
            return SiriIntentHandler()
        }
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

        // Subscribe to scene background/foreground notifications before any scene connects,
        // so we can halt Compose's Metal render loop while backgrounded. UIBackgroundModes:
        // audio keeps our run loop alive, which would otherwise let CMP keep submitting GPU
        // work that iOS rejects.
        _ = ComposeRenderingGuard.shared

        // Must precede any scene-delegate `didConnect`: both the default
        // SwiftUI scene and `CarPlaySceneDelegate` resolve Koin in their
        // connect callbacks.
        MainViewControllerKt.bootstrapKmp()
        KmpState.isReady = true
        NotificationCenter.default.post(name: KmpState.readyNotification, object: nil)

        // Required for apps to appear in Control Center
        // Must be called for remote control events to work
        UIApplication.shared.beginReceivingRemoteControlEvents()
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
