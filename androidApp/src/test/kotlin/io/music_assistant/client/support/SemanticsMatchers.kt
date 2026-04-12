package io.music_assistant.client.support

import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasContentDescription

fun hasRole(role: Role): SemanticsMatcher {
    return SemanticsMatcher(
        description = "has role '$role'"
    ) { node ->
        node.config.getOrNull(SemanticsProperties.Role) == role
    }
}

fun isTab(contentDescription: String): SemanticsMatcher {
    return hasRole(Role.Tab).and(hasContentDescription(contentDescription))
}