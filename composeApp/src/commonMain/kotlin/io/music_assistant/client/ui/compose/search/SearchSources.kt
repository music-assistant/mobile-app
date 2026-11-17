package io.music_assistant.client.ui.compose.search

import io.music_assistant.client.data.model.server.ServerProviderInstance
import io.music_assistant.client.ui.compose.common.SelectOption

/**
 * The `providers` choice of `music/search`, like the web frontend. [LIBRARY] is the library. A
 * streaming service is one target with its domain, because the server searches one instance of
 * it. Any other provider is one target per instance. An empty [selected] searches everything.
 *
 * @param providers the provider targets, by name; null while the provider list loads.
 */
data class SearchSources(
    val providers: List<SelectOption<String>>?,
    val selected: List<String>,
) {
    companion object {
        const val LIBRARY = "library"

        private const val SEARCH_FEATURE = "search"

        /**
         * [saved] without the ids of providers that are gone. While the provider list loads,
         * [saved] stays whole, so a search does not fall back to everything.
         */
        fun of(providers: List<ServerProviderInstance>?, saved: List<String>): SearchSources {
            val targets = providers?.let(::targetsOf)
            return SearchSources(
                providers = targets,
                selected = targets
                    ?.let { options -> saved.filter { id -> id == LIBRARY || options.any { it.value == id } } }
                    ?: saved,
            )
        }

        private fun targetsOf(providers: List<ServerProviderInstance>): List<SelectOption<String>> =
            providers
                .filter { it.available && SEARCH_FEATURE in it.supportedFeatures }
                .map { SelectOption(if (it.isStreamingProvider == true) it.domain else it.instanceId, it.name) }
                .distinctBy { it.value }
                .sortedBy { it.label.lowercase() }
    }
}
