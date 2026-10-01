package io.music_assistant.client.ui.compose.settings

import android.security.KeyChain
import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState

/** The system KeyChain chooser lists the certificates the user installed in Android settings. */
@Composable
actual fun ClientCertificateSetting(
    host: String,
    port: Int,
    alias: String?,
    onAliasChange: (String?) -> Unit,
) {
    val activity = LocalActivity.current ?: return
    val currentOnAliasChange by rememberUpdatedState(onAliasChange)
    ClientCertificateRow(
        alias = alias,
        onChoose = {
            // The callback runs on a binder thread; a null alias means the user cancelled.
            KeyChain.choosePrivateKeyAlias(
                activity,
                { picked -> picked?.let { currentOnAliasChange(it) } },
                null,
                null,
                host,
                port,
                alias,
            )
        },
        onClear = { onAliasChange(null) },
    )
}
