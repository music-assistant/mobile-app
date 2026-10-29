package io.music_assistant.client.nav

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Manages the editing state for a list of items identified by strings.
 *
 * This class encapsulates the state and actions for editing which items are enabled.
 * It tracks whether editing mode is active and which items are currently enabled.
 *
 * @param enabledIds The initial set of enabled item IDs.
 * @param onSubmit Callback invoked when editing is completed and changes should be submitted.
 */
internal class EditableListState(
    enabledIds: Set<String>,
    private val onSubmit: (List<String>) -> Unit,
) {
    var isEditing: Boolean by mutableStateOf(false)
        private set

    private var enabledIds: Set<String> by mutableStateOf(enabledIds)

    fun toggleEditing() {
        if (isEditing) {
            onSubmit(enabledIds.toList())
        }

        isEditing = !isEditing
    }

    fun setItemEnabled(itemId: String, enabled: Boolean) {
        enabledIds = if (enabled) {
            enabledIds + itemId
        } else {
            enabledIds - itemId
        }
    }

    fun isItemEnabled(itemId: String): Boolean {
        return enabledIds.contains(itemId)
    }
}
