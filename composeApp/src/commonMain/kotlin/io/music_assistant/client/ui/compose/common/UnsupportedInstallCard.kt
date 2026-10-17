package io.music_assistant.client.ui.compose.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import musicassistantclient.composeapp.generated.resources.Res
import musicassistantclient.composeapp.generated.resources.common_dismiss
import musicassistantclient.composeapp.generated.resources.nav_settings
import musicassistantclient.composeapp.generated.resources.unsupported_install_description
import musicassistantclient.composeapp.generated.resources.unsupported_install_link
import musicassistantclient.composeapp.generated.resources.unsupported_install_title
import org.jetbrains.compose.resources.stringResource

private const val INSTALLATION_DOCS_URL = "https://music-assistant.io/installation/"

/**
 * Warns that the server reports `unsupported_install`. The user cannot fix the server, but can
 * connect to another one in Settings. A null action hides its button.
 */
@Composable
fun UnsupportedInstallCard(
    modifier: Modifier = Modifier,
    onOpenSettings: (() -> Unit)? = null,
    onDismiss: (() -> Unit)? = null,
) {
    val uriHandler = LocalUriHandler.current
    val buttonColors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onErrorContainer)
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
        ),
    ) {
        Column(modifier = Modifier.padding(start = 16.dp, top = 16.dp, end = 8.dp, bottom = 4.dp)) {
            Text(
                text = stringResource(Res.string.unsupported_install_title),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(end = 8.dp),
            )
            Text(
                text = stringResource(Res.string.unsupported_install_description),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 4.dp, end = 8.dp),
            )
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = { uriHandler.openUri(INSTALLATION_DOCS_URL) }, colors = buttonColors) {
                    Text(stringResource(Res.string.unsupported_install_link))
                }
                onOpenSettings?.let {
                    TextButton(onClick = it, colors = buttonColors) { Text(stringResource(Res.string.nav_settings)) }
                }
                onDismiss?.let {
                    TextButton(onClick = it, colors = buttonColors) { Text(stringResource(Res.string.common_dismiss)) }
                }
            }
        }
    }
}
