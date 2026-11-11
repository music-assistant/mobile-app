package io.music_assistant.client.data.repository

import io.music_assistant.client.ui.compose.common.DataState

/**
 * [this] with [key] as the newest entry. When the map would hold more than [capacity] entries,
 * the oldest settled entries go; a [Loading][DataState.Loading] entry always stays.
 */
internal fun <K, V> Map<K, DataState<V>>.appended(key: K, state: DataState<V>, capacity: Int): Map<K, DataState<V>> =
    (this - key).let { rest ->
        val settled = rest.keys.filter { rest[it] !is DataState.Loading }
        val overflow = (rest.size + 1 - capacity).coerceAtLeast(0)
        rest - settled.take(overflow).toSet() + (key to state)
    }
