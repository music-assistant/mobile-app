package io.music_assistant.client.ui.compose.common

import androidx.compose.runtime.Composable
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/**
 * Abstraction that allows params/properties to use [StringResource] or [String] without the actual
 * underlying type needing to be known.
 */
sealed class DisplayString {
    @Composable
    abstract fun string(): String

    class Raw(private val string: String) : DisplayString() {
        @Composable
        override fun string(): String {
            return string
        }
    }

    class Resource(private val stringResource: StringResource) : DisplayString() {
        @Composable
        override fun string(): String {
            return stringResource(stringResource)
        }
    }
}

fun StringResource.toDisplayString(): DisplayString {
    return DisplayString.Resource(this)
}

fun String.toDisplayString(): DisplayString {
    return DisplayString.Raw(this)
}
