import MaWatchKit
import SwiftUI

/// The root screen: what is playing, the transport under it, and the queue and volume one
/// push away.
///
/// The cover art is the background rather than a tile, which is how watchOS puts a single
/// subject on a screen this small — the labels and the controls then have the whole glass.
/// Under Always On every part of that is taken back out: no artwork, no ticking progress and
/// outlined buttons instead of filled ones, because a dimmed screen that holds a bright
/// image for an hour is the one thing an OLED must not do.
struct NowPlayingView: View {
    @Environment(PlayerStore.self) private var store
    @Environment(\.isLuminanceReduced) private var isLuminanceReduced

    @State private var cover: UIImage?

    var body: some View {
        // A scroll view that cannot scroll, which is not a contradiction here: only a
        // scrolling container gets the content inset below watchOS's large navigation
        // title, and only a disabled one cannot then ride up over it — which is exactly
        // what this screen did while it could scroll. Nothing here is meant to scroll
        // anyway; it is a fixed control surface, as Apple's own Now Playing is.
        ScrollView {
            VStack(spacing: ControlMetrics.stackSpacing) {
                labels
                timeline
                TransportControls()
            }
            .frame(maxWidth: .infinity)
        }
        .scrollDisabled(true)
        .containerBackground(for: .navigation) { background }
        .navigationTitle(store.selected?.name ?? "Music Assistant")
        // The queue and the volume live in the bottom toolbar rather than in the column:
        // watchOS floats it over the content, so the track, the bar and the transport keep
        // the whole of a 40 mm screen instead of being pushed under its edge.
        .toolbar {
            ToolbarItemGroup(placement: .bottomBar) {
                auxiliaryLink(.queue, symbol: "list.bullet", label: "Queue")
                Spacer()
                auxiliaryLink(.volume, symbol: "speaker.wave.2.fill", label: "Volume and output")
            }
        }
        .task(id: store.nowPlaying?.imageUrl) {
            // Cleared first: keeping the previous cover behind a new track would tint the
            // screen from the wrong album until the fetch lands.
            cover = nil
            cover = await ArtworkLoader.shared.artwork(for: store.nowPlaying?.imageUrl)
        }
    }

    // MARK: - Pieces

    /// The track, or — when there is nothing to play — what the session has to say for
    /// itself, in the same place and the same size, so the screen never reflows around it.
    @ViewBuilder private var labels: some View {
        VStack(spacing: 1) {
            if let nowPlaying = store.nowPlaying {
                Text(nowPlaying.title)
                    .font(.headline)
                    .lineLimit(ControlMetrics.titleLines)
                    .minimumScaleFactor(0.7)
                    .multilineTextAlignment(.center)
                if let artist = nowPlaying.artist {
                    Text(artist)
                        .font(.caption2)
                        .foregroundStyle(.secondary)
                        .lineLimit(1)
                }
            } else {
                let message = StatusMessage(state: store.state)
                Text(message.headline)
                    .font(.headline)
                    .multilineTextAlignment(.center)
                if let detail = message.detail {
                    Text(detail)
                        .font(.caption2)
                        .foregroundStyle(.secondary)
                        .multilineTextAlignment(.center)
                }
            }
        }
        .padding(.horizontal, 4)
    }

    /// The played fraction, and the time either side of it.
    ///
    /// The server reports a position about once a second; between reports the bar is carried
    /// forward on this watch's own clock, half a second at a time. Under Always On it is
    /// drawn once from the last report — a ticking bar is exactly what a still screen is for
    /// not having.
    @ViewBuilder private var timeline: some View {
        if isLuminanceReduced {
            progress(at: Date())
        } else {
            TimelineView(.periodic(from: .now, by: 0.5)) { context in
                progress(at: context.date)
            }
        }
    }

    private func progress(at date: Date) -> some View {
        let fraction = store.progress(at: date)
        return VStack(spacing: 1) {
            ProgressView(value: fraction ?? 0)
                .progressViewStyle(.linear)
                // A radio stream has no length: the bar keeps its space so that nothing
                // reflows when a track with one starts, but it draws no played arc.
                .opacity(fraction == nil ? 0 : 1)
            if ControlMetrics.showsTrackTimes {
                HStack {
                    Text(elapsed(at: date))
                    Spacer(minLength: 4)
                    Text(remaining(at: date))
                }
                .font(.system(size: 11))
                .monospacedDigit()
                .foregroundStyle(.secondary)
                .opacity(fraction == nil ? 0 : 1)
            }
        }
        .padding(.horizontal, 6)
    }

    private func elapsed(at date: Date) -> String {
        TrackTime.format(store.position(at: date))
    }

    private func remaining(at date: Date) -> String {
        guard let duration = store.nowPlaying?.durationSeconds?.doubleValue else { return "" }
        return "-" + TrackTime.format(max(0, duration - store.position(at: date)))
    }

    private func auxiliaryLink(
        _ route: WatchRoute,
        symbol: String,
        label: LocalizedStringKey
    ) -> some View {
        NavigationLink(value: route) {
            Image(systemName: symbol)
                .font(.system(size: 15))
        }
        .accessibilityLabel(label)
        .disabled(!store.isConnected)
    }

    @ViewBuilder private var background: some View {
        if isLuminanceReduced {
            Color.black
        } else if let cover {
            ZStack {
                Image(uiImage: cover)
                    .resizable()
                    .scaledToFill()
                    .blur(radius: 20, opaque: true)
                LinearGradient(
                    colors: [.black.opacity(0.35), .black.opacity(0.75)],
                    startPoint: .top,
                    endPoint: .bottom
                )
            }
        } else {
            LinearGradient(
                colors: [Color(white: 0.14), .black],
                startPoint: .top,
                endPoint: .bottom
            )
        }
    }
}

/// Seconds as `m:ss`, or `h:mm:ss` once a track runs past the hour.
enum TrackTime {
    static func format(_ seconds: Double) -> String {
        let total = Int(seconds.rounded(.down))
        let (hours, minutes, remainder) = (total / 3600, (total % 3600) / 60, total % 60)
        return hours > 0
            ? String(format: "%d:%02d:%02d", hours, minutes, remainder)
            : String(format: "%d:%02d", minutes, remainder)
    }
}
