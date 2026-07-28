package io.music_assistant.client.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.music_assistant.client.api.ServiceClient
import io.music_assistant.client.data.model.server.LOCAL_SCHEMA_VERSION
import io.music_assistant.client.data.model.server.ServerInfo
import io.music_assistant.client.utils.HasConnectionData
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** Schema-compatibility warning to surface for the connected server, in precedence order. */
enum class SchemaWarning {
    /** Client speaks a schema below the server's minimum supported — unusable. Terminal (must exit). */
    CLIENT_INCOMPATIBLE,

    /** Server speaks a newer schema than the client — some features may misbehave. Dismissible. */
    SERVER_AHEAD,
}

/**
 * Drives the single schema-compatibility warning dialog (see App.kt).
 *
 * Server details arrive before login/autologin, so keying off [ServiceClient.sessionState] warns at
 * the right moment. [distinctUntilChanged] flips the value `null → warning` on each fresh server-info
 * arrival (each real connect), which the dialog's `LaunchedEffect` uses to re-show — matching "every time".
 */
class SchemaVersionWarningViewModel(apiClient: ServiceClient) : ViewModel() {
    /** The compatibility warning to show for the current server, or null when compatible. */
    val warning: StateFlow<SchemaWarning?> =
        apiClient.sessionState
            .map { (it as? HasConnectionData)?.serverInfo?.let(::classify) }
            .distinctUntilChanged()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    /** Terminal check first: below the server's floor supersedes merely trailing its current schema. */
    private fun classify(info: ServerInfo): SchemaWarning? {
        info.minSupportedSchemaVersion?.let {
            if (LOCAL_SCHEMA_VERSION < it) return SchemaWarning.CLIENT_INCOMPATIBLE
        }
        info.schemaVersion?.let {
            if (LOCAL_SCHEMA_VERSION < it) return SchemaWarning.SERVER_AHEAD
        }
        return null
    }
}
