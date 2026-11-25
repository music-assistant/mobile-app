import SwiftUI

/// The watch client: a remote for whatever a Music Assistant player is already playing.
///
/// The server session lives with the app's time on screen (see `RootView`), because a watch
/// screen is on for seconds and a socket behind a dark screen costs battery for state nobody
/// reads. Everything behind it — the connection, the players, the queue — is the Kotlin
/// Multiplatform `MaWatchKit` framework, which this app only renders.
@main
struct MusicAssistantWatchApp: App {
    @State private var store = PlayerStore()
    @Environment(\.scenePhase) private var scenePhase

    var body: some Scene {
        WindowGroup {
            RootView()
                .environment(store)
        }
        .onChange(of: scenePhase) { _, phase in
            // Always On keeps the app in the foreground with the display dimmed, and the
            // session is held through it on purpose: an always-on screen that froze on
            // whatever was playing when the wrist dropped would be telling the user a lie.
            if phase == .background {
                store.stop()
            } else {
                store.start()
            }
        }
    }
}
