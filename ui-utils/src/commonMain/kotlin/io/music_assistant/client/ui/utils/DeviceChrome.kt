package io.music_assistant.client.ui.utils

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
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

/**
 * System window geometry that platform code reports and shared layouts adapt to. Read it through
 * [LocalDeviceChrome]. Every value is null unless the platform reports it, so the layouts then
 * behave as on any other device.
 *
 * @property verticalBarArea null unless the platform lays its controls out vertically in the window.
 * @property pageSplit null unless the window is upright and book-style, open or partially folded. Then it is
 *   the hinge.
 * @property foldRegion null unless the window is partially folded along a vertical line.
 * @property horizontalFoldRegion null unless the window is partially folded along a horizontal line.
 */
@Immutable
data class DeviceChrome(
    val verticalBarArea: VerticalBarArea? = null,
    val pageSplit: FoldRegion? = null,
    val foldRegion: FoldRegion? = null,
    val horizontalFoldRegion: FoldRegion? = null,
) {
    companion object {
        /** The value for a window without a reported vertical bar, hinge or fold. */
        val None = DeviceChrome()
    }
}

/** The [DeviceChrome] of the current window. `App` provides it. The default reports nothing. */
val LocalDeviceChrome = compositionLocalOf { DeviceChrome.None }
