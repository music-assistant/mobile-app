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
    ///
    /// These sizes are the glyphs' frames, and the glyphs are drawn at 0.4 of them. They grew
    /// when the discs behind them went: a glyph on its own reads smaller than the same glyph
    /// on a disc, and without one the frame is a tap target rather than something drawn.
    ///
    /// The row is `secondary + spacing + primary + spacing + secondary` and has to clear the
    /// narrowest glass in circulation, which is 162 pt. At these sizes it comes to 150 pt
    /// compact — six points either side of the row before the bezel — and 178 pt on the large
    /// set, which no watch under 198 pt uses. There is no room left for another step without
    /// the row running into the glass, and unlike the Wear client nothing here scales to fit.
    static var primary: CGFloat { isCompact ? 54 : 62 }

    /// Previous and next.
    static var secondary: CGFloat { isCompact ? 44 : 50 }

    /// Tightened to pay for the sizes above: three buttons that nearly touch read as one
    /// control, which is what a transport row is, and the gap was space the glyphs wanted.
    static var transportSpacing: CGFloat { isCompact ? 4 : 8 }

    static var stackSpacing: CGFloat { isCompact ? 5 : 6 }

    /// How far the transport row is pulled up under the timeline.
    ///
    /// The space there is not stack spacing but the slack inside the buttons' own frames:
    /// those are tap targets, and at these sizes they are a good deal taller than the glyphs
    /// drawn in them, so the row reads as further below the bar than it is. This takes the
    /// slack back where it shows and leaves the targets the size they are.
    static var transportLift: CGFloat { isCompact ? 10 : 12 }

    /// The elapsed and remaining times under the progress bar.
    ///
    /// A 40 mm screen has room for the track, the bar and the transport, and not for a
    /// fourth line as well — and of the four, a pair of timestamps is the one the bar itself
    /// already says. Apple's own Now Playing drops them on the same watches.
    static var showsTrackTimes: Bool { !isCompact }
}
