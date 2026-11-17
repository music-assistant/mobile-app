package io.music_assistant.client.nav

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** What the sidebar editor saves: the enabled item ids, and every item id in the edited order. */
data class SidebarEdit(
    val enabledIds: Set<String>,
    val orderedIds: List<String>,
)

/**
 * Manages the editing state for a list of items identified by strings.
 *
 * Tracks whether editing mode is active, which items are enabled, and the order of all items.
 * The order is one flat list of the section items, so a section owns a contiguous range of it and
 * [move] never takes an item out of its section.
 *
 * @param enabledIds The initial set of enabled item IDs.
 * @param orderedIds The initial ids of all items, section by section.
 * @param onSubmit Callback invoked when editing is completed and changes should be submitted.
 */
internal class EditableListState(
    enabledIds: Set<String>,
    orderedIds: List<String>,
    private val onSubmit: (SidebarEdit) -> Unit,
) {
    var isEditing: Boolean by mutableStateOf(false)
        private set

    private var enabledIds: Set<String> by mutableStateOf(enabledIds)

    private var orderedIds: List<String> by mutableStateOf(orderedIds)

    fun toggleEditing() {
        if (isEditing) {
            onSubmit(SidebarEdit(enabledIds, orderedIds))
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

    /** The ids of [sectionIds] in the edited order. */
    fun ordered(sectionIds: List<String>): List<String> =
        sectionIds.sortedBy { id -> orderedIds.indexOf(id) }

    /** Moves an item of the section with [sectionIds] from [fromIndex] to [toIndex] of that section. */
    fun move(sectionIds: List<String>, fromIndex: Int, toIndex: Int) {
        val start = ordered(sectionIds).firstOrNull()?.let { orderedIds.indexOf(it) }
            ?.takeIf { it >= 0 } ?: return
        orderedIds = orderedIds.toMutableList().apply { add(start + toIndex, removeAt(start + fromIndex)) }
    }
}
