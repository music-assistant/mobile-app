package io.music_assistant.client.ui.compose.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import musicassistantclient.composeapp.generated.resources.Res
import musicassistantclient.composeapp.generated.resources.settings_client_certificate
import musicassistantclient.composeapp.generated.resources.settings_client_certificate_choose
import musicassistantclient.composeapp.generated.resources.settings_client_certificate_clear
import musicassistantclient.composeapp.generated.resources.settings_client_certificate_none
import org.jetbrains.compose.resources.stringResource

/**
 * The client certificate sent to an mTLS server. Each platform owns how the user
 * gets one: the Android KeyChain chooser, or a `.p12` import into the iOS Keychain.
 * [host] and [port] only preselect a certificate in the chooser.
 */
@Composable
expect fun ClientCertificateSetting(
    host: String,
    port: Int,
    alias: String?,
    onAliasChange: (String?) -> Unit,
)

@Composable
internal fun ClientCertificateRow(
    alias: String?,
    onChoose: () -> Unit,
    onClear: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(stringResource(Res.string.settings_client_certificate))
            Text(
                text = alias ?: stringResource(Res.string.settings_client_certificate_none),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        alias?.let {
            TextButton(onClick = onClear) { Text(stringResource(Res.string.settings_client_certificate_clear)) }
        }
        TextButton(onClick = onChoose) { Text(stringResource(Res.string.settings_client_certificate_choose)) }
    }
}
