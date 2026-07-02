import SwiftUI
import ComposeApp
import UIKit
import CarPlay
import Intents
import AVFoundation
import os
import os.log
import os.lock

private let siriLog = OSLog(
    subsystem: Bundle.main.bundleIdentifier ?? "io.music-assistant.client",
    category: "Siri"
)

private final class SystemVolumeButtonObserver: NSObject {
    private let session = AVAudioSession.sharedInstance()
    private let appActive = OSAllocatedUnfairLock(initialState: UIApplication.shared.applicationState == .active)
    private var observation: NSKeyValueObservation?
    private var lastVolume: Float = AVAudioSession.sharedInstance().outputVolume
    private var isAppActive: Bool { appActive.withLock { $0 } }

    /// Wired once at startup. Lets us check whether the local engine is actively
    /// rendering before we touch the shared session's category.
    weak var player: NativeAudioController?

    override init() {
        super.init()
        NotificationCenter.default.addObserver(
            self,
            selector: #selector(appDidBecomeActive),
            name: UIApplication.didBecomeActiveNotification,
            object: nil
        )
        NotificationCenter.default.addObserver(
            self,
            selector: #selector(appWillResignActive),
            name: UIApplication.willResignActiveNotification,
            object: nil
        )
    }

    deinit {
        NotificationCenter.default.removeObserver(self)
    }

    @objc private func appDidBecomeActive() {
        appActive.withLock { $0 = true }
        start()
    }

    @objc private func appWillResignActive() {
        appActive.withLock { $0 = false }
        stop()
    }

    func start() {
        guard observation == nil else { return }
        do {
            // KVO needs an active session; mix so remote volume capture doesn't steal focus.
            if player?.isRenderingAudio != true {
                try session.setCategory(.playback, mode: .default, options: [.mixWithOthers])
                try session.setActive(true, options: [])
            }
            lastVolume = session.outputVolume
            observation = session.observe(\.outputVolume, options: [.new]) { [weak self] audioSession, change in
                guard let self else { return }
                let volume = change.newValue ?? audioSession.outputVolume
                guard volume != self.lastVolume else { return }
                self.lastVolume = volume
                guard self.isAppActive else { return }
                DispatchQueue.main.async { [weak self] in
                    guard self?.isAppActive == true else { return }
                    KmpHelper.shared.onPlatformVolumeButtonPressed()
                }
            }
        } catch {
            NativeLog.shared.error(tag: "SystemVolumeButtonObserver", message: "Failed to activate audio session for volume observation: \(error)")
        }
    }

    func stop() {
        observation?.invalidate()
        observation = nil
        // Do not deactivate; this shared session may still belong to local playback.
    }
}

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

/// Buffers an incoming musicassistant:// URL when it arrives before Koin is
/// initialized (cold-launch-from-deep-link). Replayed by ContentView.onAppear
/// once KmpState.isReady == true. Accessed only from the main thread.
enum PendingURL {
    static var url: URL?
}

/// Single dispatch point for incoming deep links — mirrors Android's
/// MainActivity.handleIncomingUri. Two entry forms reach here:
///   - custom scheme  musicassistant://auth/callback   → OAuth (peeled off)
///   - custom scheme  musicassistant://app/<page>       → DeepLinkBus
///   - Universal Link https://…music-assistant.io/app/<page> → DeepLinkBus
/// The OAuth callback is handled explicitly; everything else is forwarded to
/// the shared DeepLinkBus, which self-filters and ignores anything it doesn't
/// recognize.
func handleIncomingURL(_ url: URL) {
    if url.scheme == "musicassistant", url.host == "auth" {
        guard url.path == "/callback",
              let components = URLComponents(url: url, resolvingAgainstBaseURL: false),
              let code = components.queryItems?.first(where: { $0.name == "code" })?.value
        else { return }
        KmpHelper.shared.authManager.handleOAuthCallback(token: code)
        return
    }
    KmpHelper.shared.handleDeepLink(urlString: url.absoluteString)
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

#if DEBUG
/// Forwards Kermit logs (via the KMP `OsLogSink` bridge) to the unified log with
/// `privacy: .public`, for local development
final class OsLogSinkImpl: NSObject, OsLogSink {
    private let subsystem = Bundle.main.bundleIdentifier ?? "io.music-assistant.client"

    func log(severity: String, tag: String, message: String) {
        let logger = os.Logger(subsystem: subsystem, category: tag)
        let level: OSLogType
        switch severity {
        case "Verbose", "Debug": level = .debug
        case "Info":             level = .info
        case "Warn":             level = .default
        case "Error":            level = .error
        case "Assert":           level = .fault
        default:                 level = .default
        }
        logger.log(level: level, "\(message, privacy: .public)")
    }
}
#endif

@main
struct iOSApp: App {
    @UIApplicationDelegateAdaptor(AppDelegate.self) var appDelegate

    // Keep strong references to native integrations
    // Using NativeAudioController with swift-opus and libFLAC for decoding
    private let player = NativeAudioController()
    private let volumeButtonObserver = SystemVolumeButtonObserver()

    init() {
        // Register the Swift audio player with Kotlin before KMP services are created.
        PlatformPlayerProvider.shared.player = player
        volumeButtonObserver.player = player

        #if DEBUG
        // Route Kermit logs to the unified log un-redacted during development
        // Must run before bootstrapKmp() so init-time logs reach the sink.
        OsLogSinkProvider.shared.sink = OsLogSinkImpl()
        #endif

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
        if UIApplication.shared.applicationState == .active {
            volumeButtonObserver.start()
        }
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

                    if let pending = PendingURL.url {
                        PendingURL.url = nil
                        handleIncomingURL(pending)
                    }
                }
                .onOpenURL { url in
                    // Custom-scheme links (musicassistant://…).
                    if KmpState.isReady {
                        handleIncomingURL(url)
                    } else {
                        PendingURL.url = url
                    }
                }
                .onContinueUserActivity(NSUserActivityTypeBrowsingWeb) { activity in
                    // Universal Links (https://…music-assistant.io/app/…) arrive
                    // as a web-browsing NSUserActivity, not via onOpenURL.
                    guard let url = activity.webpageURL else { return }
                    if KmpState.isReady {
                        handleIncomingURL(url)
                    } else {
                        PendingURL.url = url
                    }
                }
        }
    }
}
