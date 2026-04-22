package io.music_assistant.client.support

import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText

fun hasRole(role: Role): SemanticsMatcher {
    return SemanticsMatcher(
        description = "has role '$role'"
    ) { node ->
        node.config.getOrNull(SemanticsProperties.Role) == role
    }
}

fun isTab(text: String): SemanticsMatcher {
    return hasRole(Role.Tab)
        .and(hasContentDescription(text).or(hasText(text)))
}