package io.music_assistant.client.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.music_assistant.client.api.ServiceClient
import io.music_assistant.client.data.model.server.MAX_SUPPORTED_SCHEMA_VERSION
import io.music_assistant.client.utils.HasConnectionData
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * Drives the single "server too new" warning dialog (see App.kt).
 *
 * Server details (with [schemaVersion][io.music_assistant.client.data.model.server.ServerInfo.schemaVersion])
 * arrive before login/autologin, so keying off [ServiceClient.sessionState] warns at the right moment.
 * [distinctUntilChanged] on the raw schema flips the value `null → N` on each fresh server-info arrival
 * (each real connect), which the dialog's `LaunchedEffect` uses to re-show — matching "every time".
 */
class SchemaVersionWarningViewModel(apiClient: ServiceClient) : ViewModel() {
    /** The incompatible server schema (> [MAX_SUPPORTED_SCHEMA_VERSION]) to warn about, or null. */
    val incompatibleSchema: StateFlow<Int?> =
        apiClient.sessionState
            .map { (it as? HasConnectionData)?.serverInfo?.schemaVersion }
            .distinctUntilChanged()
            .map { schema -> schema?.takeIf { it > MAX_SUPPORTED_SCHEMA_VERSION } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
}
