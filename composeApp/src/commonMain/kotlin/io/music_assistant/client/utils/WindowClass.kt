package io.music_assistant.client.utils

import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.window.core.layout.WindowSizeClass

object WindowClass {
    @Composable
    fun isAtLeastExpanded(): Boolean =
        isAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_EXPANDED_LOWER_BOUND)

    @Composable
    fun isAtLeastLarge(): Boolean =
        isAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_LARGE_LOWER_BOUND)

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
    WindowClass.isAtLeastMedium() -> 96.dp
    else -> 48.dp
}

@Composable
fun libraryItemMinWidth() = when {
    WindowClass.isAtLeastMedium() -> 360.dp
    else -> 240.dp
}
