package io.music_assistant.client.nav

import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Text
import androidx.compose.material3.WideNavigationRail
import androidx.compose.material3.WideNavigationRailValue
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.rememberWideNavigationRailState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavKey
import androidx.window.core.layout.WindowSizeClass
import io.music_assistant.client.ui.utils.WindowClass
import io.music_assistant.client.ui.utils.fadingEdges
import kotlinx.coroutines.launch

/**
 * Shows a navigation bar combined with a navigation drawer based on [sections] on
 * tall, narrow windows and a navigation rail instead on wide ones (see [WindowClass.isWide]).
 *
 * When showing a bar, only the first [Section] items (up to [maxNavigationBarItems]) are used.
 */
@Composable
fun AdaptiveNavigationBarLayout(
    appName: String,
    sections: List<Section>,
    trailingItem: NavigationItem? = null,
    showNavigation: Boolean = true,
    drawInitiallyOpen: Boolean = false,
    navigationBarHeight: Dp = 64.dp,
    maxNavigationBarItems: Int = 2,
    menuLabel: String,
    onEdited: ((List<String>) -> Unit)? = null,
    content: @Composable BoxScope.(contentPadding: PaddingValues, drawerOpen: Boolean) -> Unit,
) {
    val editState = onEdited?.let { onEditedCallback ->
        remember(sections) {
            EditableListState(
                enabledIds = sections.flatMap { it.items }.filter { it.enabled }.map { it.id }
                    .toSet(),
                onSubmit = onEditedCallback,
            )
        }
    }

    if (WindowClass.isWide()) {
        RailLayout(
            appName = appName,
            sections = sections,
            showNavigation = showNavigation,
            editState = editState,
            content = content,
        )
    } else {
        BarWithDrawerLayout(
            appName = appName,
            sections = sections,
            trailingItem = trailingItem,
            showNavigation = showNavigation,
            navigationBarHeight = navigationBarHeight,
            menuLabel = menuLabel,
            maxNavigationBarItems = maxNavigationBarItems,
            drawInitiallyOpen = drawInitiallyOpen,
            editState = editState,
            content = content,
        )
    }
}

@Composable
private fun RailLayout(
    appName: String,
    sections: List<Section>,
    showNavigation: Boolean,
    editState: EditableListState?,
    content: @Composable (BoxScope.(PaddingValues, Boolean) -> Unit),
) {
    Row(Modifier.fillMaxWidth()) {
        if (showNavigation) {
            WideNavigationRail(
                modifier = Modifier.width(280.dp),
                state = rememberWideNavigationRailState(initialValue = WideNavigationRailValue.Expanded),
                header = {
                    SidebarHeader(
                        appName = appName,
                        editState = editState,
                    )
                },
            ) {
                SidebarSections(
                    sections = sections,
                    itemOnClick = { it.onClick() },
                    editState = editState,
                )
            }
        }

        Box {
            content(PaddingValues(), false)
        }
    }
}

