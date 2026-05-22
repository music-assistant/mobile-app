package io.music_assistant.client.ui.compose.common

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import musicassistantclient.composeapp.generated.resources.Res
import musicassistantclient.composeapp.generated.resources.common_cancel
import musicassistantclient.composeapp.generated.resources.mass
import musicassistantclient.composeapp.generated.resources.settings_connecting
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

@Composable
fun AutoLoginSplash(
    visible: Boolean,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // No AnimatedVisibility wrapper — workaround for CMP 1.10.3 iOS first-frame
    // crash where overlapping animated subtrees + LazyLayout subcompose trip a
    // SubcomposeLayout precondition. Splash now appears/disappears without fade.
    if (!visible) return
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        MaterialTheme.colorScheme.surfaceContainerHigh,
                        MaterialTheme.colorScheme.surfaceContainerLow,
                    ),
                ),
            ),
    ) {
        Column(
            modifier = Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            Image(
                painter = painterResource(Res.drawable.mass),
                contentDescription = null,
                modifier = Modifier.size(128.dp),
            )
            CircularProgressIndicator()
            Text(
                text = stringResource(Res.string.settings_connecting),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        OutlinedButton(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 48.dp),
            onClick = onCancel,
        ) {
            Text(stringResource(Res.string.common_cancel))
        }
    }
}
