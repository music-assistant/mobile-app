package io.music_assistant.client.ui.compose.common

fun <T> Result<List<T>>.getOrEmptyList(): List<T> {
    return this.getOrNull() ?: emptyList()
}
