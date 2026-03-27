package io.music_assistant.client.utils

import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.runtime.Composable
import androidx.window.core.layout.WindowSizeClass

object WindowClass {
    @Composable
    fun isAtLeastExpanded(): Boolean {
        val windowSizeClass =
            currentWindowAdaptiveInfo(supportLargeAndXLargeWidth = true).windowSizeClass
        return windowSizeClass
            .isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_EXPANDED_LOWER_BOUND)
    }
}