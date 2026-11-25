import SwiftUI

extension View {
    /// Makes this button the target of the one-handed primary action, the double tap on
    /// Apple Watch Series 9, Ultra 2 and newer.
    ///
    /// The gesture needs watchOS 11, and the app runs from watchOS 10, where this does
    /// nothing. It works only on a `Button` or a `Toggle`. Give it to one control per page:
    /// the system runs the shortcut of the page on screen.
    @ViewBuilder
    func primaryActionShortcut() -> some View {
        if #available(watchOS 11.0, *) {
            handGestureShortcut(.primaryAction)
        } else {
            self
        }
    }
}

/// A button that draws its label and nothing else: no dimming while it is pressed.
///
/// For a button as large as the screen, where a press highlight over a photograph reads as
/// a flash rather than as a press.
struct UnhighlightedButtonStyle: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
    }
}
