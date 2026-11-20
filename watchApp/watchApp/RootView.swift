import SwiftUI

/// The screens behind the now-playing root. Each is pushed and dismissed with the system
/// swipe, which is what a watch user reaches for instead of a back button.
enum WatchRoute: Hashable {
    case volume
    case queue
    case output
}

/// The now-playing root, with the volume and the queue a push away and the output picker one
/// step past the volume — so "how loud" and "out of what" sit together, as they do on the
/// Wear OS client.
struct RootView: View {
    @Environment(PlayerStore.self) private var store

    var body: some View {
        NavigationStack {
            NowPlayingView()
                .navigationDestination(for: WatchRoute.self) { route in
                    switch route {
                    case .volume: VolumeView()
                    case .queue: QueueView()
                    case .output: OutputView()
                    }
                }
        }
        // `scenePhase` reports changes, not the first state, so the session is opened here.
        .task { store.start() }
    }
}
