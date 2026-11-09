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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.WideNavigationRail
import androidx.compose.material3.WideNavigationRailValue
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.rememberWideNavigationRailState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
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
import io.music_assistant.client.ui.utils.LocalDeviceChrome
import io.music_assistant.client.ui.utils.VerticalBarArea
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
    playbackItems: List<PlaybackItem> = emptyList(),
    showNavigation: Boolean = true,
    drawInitiallyOpen: Boolean = false,
    navigationBarHeight: Dp = 64.dp,
    maxNavigationBarItems: Int = 2,
    menuLabel: String,
    // The overflow button of the vertical bar, which the nav module has no string resources for.
    moreLabel: String,
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
            trailingItem = trailingItem,
            playbackItems = playbackItems,
            showNavigation = showNavigation,
            editState = editState,
            maxNavigationBarItems = maxNavigationBarItems,
            moreLabel = moreLabel,
            content = content,
        )
    } else {
        BarWithDrawerLayout(
            appName = appName,
            sections = sections,
            trailingItem = trailingItem,
            playbackItems = playbackItems,
            showNavigation = showNavigation,
            navigationBarHeight = navigationBarHeight,
            menuLabel = menuLabel,
            moreLabel = moreLabel,
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
    trailingItem: NavigationItem?,
    playbackItems: List<PlaybackItem>,
    showNavigation: Boolean,
    editState: EditableListState?,
    maxNavigationBarItems: Int,
    moreLabel: String,
    content: @Composable (BoxScope.(PaddingValues, Boolean) -> Unit),
) {
    // Across a vertical fold the rail takes the left page and the content the right one.
    val deviceChrome = LocalDeviceChrome.current
    val fold = deviceChrome.foldRegion
    val verticalBarArea = deviceChrome.verticalBarArea
    Box(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth()) {
            if (showNavigation) {
                WideNavigationRail(
                    modifier = Modifier.width(fold?.start ?: 280.dp),
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
                if (fold != null) {
                    Spacer(Modifier.width(fold.end - fold.start))
                }
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    // The content slides while it changes screens. Clip it, so that a screen that leaves
                    // does not draw over the rail.
                    .clipToBounds()
                    // An expanded player keeps its background under the strip and insets its own content.
                    .padding(end = if (showNavigation) verticalBarArea?.trailingInset ?: 0.dp else 0.dp),
            ) {
                content(PaddingValues(), false)
            }
        }
        if (verticalBarArea != null) {
            VerticalNavigationBar(
                area = verticalBarArea,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = verticalBarArea.end, bottom = verticalBarArea.bottom),
                playbackItems = playbackItems,
                items = sections.first().items.take(maxNavigationBarItems) + listOfNotNull(trailingItem),
                moreLabel = moreLabel,
            )
        }
    }
}

