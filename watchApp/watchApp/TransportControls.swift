import SwiftUI

/// Previous, play/pause and next, sized so the row fits the narrowest watch in circulation.
///
/// The play button is the larger of the three because it is the one the user reaches for
/// without looking; previous and next are dimmed rather than hidden when the player cannot
/// skip, since a gap where a control was reads as the app having lost something.
struct TransportControls: View {
    @Environment(PlayerStore.self) private var store
    @Environment(\.isLuminanceReduced) private var isLuminanceReduced

    var body: some View {
        HStack(spacing: ControlMetrics.transportSpacing) {
            transportButton(
                symbol: "backward.fill",
                label: "Previous track",
                action: store.previous
            )
            .disabled(!store.canSkip)

            playPauseButton

            transportButton(
                symbol: "forward.fill",
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
                .foregroundStyle(isLuminanceReduced ? Color.primary : Color.black)
                .frame(width: ControlMetrics.primary, height: ControlMetrics.primary)
                .background {
                    if isLuminanceReduced {
                        Circle().strokeBorder(.primary.opacity(0.7), lineWidth: 1.5)
                    } else {
                        Circle().fill(Color.accentColor)
                    }
                }
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
                .background(ControlShape(isOutlined: isLuminanceReduced))
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
