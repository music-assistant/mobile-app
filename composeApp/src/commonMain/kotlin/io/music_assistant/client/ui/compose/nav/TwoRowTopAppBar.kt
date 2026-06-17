package io.music_assistant.client.ui.compose.nav

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * [TopAppBar] with row below the navigation icon, title and actions. Not an official component
 * within Material Design 3.
 *
 * Collapsing is owned by the surrounding [TopBarLayout]; this composable is a plain two-row stack and
 * does not deal with scroll behavior itself.
 */
@Composable
fun TwoRowTopAppBar(
    title: @Composable () -> Unit,
    navigationIcon: @Composable () -> Unit,
    actions: @Composable RowScope.() -> Unit,
    secondRow: @Composable RowScope.() -> Unit,
) {
    Column {
        TopAppBar(
            title = title,
            navigationIcon = navigationIcon,
            actions = actions,
        )

        TopAppBar(
            title = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    secondRow()
                }
            },
            windowInsets = WindowInsets(),
        )
    }
}
