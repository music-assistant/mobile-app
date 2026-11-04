package io.music_assistant.client.ui.utils

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.Dp

/**
 * Where the system expects vertical controls: a strip along the trailing edge, beside the status
 * bar and camera, as on the outer display of iPhone Duo.
 *
 * @property trailingInset width of the strip, which content must stay out of.
 * @property top distance from the top of the window to the first usable position for controls.
 * @property bottom distance from the bottom of the window to the last usable position.
 * @property end distance from the trailing edge of the window to the controls.
 */
data class VerticalBarArea(val trailingInset: Dp, val top: Dp, val bottom: Dp, val end: Dp)

/**
 * The band around a fold, measured from the leading or top edge of the window. Buttons are hard to
 * tap inside it, so interactive content stays on either side.
 */
data class FoldRegion(val start: Dp, val end: Dp)

/** System window geometry that platform code reports and shared layouts adapt to. */
object DeviceChrome {
    /** Null unless the platform lays its controls out vertically in the current window. */
    var verticalBarArea by mutableStateOf<VerticalBarArea?>(null)

    /** The hinge of an upright book-style window, open or partially folded; else null. */
    var pageSplit by mutableStateOf<FoldRegion?>(null)

    /** Null unless the window is partially folded along a vertical line. */
    var foldRegion by mutableStateOf<FoldRegion?>(null)

    /** Null unless the window is partially folded along a horizontal line. */
    var horizontalFoldRegion by mutableStateOf<FoldRegion?>(null)
}
