import MaWatchKit
import SwiftUI

/// What the screens say in place of a track while there is nothing to show.
///
/// Only the states a user can act on explain themselves in a second line; "Connecting…" with
/// a paragraph under it would be noise on a screen this size.
struct StatusMessage {
    let headline: LocalizedStringKey
    let detail: String?
    let symbol: String

    /// `WatchSessionStatus` is a Kotlin enum, which crosses into Swift as a class rather
    /// than a Swift enum, so the cases are compared rather than matched.
    init(state: WatchUiState) {
        let status = state.status
        if status == WatchSessionStatus.connected {
            headline = "Nothing playing"
            detail = nil
            symbol = "music.note"
        } else if status == WatchSessionStatus.notConfigured {
            headline = "No server"
            detail = "Sign in with Music Assistant on your iPhone"
            symbol = "iphone"
        } else if status == WatchSessionStatus.remoteOnly {
            headline = "Remote access only"
            detail = "Connect your iPhone to the server directly"
            symbol = "iphone.slash"
        } else if status == WatchSessionStatus.unauthorized {
            headline = "Token rejected"
            detail = state.statusMessage
            symbol = "lock.slash"
        } else if status == WatchSessionStatus.reconnecting {
            headline = "Reconnecting…"
            detail = nil
            symbol = "antenna.radiowaves.left.and.right.slash"
        } else {
            headline = "Connecting…"
            detail = nil
            symbol = "antenna.radiowaves.left.and.right"
        }
    }
}

/// A full-screen stand-in for a list that cannot be filled yet.
struct StatusView: View {
    let state: WatchUiState

    var body: some View {
        let message = StatusMessage(state: state)
        VStack(spacing: 6) {
            Image(systemName: message.symbol)
                .font(.title3)
                .foregroundStyle(.secondary)
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
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .padding(.horizontal, 8)
    }
}
