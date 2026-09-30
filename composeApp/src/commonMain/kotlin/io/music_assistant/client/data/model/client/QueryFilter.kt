package io.music_assistant.client.data.model.client

import io.music_assistant.client.data.model.client.items.AppMediaItem
import io.music_assistant.client.data.model.client.items.PlayableItem

/**
 * Client-side text filter over an already loaded list, mirroring the web frontend's
 * `getFilteredItems`: case-insensitive `contains` on the display name, the subtitle (artists)
 * and the parent name (album / podcast). A blank query returns the receiver unchanged, so the
 * result keeps the input order and can be sorted afterwards.
 */
fun <T> List<T>.clientFiltered(query: String): List<T> {
    val needle = query.trim().takeIf { it.isNotEmpty() } ?: return this
    return filter { it.matchesQuery(needle) }
}

private fun Any?.matchesQuery(needle: String): Boolean {
    val haystack = when (this) {
        is PlayableItem -> listOf(displayName, subtitle, parentName)
        is AppMediaItem -> listOf(displayName, subtitle)
        else -> return true
    }
    return haystack.any { it?.contains(needle, ignoreCase = true) == true }
}
