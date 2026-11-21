import SwiftUI

/// Previous, play/pause and next, sized so the row fits the narrowest watch in circulation.
///
/// Skip is `backward.end` / `forward.end` and not the `.fill` pair: one triangle against an
/// end bar, outlined, which is the glyph the Wear client draws. Taken as SF Symbols rather
/// than as the Wear vector itself, so they keep the weight, the optical sizing and the Always
/// On treatment that every other glyph on this watch gets.
///
/// The three are glyphs on the cover with no disc behind them. The play button is still the
/// larger of them, and carries the accent colour the disc used to, because it is the one the
/// user reaches for without looking. The frames stay, so the tap targets are what they were
/// and nothing reflows when the screen dims; previous and next are dimmed rather than hidden
/// when the player cannot skip, since a gap where a control was reads as the app having lost
/// something.
struct TransportControls: View {
    @Environment(PlayerStore.self) private var store
    @Environment(\.isLuminanceReduced) private var isLuminanceReduced

    var body: some View {
        HStack(spacing: ControlMetrics.transportSpacing) {
            transportButton(
                symbol: "backward.end",
                label: "Previous track",
                action: store.previous
            )
            .disabled(!store.canSkip)

            playPauseButton

            transportButton(
                symbol: "forward.end",
                label: "Next track",
                action: store.next
            )
            .disabled(!store.canSkip)
        }
        .disabled(!store.isConnected)
    }

    private var playPauseButton: some View {
        Button(action: store.togglePlayPause) {
            Image(systemName: store.isPlaying ? "pause.fill" : "play.fill")
                .font(.system(size: ControlMetrics.primary * 0.4, weight: .semibold))
                // The accent colour carried the disc before; with the disc gone it carries
                // the glyph, so the play button is still the one the eye lands on. Always On
                // takes it back to plain, as it did the fill.
                .foregroundStyle(isLuminanceReduced ? Color.primary : Color.accentColor)
                .frame(width: ControlMetrics.primary, height: ControlMetrics.primary)
        }
        .buttonStyle(.plain)
        .contentShape(Circle())
        .accessibilityLabel(store.isPlaying ? "Pause" : "Play")
    }

    private func transportButton(
        symbol: String,
        label: LocalizedStringKey,
        action: @escaping () -> Void
    ) -> some View {
        Button(action: action) {
            Image(systemName: symbol)
                .font(.system(size: ControlMetrics.secondary * 0.4))
                .frame(width: ControlMetrics.secondary, height: ControlMetrics.secondary)
        }
        .buttonStyle(.plain)
        .contentShape(Circle())
        .accessibilityLabel(label)
    }
}

/// The disc behind a secondary control: filled while the screen is lit, and a ring of lit
/// pixels rather than a disc of them once it dims.
struct ControlShape: View {
    let isOutlined: Bool

    var body: some View {
        if isOutlined {
            Circle().strokeBorder(.primary.opacity(0.5), lineWidth: 1.5)
        } else {
            Circle().fill(.white.opacity(0.18))
        }
    }
}
