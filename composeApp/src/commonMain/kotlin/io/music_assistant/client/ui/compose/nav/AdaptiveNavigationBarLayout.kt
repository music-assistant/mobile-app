package io.music_assistant.client.ui.compose.nav

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavKey
import androidx.window.core.layout.WindowSizeClass
import io.music_assistant.client.utils.WindowClass

/**
 * Shows a [NavigationBar] based on [navigationItems] on smaller screens and a [NavigationRail]
 * instead on expanded and larger.
 */
@Composable
fun AdaptiveNavigationBarLayout(
    navigationItems: List<NavigationItem>,
    showNavigation: Boolean = true,
    navigationBarHeight: Dp = 88.dp,
    navigationRailWidth: Dp = 80.dp,
    content: @Composable BoxScope.(contentPadding: PaddingValues) -> Unit,
) {
    val isExpandedScreen = WindowClass.isAtLeastExpanded()

    Box(modifier = Modifier.fillMaxSize()) {
        val showRail = showNavigation && isExpandedScreen
        val showBar = showNavigation && !isExpandedScreen

        // Reserve the real system navigation-bar inset so the chrome reflows when it is
        // shown/hidden (edge-to-edge). Zero when hidden or on devices without a bottom bar.
        val bottomInset = WindowInsets.navigationBars
            .only(WindowInsetsSides.Bottom)
            .asPaddingValues()
            .calculateBottomPadding()

        content(
            if (showRail) {
                PaddingValues(start = navigationRailWidth, bottom = bottomInset)
            } else if (showBar) {
                PaddingValues(bottom = navigationBarHeight + bottomInset)
            } else {
                PaddingValues()
            },
        )

        if (showRail) {
            NavigationRail(
                modifier = Modifier.width(navigationRailWidth),
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            ) {
                navigationItems.forEach {
                    NavigationRailItem(
                        selected = it.selected,
                        onClick = it.onClick,
                        icon = {
                            Icon(it.icon, contentDescription = it.label)
                        },
                    )
                }
            }
        } else if (showBar) {
            NavigationBar(
                modifier = Modifier.align(Alignment.BottomCenter)
                    .height(navigationBarHeight + bottomInset),
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                windowInsets = WindowInsets.navigationBars.only(WindowInsetsSides.Bottom),
            ) {
                navigationItems.forEach {
                    NavigationBarItem(
                        selected = it.selected,
                        onClick = it.onClick,
                        icon = {
                            Icon(it.icon, contentDescription = it.label)
                        },
                    )
                }
            }
        }
    }
}

data class NavigationItem(
    val selected: Boolean,
    val onClick: () -> Unit,
    val icon: ImageVector,
    val label: String? = null,
)

fun <T : NavKey> MultiBackStack<T>.createNavigationItem(
    backStack: Int,
    icon: ImageVector,
    label: String? = null,
    screenState: ScreenState? = null,
): NavigationItem {
    return NavigationItem(
        selected = currentBackStack == backStack,
        onClick = {
            if (this.currentBackStack == backStack) {
                resetCurrentBackStack(screenState)
            } else {
                currentBackStack = backStack
            }
        },
        icon = icon,
        label = label,
    )
}

@Preview
@Composable
fun PreviewAdaptiveNavigationBarLayout() {
    AdaptiveNavigationBarLayout(
        navigationItems = listOf(
            NavigationItem(
                selected = true,
                onClick = {},
                icon = Icons.Default.Home,
            ),
            NavigationItem(
                selected = false,
                onClick = {},
                icon = Icons.Default.Settings,
            ),
        ),
    ) { contentPadding ->
        Text(
            modifier = Modifier.padding(contentPadding),
            text = "Content",
        )
    }
}

@Preview(
    widthDp = WindowSizeClass.WIDTH_DP_EXPANDED_LOWER_BOUND,
    heightDp = WindowSizeClass.HEIGHT_DP_EXPANDED_LOWER_BOUND,
)
@Composable
fun PreviewAdaptiveNavigationBarLayoutExpanded() {
    PreviewAdaptiveNavigationBarLayout()
}
