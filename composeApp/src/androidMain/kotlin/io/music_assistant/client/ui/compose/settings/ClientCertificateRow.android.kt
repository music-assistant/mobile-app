package io.music_assistant.client.ui.compose.settings

import android.security.KeyChain
import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState

/** The system KeyChain chooser lists the certificates the user installed in Android settings. */
@Composable
actual fun rememberClientCertificatePicker(onPicked: (String) -> Unit): ClientCertificatePicker? {
    val activity = LocalActivity.current ?: return null
    val currentOnPicked by rememberUpdatedState(onPicked)
    return remember(activity) {
        ClientCertificatePicker { host, port, currentAlias ->
            // The callback runs on a binder thread; a null alias means the user cancelled.
            KeyChain.choosePrivateKeyAlias(
                activity,
                { alias -> alias?.let { currentOnPicked(it) } },
                null,
                null,
                host,
                port,
                currentAlias,
            )
        }
    }
}
