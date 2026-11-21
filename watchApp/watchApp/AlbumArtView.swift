import MaWatchKit
import SwiftUI

/// The cover on its own: the page above the now-playing screen, reached by swiping down.
///
/// The cover is the whole screen here, and unlike the one behind the controls it is neither
/// blurred nor darkened — there is nothing left on top of it that has to stay readable. The
/// title and the artist sit at the bottom, out of the way of the art, and carry a shadow
/// rather than a scrim so that the cover keeps the glass to itself.
///
/// The cover is also the play button. Nothing is drawn here and nowhere on it could a tap
/// mean anything else, so the whole of it takes one.
///
/// Under Always On the cover **stays**, which is the one place this app does that: the page
/// is nothing but the cover, so taking it away would leave an empty screen rather than a
/// quieter one. The now-playing page still goes black behind its controls.
struct AlbumArtView: View {
    @Environment(PlayerStore.self) private var store

    @State private var cover: UIImage?

    var body: some View {
        VStack(spacing: 1) {
            Spacer(minLength: 0)
            if let nowPlaying = store.nowPlaying {
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
            }
        }
        .padding(.horizontal, 4)
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .bottom)
        // Legible over a bright cover without putting anything back over the art.
        .shadow(color: .black.opacity(0.7), radius: 3)
        .contentShape(Rectangle())
        .onTapGesture { store.togglePlayPause() }
        .accessibilityAddTraits(.isButton)
        .accessibilityLabel(store.nowPlaying?.isPlaying == true ? "Pause" : "Play")
        .containerBackground(for: .navigation) { background.ignoresSafeArea() }
        // No title: the page is the cover, and the time at the top is the only other thing
        // on it. watchOS crossfades the two pages' container backgrounds, which is what
        // turns the blurred cover behind the controls into this one.
        .navigationTitle("")
        .task(id: store.nowPlaying?.imageUrl) {
            // Cleared first: the previous album's cover under a new track's title is worse
            // than a moment of black.
            cover = nil
            cover = await ArtworkLoader.shared.artwork(for: store.nowPlaying?.imageUrl)
        }
    }

    @ViewBuilder private var background: some View {
        if let cover {
            // The black is what sizes this, as on the now-playing screen: an image under
            // `scaledToFill` reports back whatever it was proposed, so a flexible colour
            // has to claim the screen first and the cover fills that.
            Color.black
                .overlay {
                    Image(uiImage: cover)
                        .resizable()
                        .scaledToFill()
                }
                .clipped()
        } else {
            Color.black
        }
    }
}
