package io.music_assistant.client.ui.compose.nav

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.runtime.MutableState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
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
    navigationBarHeight: Dp = 64.dp,
    navigationRailWidth: Dp = 80.dp,
    // Android TV: a freshly composed root (cold start, or returning from Settings via the
    // back arrow) doesn't reliably receive a focus grant once the window already holds focus,
    // leaving the remote dead until a D-pad press. The caller attaches this requester to the
    // currently-selected item so it can land initial focus there explicitly, and tracks whether
    // the item actually holds focus (to know when the landing has stuck).
    selectedItemFocusRequester: FocusRequester? = null,
    selectedItemFocused: MutableState<Boolean>? = null,
    content: @Composable BoxScope.(contentPadding: PaddingValues) -> Unit,
) {
    val isExpandedScreen = WindowClass.isAtLeastExpanded()

    val selectedItemModifier = { isSelected: Boolean ->
        if (isSelected && selectedItemFocusRequester != null) {
            Modifier
                .focusRequester(selectedItemFocusRequester)
                .onFocusChanged { state -> selectedItemFocused?.value = state.isFocused }
        } else {
            Modifier
        }
    }

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
                        modifier = selectedItemModifier(it.selected),
                        selected = it.selected,
                        onClick = it.onClick,
                        icon = {
                            Icon(it.icon, contentDescription = it.label)
                        },
                    )
                }
            }
        } else if (showBar) {
            // Two concerns are split so icon placement doesn't depend on M3's inset handling:
            //  - the wrapper Box (navigationBarHeight + bottomInset tall) paints the bar background
            //    down behind the system nav / home indicator, so no white bleeds through edge-to-edge;
            //  - the transparent NavigationBar is pinned to the top at exactly navigationBarHeight
            //    (M3's intrinsic min is ~80dp, so the explicit height is required to go smaller), with
            //    zeroed windowInsets so its icons stay centered in that height instead of being pushed
            //    up over a lopsided empty band (which showed as an iOS gap / Android 3-button crimp).
            Box(
                modifier = Modifier.align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(navigationBarHeight + bottomInset)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh),
            ) {
                NavigationBar(
                    modifier = Modifier.align(Alignment.TopCenter).height(navigationBarHeight),
                    containerColor = Color.Transparent,
                    windowInsets = WindowInsets(0, 0, 0, 0),
                ) {
                    navigationItems.forEach {
                        NavigationBarItem(
                            modifier = selectedItemModifier(it.selected),
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
