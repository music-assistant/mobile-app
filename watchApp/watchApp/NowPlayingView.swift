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
        // No scroll view. There was one, disabled, because only a scrolling container gets
        // its content inset below watchOS's *large* navigation title - and that title is
        // gone: a bar with toolbar items in it is an inline one, and this screen has two.
        // What the scroll view cost was a height to lay out against. A scroll view proposes
        // none, so neither `maxHeight` nor a spacer had anything to take, and the column
        // stayed hung under the bar whatever it was asked to do. A plain stack is proposed
        // the whole of what the navigation container has left, which is why the slack below
        // reaches the content now.
        VStack(spacing: ControlMetrics.stackSpacing) {
            Spacer(minLength: 0)
            labels
            timeline
            TransportControls()
                .padding(.top, -ControlMetrics.transportLift)
        }
        // The navigation container keeps about 39 pt of bottom safe area, and the column was
        // already sitting at the bottom of what that left it - measured, with a border drawn
        // round the frame. That strip is the only room left, so the column claims it and
        // gives back just enough to keep the transport off the curve of the glass.
        .padding(.bottom, Self.bottomInset)
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .bottom)
        .ignoresSafeArea(.container, edges: .bottom)
        // `ignoresSafeArea` on the background and not on the column: the artwork runs to
        // the bezel on all four sides, while the controls stay inside the glass.
        .containerBackground(for: .navigation) { background.ignoresSafeArea() }
        .navigationTitle(store.selected?.name ?? "Music Assistant")
        .toolbar {
            ToolbarItem(placement: .topBarLeading) {
                // The AirPlay glyph, as on iOS: picking where the sound comes out is the
                // same question here, and it is the symbol a user already reads as "output".
                barLink(.volume, symbol: "airplayaudio", label: "Volume and output")
            }
            ToolbarItem(placement: .topBarTrailing) {
                barLink(.queue, symbol: "list.bullet", label: "Queue")
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
                // One line, on every watch. A second line is 18 pt that the column does
                // not have on a 40 mm screen, and allowing it anywhere would make the
                // height of this screen depend on the track: the transport row would shift
                // under the user's thumb as one song followed another. A long title scales
                // down first and only then truncates.
                Text(nowPlaying.title)
                    .font(.headline)
                    .lineLimit(1)
                    .minimumScaleFactor(0.7)
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
            progressBar(fraction ?? 0)
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

    /// The played fraction as a hairline.
    ///
    /// Drawn rather than taken from `ProgressView(.linear)`, whose height is watchOS's to
    /// choose and is built for a list row: several times this, and heavy enough on a screen
    /// this small to read as a control rather than as a position.
    private func progressBar(_ fraction: Double) -> some View {
        GeometryReader { geometry in
            ZStack(alignment: .leading) {
                Capsule().fill(.white.opacity(0.25))
                Capsule()
                    .fill(Color.accentColor)
                    .frame(width: geometry.size.width * fraction)
            }
        }
        .frame(height: Self.progressBarHeight)
    }

    private static let progressBarHeight: CGFloat = 2

    private static let bottomInset: CGFloat = 8

    private func elapsed(at date: Date) -> String {
        TrackTime.format(store.position(at: date))
    }

    private func remaining(at date: Date) -> String {
        guard let duration = store.nowPlaying?.durationSeconds?.doubleValue else { return "" }
        return "-" + TrackTime.format(max(0, duration - store.position(at: date)))
    }

    /// The queue and the output picker, in the navigation bar rather than in the column.
    ///
    /// They sit either side of the bar, so the player's name - the inline title - reads
    /// between them, and the whole of the glass below belongs to the track and the
    /// transport. The clock stays where watchOS draws it; its corner is the system's and
    /// not an app's to lay out.
    ///
    /// Bare glyphs, without the filled or outlined disc the transport buttons carry: a
    /// navigation bar item on watchOS is a glyph, and a disc in a bar that is thirty-odd
    /// points tall reads as a button that did not fit. Always On dims the bar itself, so
    /// there is nothing here to take out of it.
    private func barLink(
        _ route: WatchRoute,
        symbol: String,
        label: LocalizedStringKey
    ) -> some View {
        NavigationLink(value: route) {
            Image(systemName: symbol)
        }
        .accessibilityLabel(label)
        .disabled(!store.isConnected)
    }

    @ViewBuilder private var background: some View {
        if isLuminanceReduced {
            Color.black
        } else if let cover {
            // The black is what sizes this, not the cover. A `ZStack` takes its size from
            // its children, and an image under `scaledToFill` reports back whatever it was
            // proposed — which left the background an inset rectangle with bare screen
            // under it. A plain colour is flexible, so it claims the whole screen, and the
            // cover fills that and is clipped to it.
            Color.black
                .overlay {
                    Image(uiImage: cover)
                        .resizable()
                        .scaledToFill()
                        .blur(radius: 2, opaque: true)
                }
                .overlay {
                    LinearGradient(
                        colors: [.black.opacity(0.35), .black.opacity(0.75)],
                        startPoint: .top,
                        endPoint: .bottom
                    )
                }
                .clipped()
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
