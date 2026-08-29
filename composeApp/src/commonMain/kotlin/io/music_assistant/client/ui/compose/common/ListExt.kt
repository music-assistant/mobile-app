package io.music_assistant.client.ui.compose.common

import kotlin.reflect.KClass

fun <T, U : Any> List<T>.filterIsInstance(type: KClass<U>): List<U> {
    return filter { type.isInstance(it) } as List<U>
}