@Composable
private fun BarWithDrawerLayout(
    appName: String,
    sections: List<Section>,
    trailingItem: NavigationItem?,
    showNavigation: Boolean,
    navigationBarHeight: Dp,
    menuLabel: String,
    maxNavigationBarItems: Int,
    drawInitiallyOpen: Boolean,
    editState: EditableListState?,
    content: @Composable (BoxScope.(PaddingValues, Boolean) -> Unit),
) {
    val scope = rememberCoroutineScope()
    val drawerState = rememberDrawerState(
        initialValue = if (drawInitiallyOpen) {
            DrawerValue.Open
        } else {
            DrawerValue.Closed
        },
    )

    val drawerIsOpeningOrOpen = drawerState.targetValue == DrawerValue.Open || drawerState.isOpen
    BackHandler(enabled = drawerIsOpeningOrOpen) {
        scope.launch {
            drawerState.close()
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = drawerIsOpeningOrOpen,
        drawerContent = {
            ModalDrawerSheet(
                modifier = Modifier.testTag(NavigationDrawerSemantics.TAG),
            ) {
                Spacer(modifier = Modifier.height(8.dp))

                SidebarHeader(
                    appName = appName,
                    editState = editState,
                )

                Spacer(modifier = Modifier.height(16.dp))

                SidebarSections(
                    sections = sections,
                    itemOnClick = {
                        scope.launch {
                            drawerState.close()
                            it.onClick()
                        }
                    },
                    editState = editState,
                )
            }
        },
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Reserve the real system navigation-bar inset so the chrome reflows when it is
            // shown/hidden (edge-to-edge). Zero when hidden or on devices without a bottom bar.
            val bottomInset = WindowInsets.navigationBars
                .only(WindowInsetsSides.Bottom)
                .asPaddingValues()
                .calculateBottomPadding()

            content(
                if (showNavigation) {
                    PaddingValues(bottom = navigationBarHeight + bottomInset)
                } else {
                    PaddingValues()
                },
                drawerIsOpeningOrOpen,
            )

            if (showNavigation) {
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
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .height(navigationBarHeight)
                            .testTag(NavigationBarSemantics.TAG),
                        containerColor = Color.Transparent,
                        windowInsets = WindowInsets(0, 0, 0, 0),
                    ) {
                        val menuItem = NavigationItem(
                            selected = false,
                            onClick = {
                                scope.launch { drawerState.open() }
                            },
                            icon = Icons.Default.Menu,
                            label = menuLabel,
                        )

                        val firstSection = sections.first()
                        val barItems = buildList {
                            add(menuItem)
                            addAll(firstSection.items.take(maxNavigationBarItems))
                            if (trailingItem != null) {
                                add(trailingItem)
                            }
                        }

                        barItems.forEach {
                            NavigationBarItem(it)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SidebarHeader(
    appName: String,
    editState: EditableListState?,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(appName, style = MaterialTheme.typography.titleLarge)

        if (editState != null) {
            IconButton(
                onClick = { editState.toggleEditing() },
            ) {
                Icon(
                    imageVector = if (editState.isEditing) Icons.Default.Done else Icons.Default.Edit,
                    contentDescription = null,
                )
            }
        }
    }
}

@Composable
private fun SidebarSections(
    sections: List<Section>,
    itemOnClick: (NavigationItem) -> Unit,
    editState: EditableListState?,
) {
    Column(
        modifier = Modifier
            .padding(horizontal = 16.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        sections.forEachIndexed { index, section ->
            if (index != 0) {
                Spacer(modifier = Modifier.height(8.dp))
            }

            SectionTitle(section.title)

            section.items.forEach {
                if (it.enabled || (editState?.isEditing == true)) {
                    NavigationDrawerItem(
                        label = {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(it.label)
                                if (it.editable && editState?.isEditing == true) {
                                    Checkbox(
                                        checked = editState.isItemEnabled(it.id),
                                        onCheckedChange = { checked ->
                                            editState.setItemEnabled(it.id, checked)
                                        },
                                    )
                                }
                            }
                        },
                        icon = {
                            Icon(
                                it.icon,
                                contentDescription = null,
                                modifier = Modifier.size(24.dp),
                            )
                        },
                        selected = it.selected,
                        onClick = { itemOnClick(it) },
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(title: String) {
    Text(
        title,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        style = MaterialTheme.typography.titleMedium,
    )
}

@Composable
private fun RowScope.NavigationBarItem(item: NavigationItem) {
    NavigationBarItem(
        modifier = Modifier.clearAndSetSemantics {
            role = Role.Tab
            selected = item.selected
            contentDescription = item.contentDescription ?: item.label
        },
        selected = item.selected,
        onClick = item.onClick,
        icon = {
            Icon(item.icon, contentDescription = null)
        },
        label = {
            val modifier = if (item.marqueeLabel) {
                Modifier
                    .padding(horizontal = 8.dp)
                    .fillMaxWidth()
                    .fadingEdges(12.dp)
                    .basicMarquee()
            } else {
                Modifier
            }

            Text(
                item.label,
                maxLines = 1,
                textAlign = TextAlign.Center,
                modifier = modifier.fillMaxWidth(),
            )
        },
    )
}

data class Section(
    val title: String,
    val items: List<NavigationItem>,
)

data class NavigationItem(
    val selected: Boolean,
    val onClick: () -> Unit,
    val icon: ImageVector,
    val label: String,
    val contentDescription: String? = null,
    val marqueeLabel: Boolean = false,
    val enabled: Boolean = true,
    val editable: Boolean = false,
    val id: String = label,
)

fun <T : NavKey> MultiBackStack<T>.createNavigationItem(
    backStack: Int,
    icon: ImageVector,
    label: String,
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

object NavigationBarSemantics {
    const val TAG = "NavBar"
}

object NavigationDrawerSemantics {
    const val TAG = "NavDrawer"
}

@Preview
@Composable
fun PreviewAdaptiveNavigationBarLayout(drawerOpen: Boolean = false) {
    AdaptiveNavigationBarLayout(
        appName = "Drawer Title",
        sections = listOf(
            Section(
                title = "Section 1",
                items = listOf(
                    NavigationItem(
                        selected = true,
                        onClick = {},
                        icon = Icons.Default.Home,
                        label = "Home",
                    ),
                ),
            ),
            Section(
                title = "Section 2",
                items = listOf(
                    NavigationItem(
                        selected = false,
                        onClick = {},
                        icon = Icons.Default.Settings,
                        label = "Settings",
                    ),
                ),
            ),
        ),
        drawInitiallyOpen = drawerOpen,
        menuLabel = "Menu",
    ) { contentPadding, _ ->
        Text(
            modifier = Modifier.padding(contentPadding),
            text = "Content",
        )
    }
}

@Preview
@Composable
fun PreviewAdaptiveNavigationBarLayoutDrawerOpen() {
    PreviewAdaptiveNavigationBarLayout(drawerOpen = true)
}

@Preview(
    widthDp = WindowSizeClass.WIDTH_DP_EXPANDED_LOWER_BOUND,
    heightDp = WindowSizeClass.HEIGHT_DP_EXPANDED_LOWER_BOUND,
)
@Composable
fun PreviewAdaptiveNavigationBarLayoutExpanded() {
    PreviewAdaptiveNavigationBarLayout()
}
