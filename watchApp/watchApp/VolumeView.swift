import SwiftUI

/// How loud, and out of what.
///
/// The Digital Crown is the control: on a watch it is the fastest way to a volume, and it
/// leaves the glass free for the one other thing a user wants here — which player the sound
/// is coming out of, a push away at the bottom. The buttons either side are the same step in
/// a form that works with a gloved hand.
///
/// A player that reports no volume, or a session that is down, disables the slider but not
/// the output button: picking a different player is exactly what such a user wants next.
struct VolumeView: View {
    @Environment(PlayerStore.self) private var store
    @Environment(\.isLuminanceReduced) private var isLuminanceReduced

    @State private var crownLevel: Double = 0
    @State private var lastCommanded: Int?
    @FocusState private var isCrownFocused: Bool

    private var level: Int { store.volume ?? 0 }

    private var isAdjustable: Bool { store.isConnected && store.canSetVolume && store.volume != nil }

    var body: some View {
        VStack(spacing: 10) {
            Text("\(level)%")
                .font(.system(size: 34, weight: .semibold, design: .rounded))
                .monospacedDigit()
                .contentTransition(.numericText())

            HStack(spacing: 10) {
                stepButton(symbol: "speaker.fill", delta: -stepSize, label: "Volume down")
                Gauge(value: Double(level), in: 0...100) { EmptyView() }
                    .gaugeStyle(.linearCapacity)
                    .tint(Color.accentColor)
                stepButton(symbol: "speaker.wave.3.fill", delta: stepSize, label: "Volume up")
            }
            .opacity(isAdjustable ? 1 : 0.4)

            NavigationLink(value: WatchRoute.output) {
                Label {
                    Text(store.selected?.name ?? "Output")
                        .lineLimit(1)
                } icon: {
                    Image(systemName: store.selected?.isGroup == true ? "hifispeaker.2.fill" : "hifispeaker.fill")
                }
            }
            .disabled(!store.isConnected)
        }
        .padding(.horizontal, 4)
        .navigationTitle("Volume")
        .focusable(isAdjustable)
        .focused($isCrownFocused)
        .digitalCrownRotation(
            $crownLevel,
            from: 0,
            through: 100,
            by: 1,
            sensitivity: .medium,
            isContinuous: false,
            isHapticFeedbackEnabled: true
        )
        .onChange(of: crownLevel) { _, turned in
            command(Int(turned.rounded()))
        }
        .onChange(of: store.volume) { _, reported in
            // A volume that arrived from anywhere else — another client, or the server
            // refusing a step — moves the crown back under the user's finger.
            guard let reported, reported != lastCommanded else { return }
            lastCommanded = reported
            crownLevel = Double(reported)
        }
        .onChange(of: isAdjustable) { _, adjustable in
            // The screen can be opened while the session is still coming up; the crown is
            // handed over the moment there is a volume to turn.
            isCrownFocused = adjustable
        }
        .task {
            lastCommanded = store.volume
            crownLevel = Double(level)
            isCrownFocused = isAdjustable
        }
    }

    private func stepButton(symbol: String, delta: Int, label: LocalizedStringKey) -> some View {
        Button {
            command((level + delta).clamped(to: 0...100))
        } label: {
            Image(systemName: symbol)
                .font(.system(size: 14))
                .frame(width: 32, height: 32)
                .background(ControlShape(isOutlined: isLuminanceReduced))
        }
        .buttonStyle(.plain)
        .contentShape(Circle())
        .accessibilityLabel(label)
        .disabled(!isAdjustable)
    }

    /// Sends one level, once.
    ///
    /// Both the crown and the buttons land here, and the crown re-enters it every time the
    /// binding moves — including when the binding was moved from a server report. Comparing
    /// against the last level this screen commanded is what keeps that from echoing back.
    private func command(_ target: Int) {
        guard isAdjustable, target != lastCommanded else { return }
        lastCommanded = target
        crownLevel = Double(target)
        store.setVolume(target)
    }

    /// Five, as on the Wear OS client: a turn of the crown moves a usable amount, and the
    /// server's scale is 0 to 100.
    private let stepSize = 5
}

extension Comparable {
    func clamped(to range: ClosedRange<Self>) -> Self {
        min(max(self, range.lowerBound), range.upperBound)
    }
}
