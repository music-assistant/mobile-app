package io.music_assistant.client.ui.compose.nav

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Surface
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll

/**
 * Simplified version of [androidx.compose.material3.Scaffold] that just supports a top bar. Can be
 * nested in a [androidx.compose.material3.Scaffold] with a bottom bar without introducing the
 * extra recomposition of a nesting set [androidx.compose.material3.Scaffold] composables.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Screen(topBar: @Composable (TopAppBarScrollBehavior) -> Unit, content: @Composable () -> Unit) {
    Surface {
        val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
        Column(
            modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        ) {
            topBar(scrollBehavior)
            content()
        }
    }
}
