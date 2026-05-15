package io.music_assistant.client.ui.compose.nav

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavKey
import androidx.window.core.layout.WindowSizeClass
import io.music_assistant.client.utils.WindowClass

/**
 * Shows a [NavigationBar] based on [navigationItems] on smaller screens and a [NavigationRail]
 * instead on expanded and larger.
 */
@Composable
fun AdaptiveNavigationScaffold(
    navigationItems: List<NavigationItem>,
    showNavBar: Boolean = true,
    content: @Composable BoxScope.(contentPadding: PaddingValues) -> Unit,
) {
    val isExpandedScreen = WindowClass.isAtLeastExpanded()

    Scaffold(
        bottomBar = {
            if (showNavBar && !isExpandedScreen) {
                NavigationBar(modifier = Modifier.height(88.dp)) {
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
        },
    ) { contentPadding ->
        Row {
            if (showNavBar && isExpandedScreen) {
                NavigationRail {
                    navigationItems.forEach {
                        NavigationRailItem(
                            it.selected,
                            it.onClick,
                            icon = {
                                Icon(it.icon, contentDescription = it.label)
                            },
                        )
                    }
                }
            }

            Box {
                content(contentPadding)
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
): NavigationItem {
    return NavigationItem(
        selected = currentBackStack == backStack,
        onClick = {
            if (this.currentBackStack == backStack) {
                resetCurrentBackStack()
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
fun PreviewAdaptiveNavigationScaffold() {
    AdaptiveNavigationScaffold(
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
    ) {
        Text("Content")
    }
}

@Preview(
    widthDp = WindowSizeClass.WIDTH_DP_EXPANDED_LOWER_BOUND,
    heightDp = WindowSizeClass.HEIGHT_DP_EXPANDED_LOWER_BOUND,
)
@Composable
fun PreviewAdaptiveNavigationScaffoldExpanded() {
    PreviewAdaptiveNavigationScaffold()
}
