import MaWatchKit
import SwiftUI

/// The queue of the selected player, around whatever is playing now.
///
/// The watch loads a window rather than the whole queue — a full page is a large message for
/// a device on a Bluetooth-proxied link — so this is the part of the queue the user is in,
/// not all of it. A tap plays a row; a swipe removes it, at once, and the server's own
/// update settles any disagreement.
struct QueueView: View {
    @Environment(PlayerStore.self) private var store
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        Group {
            if store.state.queue.isLoading && store.state.queue.items.isEmpty {
                ProgressView()
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
            } else if store.state.queue.items.isEmpty {
                emptyState
            } else {
                list
            }
        }
        .navigationTitle("Queue")
    }

    private var list: some View {
        List {
            // Keyed on the server's `queue_item_id`, which play and remove both address.
                ForEach(store.state.queue.items, id: \.id) { item in
                Button {
                    store.playQueueItem(item.id)
                    dismiss()
                } label: {
                    row(for: item)
                }
                .buttonStyle(.plain)
                .listRowBackground(background(for: item))
                .swipeActions(edge: .trailing) {
                    Button(role: .destructive) {
                        store.removeQueueItem(item.id)
                    } label: {
                        Label("Remove", systemImage: "trash")
                    }
                }
            }
        }
    }

    private func row(for item: WatchQueueItem) -> some View {
        HStack(spacing: 6) {
            VStack(alignment: .leading, spacing: 1) {
                Text(item.title)
                    .font(.body)
                    .lineLimit(2)
                if let artist = item.artist {
                    Text(artist)
                        .font(.caption2)
                        .foregroundStyle(.secondary)
                        .lineLimit(1)
                }
            }
            Spacer(minLength: 0)
            if isPlaying(item) {
                Image(systemName: "speaker.wave.2.fill")
                    .font(.caption2)
                    .foregroundStyle(Color.accentColor)
            }
        }
    }

    private var emptyState: some View {
        VStack(spacing: 6) {
            Image(systemName: "music.note.list")
                .font(.title3)
                .foregroundStyle(.secondary)
            if store.isConnected {
                Text("Queue is empty").font(.headline)
            } else {
                Text("Not connected").font(.headline)
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }

    private func isPlaying(_ item: WatchQueueItem) -> Bool {
        item.id == store.state.activeQueueItemId
    }

    private func background(for item: WatchQueueItem) -> some View {
        isPlaying(item) ? Color.accentColor.opacity(0.3) : Color.gray.opacity(0.18)
    }
}
