package io.music_assistant.client.ui.compose.common

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Link
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalClipboard
import io.music_assistant.client.api.DeepLinkBus
import kotlinx.coroutines.launch
import musicassistantclient.composeapp.generated.resources.Res
import musicassistantclient.composeapp.generated.resources.action_copy_play_link
import org.jetbrains.compose.resources.stringResource

/**
 * "Copy play link": copies the link that plays [mediaUri] from outside the app (Shortcuts, NFC,
 * automations). [player] names the target (`DeepLinkBus.LOCAL_PLAYER` for the built-in player);
 * null leaves it to the player selected when the link opens.
 */
@Composable
fun copyPlayLinkMenuOption(mediaUri: String, player: String? = null): OverflowMenuOption {
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    return OverflowMenuOption(
        title = stringResource(Res.string.action_copy_play_link),
        icon = Icons.Default.Link,
        onClick = {
            scope.launch { clipboard.setClipEntry(plainTextClipEntry(DeepLinkBus.playLink(mediaUri, player))) }
        },
    )
}
