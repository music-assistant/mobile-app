package io.music_assistant.client.support

import kotlinx.coroutines.runBlocking
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getString

fun StringResource.get(vararg formatArgs: Any): String {
    val resource = this
    return runBlocking {
        if (formatArgs.isNotEmpty()) {
            getString(resource, *formatArgs)
        } else {
            getString(resource)
        }
    }
}
