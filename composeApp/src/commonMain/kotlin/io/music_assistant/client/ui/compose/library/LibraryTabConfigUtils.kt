package io.music_assistant.client.ui.compose.library

// Toggling ON moves the item to the bottom of the enabled section.
// Toggling OFF moves the item to the top of the disabled section.
// In both cases the item is inserted at index `enabledCountAfterRemove`.
internal fun <T> moveToEnabledBoundary(
    items: List<Pair<T, Boolean>>,
    target: T,
    newEnabled: Boolean,
): List<Pair<T, Boolean>> {
    val mutable = items.toMutableList()
    val idx = mutable.indexOfFirst { it.first == target }
    if (idx < 0) return items
    mutable.removeAt(idx)
    val insertIndex = mutable.count { it.second }
    mutable.add(insertIndex, target to newEnabled)
    return mutable
}
