package io.music_assistant.client.utils

import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.dp
import androidx.window.core.layout.WindowSizeClass

object WindowClass {
    /**
     * True when the window has room to spend width instead of height: it is either
     * wide in absolute terms, or wider than it is tall. Drives the side-by-side
     * layouts (navigation rail, row-form item header, inline player controls),
     * which all trade vertical space for horizontal space.
     *
     * A compact phone in landscape qualifies. That is deliberate: landscape is the
     * case those layouts were designed for.
     */
    @Composable
    fun isWide(): Boolean = isAtLeastExpanded() || (isAtLeastMedium() && isLandscape())

    @Composable
    fun isLandscape(): Boolean =
        with(LocalWindowInfo.current.containerSize) { width > height }

    @Composable
    private fun isAtLeastExpanded(): Boolean =
        isAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_EXPANDED_LOWER_BOUND)

    @Composable
    fun isAtLeastMedium(): Boolean =
        isAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND)

    @Composable
    private fun isAtLeastBreakpoint(widthDp: Int): Boolean =
        currentWindowAdaptiveInfoV2().windowSizeClass.isWidthAtLeastBreakpoint(widthDp)
}

@Composable
fun gridItemMinSize() = when {
    WindowClass.isAtLeastMedium() -> 180.dp
    else -> 140.dp
}

@Composable
fun rowImageSize() = when {
    WindowClass.isAtLeastMedium() -> 48.dp
    else -> 48.dp
}

@Composable
fun libraryItemMinWidth() = when {
    WindowClass.isAtLeastMedium() -> 360.dp
    else -> 240.dp
}