@Composable
private fun BarWithDrawerLayout(
    appName: String,
    sections: List<Section>,
    trailingItem: NavigationItem?,
    playbackItems: List<PlaybackItem>,
    showNavigation: Boolean,
    navigationBarHeight: Dp,
    menuLabel: String,
    moreLabel: String,
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

    val verticalBarArea = LocalDeviceChrome.current.verticalBarArea
    val noRoomForBar = verticalBarArea?.let { verticalBarCapacity(it) == 0 } == true
    ModalNavigationDrawer(
        drawerState = drawerState,
        // Without room for the vertical bar there is no menu button, so the edge swipe has to open it.
        gesturesEnabled = drawerIsOpeningOrOpen || noRoomForBar,
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
                when {
                    // An expanded player keeps its background under the strip and insets its content.
                    !showNavigation -> PaddingValues()
                    verticalBarArea != null -> PaddingValues(end = verticalBarArea.trailingInset)
                    else -> PaddingValues(bottom = navigationBarHeight + bottomInset)
                },
                drawerIsOpeningOrOpen,
            )

            val menuItem = NavigationItem(
                selected = false,
                onClick = {
                    scope.launch { drawerState.open() }
                },
                icon = Icons.Default.Menu,
                label = menuLabel,
            )
            val barItems = buildList {
                add(menuItem)
                addAll(sections.first().items.take(maxNavigationBarItems))
                if (trailingItem != null) {
                    add(trailingItem)
                }
            }

            if (verticalBarArea != null) {
                VerticalNavigationBar(
                    area = verticalBarArea,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = verticalBarArea.end, bottom = verticalBarArea.bottom),
                    playbackItems = playbackItems,
                    items = barItems,
                    moreLabel = moreLabel,
                )
            } else if (showNavigation) {
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

/** How many buttons of the vertical bar fit between the system areas above and below it. */
@Composable
private fun verticalBarCapacity(area: VerticalBarArea): Int {
    val density = LocalDensity.current
    val windowHeight = with(density) { LocalWindowInfo.current.containerSize.height.toDp() }
    return ((windowHeight - area.top - area.bottom) / VerticalBarWidth).toInt().coerceAtLeast(0)
}

/**
 * The navigation bar for a [VerticalBarArea]: icons only, because the strip is too narrow for labels.
 * The [playbackItems] come first.
 *
 * The strip is shared with the status bar and the Dynamic Island, which grows downwards when a Live
 * Activity arrives. Buttons that no longer fit move into an overflow menu, as Apple's guidelines ask.
 */
@Composable
private fun VerticalNavigationBar(
    playbackItems: List<PlaybackItem>,
    items: List<NavigationItem>,
    moreLabel: String,
    area: VerticalBarArea,
    modifier: Modifier = Modifier,
) {
    // Never more buttons than the area the system left for the bar; with room for one, only "more".
    val capacity = verticalBarCapacity(area)
    if (capacity == 0) return
    val entries = playbackItems.map { it.toBarEntry() } + items.map { it.toBarEntry() }
    val visible = if (capacity >= 2 && entries.size <= capacity) entries else entries.take(capacity - 1)
    val overflow = entries.drop(visible.size)
    var overflowOpen by remember { mutableStateOf(false) }

    Surface(
        modifier = modifier
            .width(VerticalBarWidth)
            .testTag(NavigationBarSemantics.TAG),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 3.dp,
        shadowElevation = 3.dp,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            visible.forEach { entry ->
                VerticalBarButton(entry)
            }
            if (overflow.isNotEmpty()) {
                Box {
                    VerticalBarButton(
                        VerticalBarEntry(
                            icon = Icons.Default.MoreVert,
                            contentDescription = moreLabel,
                            label = moreLabel,
                            selected = overflow.any { it.selected },
                            enabled = true,
                            onClick = { overflowOpen = true },
                        ),
                    )
                    DropdownMenu(expanded = overflowOpen, onDismissRequest = { overflowOpen = false }) {
                        overflow.forEach { entry ->
                            DropdownMenuItem(
                                text = { Text(entry.label) },
                                enabled = entry.enabled,
                                leadingIcon = { Icon(entry.icon, contentDescription = null) },
                                onClick = {
                                    overflowOpen = false
                                    entry.onClick()
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun VerticalBarButton(entry: VerticalBarEntry) {
    IconButton(
        modifier = Modifier.size(VerticalBarWidth),
        onClick = entry.onClick,
        enabled = entry.enabled,
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .background(
                    color = if (entry.selected) {
                        MaterialTheme.colorScheme.secondaryContainer
                    } else {
                        Color.Transparent
                    },
                    shape = CircleShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = entry.icon,
                contentDescription = entry.contentDescription,
            )
        }
    }
}

/** One button of the vertical bar, from a [PlaybackItem] or a [NavigationItem]. */
private class VerticalBarEntry(
    val icon: ImageVector,
    val label: String,
    val contentDescription: String,
    val selected: Boolean,
    val enabled: Boolean,
    val onClick: () -> Unit,
)

private fun PlaybackItem.toBarEntry() = VerticalBarEntry(
    icon = icon,
    label = label,
    contentDescription = label,
    selected = false,
    enabled = enabled,
    onClick = onClick,
)

private fun NavigationItem.toBarEntry() = VerticalBarEntry(
    icon = icon,
    label = overflowLabel ?: label,
    contentDescription = contentDescription ?: label,
    selected = selected,
    enabled = true,
    onClick = onClick,
)

private val VerticalBarWidth = 56.dp

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
            val icon = @Composable {
                Box {
                    Icon(item.icon, contentDescription = null)
                    item.badgeColor?.let { StatusBadge(it) }
                }
            }
            item.iconBadge?.let { badge ->
                BadgedBox(badge = { badge() }) { icon() }
            } ?: icon()
        },
        label = {
            val modifier = if (item.marqueeLabel) {
                Modifier
                    .padding(horizontal = 4.dp)
                    .fillMaxWidth()
                    .fadingEdges(4.dp)
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

/** A dot on the bottom-end corner of the icon, ringed in the bar colour so it reads over the glyph. */
@Composable
private fun BoxScope.StatusBadge(color: Color) {
    Box(
        modifier = Modifier
            .align(Alignment.BottomEnd)
            .offset(x = 2.dp, y = 2.dp)
            .size(10.dp)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh, CircleShape)
            .padding(1.dp)
            .background(color, CircleShape),
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
    /** Drawn on the top-right corner of [icon] in the bottom bar. */
    val iconBadge: (@Composable () -> Unit)? = null,
    val label: String,
    val contentDescription: String? = null,
    /** Colour of the status dot on the icon, or null for no dot. Shown in the bottom bar only. */
    val badgeColor: Color? = null,
    val marqueeLabel: Boolean = false,
    val enabled: Boolean = true,
    val editable: Boolean = false,
    val id: String = label,
    /** Text for the vertical overflow menu. Null uses [label]. */
    val overflowLabel: String? = null,
)

/**
 * A playback button of the vertical bar, for example Play or Next. Only the vertical bar shows it.
 * While [enabled] is false, the button shows dimmed and ignores taps.
 */
data class PlaybackItem(
    val icon: ImageVector,
    val label: String,
    val enabled: Boolean,
    val onClick: () -> Unit,
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
        moreLabel = "More",
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
