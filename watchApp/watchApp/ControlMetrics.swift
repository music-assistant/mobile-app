import CoreGraphics
import WatchKit

/// One of two size sets, chosen from the width of the glass.
///
/// A 40 mm watch is 162 pt wide and a 49 mm one 205 pt, and a transport row that fits the
/// larger comfortably runs into the bezel on the smaller. The threshold sits between the
/// 41 mm (176 pt) and 45 mm (198 pt) families, so every watch in circulation lands on the set
/// its glass was measured for.
enum ControlMetrics {
    private static let screenWidth = WKInterfaceDevice.current().screenBounds.width

    static let isCompact = screenWidth < 180

    /// The play/pause button, which is the one thing the user reaches for without looking.
    static var primary: CGFloat { isCompact ? 44 : 48 }

    /// Previous and next.
    static var secondary: CGFloat { isCompact ? 34 : 38 }

    static var transportSpacing: CGFloat { isCompact ? 8 : 12 }

    static var stackSpacing: CGFloat { isCompact ? 5 : 6 }

    /// The elapsed and remaining times under the progress bar.
    ///
    /// A 40 mm screen has room for the track, the bar and the transport, and not for a
    /// fourth line as well — and of the four, a pair of timestamps is the one the bar itself
    /// already says. Apple's own Now Playing drops them on the same watches.
    static var showsTrackTimes: Bool { !isCompact }

    /// How many lines the track title may take.
    ///
    /// Everything on the now-playing screen has to fit between the navigation title and the
    /// bottom toolbar without scrolling, and a second title line is 18 pt a 40 mm screen does
    /// not have. The title scales down into one line there instead.
    static var titleLines: Int { isCompact ? 1 : 2 }
}
