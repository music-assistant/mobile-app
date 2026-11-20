import MaWatchKit
import SwiftUI

/// Which player the sound comes out of.
///
/// The current one is tinted and says "Current" in words rather than carrying a tick alone:
/// on a watch, colour is read before a glyph is, and the second line says what the colour
/// means. Picking a player goes back to the volume screen, so the slider there is at once
/// the new player's.
///
/// A swipe moves what is playing to that player instead of just pointing the watch at it.
struct OutputView: View {
    @Environment(PlayerStore.self) private var store
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        Group {
            if store.state.selectablePlayers.isEmpty {
                StatusView(state: store.state)
            } else {
                list
            }
        }
        .navigationTitle("Output")
    }

    private var list: some View {
        List {
            // Keyed on the server's player id: `WatchPlayer` is a Kotlin class, and
                // conforming an imported type to `Identifiable` here would break the day the
                // framework declares it itself.
                ForEach(store.state.selectablePlayers, id: \.id) { player in
                Button {
                    store.selectPlayer(player.id)
                    dismiss()
                } label: {
                    row(for: player)
                }
                .buttonStyle(.plain)
                .listRowBackground(background(for: player))
                .swipeActions(edge: .trailing) {
                    if player.id != store.selected?.id {
                        Button {
                            store.transferPlayback(to: player.id)
                            dismiss()
                        } label: {
                            Label("Move here", systemImage: "arrow.turn.down.right")
                        }
                        .tint(.indigo)
                    }
                }
            }
        }
    }

    private func row(for player: WatchPlayer) -> some View {
        HStack(spacing: 6) {
            Image(systemName: player.isGroup ? "hifispeaker.2.fill" : "hifispeaker.fill")
                .font(.caption)
                .foregroundStyle(.secondary)
            VStack(alignment: .leading, spacing: 1) {
                Text(player.name)
                    .font(.body)
                    .lineLimit(1)
                if let detail = detail(for: player) {
                    Text(detail)
                        .font(.caption2)
                        .foregroundStyle(.secondary)
                        .lineLimit(1)
                }
            }
            Spacer(minLength: 0)
            if player.isPlaying {
                Image(systemName: "speaker.wave.2.fill")
                    .font(.caption2)
                    .foregroundStyle(.secondary)
            }
        }
    }

    /// "Current" for the player the watch is pointed at; otherwise what it is playing, or
    /// how many members a group holds.
    private func detail(for player: WatchPlayer) -> String? {
        if player.id == store.selected?.id { return String(localized: "Current") }
        if let subtitle = player.subtitle { return subtitle }
        guard player.memberCount > 0 else { return nil }
        return player.memberCount == 1
            ? String(localized: "1 player")
            : String(localized: "\(Int(player.memberCount)) players")
    }

    private func background(for player: WatchPlayer) -> some View {
        player.id == store.selected?.id
            ? Color.accentColor.opacity(0.3)
            : Color.gray.opacity(0.18)
    }
}
