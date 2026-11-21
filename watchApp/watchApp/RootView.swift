import SwiftUI

/// The screens behind the now-playing root. Each is pushed and dismissed with the system
/// swipe, which is what a watch user reaches for instead of a back button.
enum WatchRoute: Hashable {
    case volume
    case queue
    case output
}

/// Which of the two root pages is up. The cover sits above the controls, so a swipe down
/// uncovers it — and its dot sits above theirs in the page indicator on the right.
enum WatchPage: Hashable {
    case albumArt
    case nowPlaying
}

/// The now-playing root, with the volume and the queue a push away and the output picker one
/// step past the volume — so "how loud" and "out of what" sit together, as they do on the
/// Wear OS client.
///
/// The root is two pages rather than one: the controls, where the watch starts, and the cover
/// on its own above them. The queue is not one of them — it is a push, as it always was, so it
/// covers the pages and their indicator rather than sitting beside them.
struct RootView: View {
    @Environment(PlayerStore.self) private var store

    @State private var page: WatchPage = .nowPlaying

    var body: some View {
        NavigationStack {
            TabView(selection: $page) {
                AlbumArtView()
                    .tag(WatchPage.albumArt)
                NowPlayingView()
                    .tag(WatchPage.nowPlaying)
            }
            .tabViewStyle(.verticalPage)
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
