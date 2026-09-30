package io.music_assistant.client.utils

import androidx.collection.LruCache

suspend fun <K : Any, V : Any> LruCache<K, V>.getOrPut(key: K, value: suspend () -> V?): V? {
    val cached = this[key]
    return if (cached != null) {
        cached
    } else {
        val newValue = value()
        if (newValue != null) {
            this.put(key, newValue)
        }

        newValue
    }
}
