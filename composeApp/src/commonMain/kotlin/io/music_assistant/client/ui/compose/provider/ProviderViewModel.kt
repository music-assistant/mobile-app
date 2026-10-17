package io.music_assistant.client.ui.compose.provider

import androidx.collection.LruCache
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.music_assistant.client.api.Request
import io.music_assistant.client.api.ServiceClient
import io.music_assistant.client.data.model.client.ProviderDetails
import io.music_assistant.client.data.model.server.ServerMediaItem
import io.music_assistant.client.data.repository.ProviderRepository
import io.music_assistant.client.ui.compose.common.icons.BookshelfIcon
import io.music_assistant.client.ui.compose.common.providers.ProviderIconModel
import io.music_assistant.client.utils.getOrPut
import io.music_assistant.client.utils.resultAs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class ProviderViewModel(
    private val serviceClient: ServiceClient,
    providerRepository: ProviderRepository,
) : ViewModel() {
    private var providerDetailsByDomain = emptyMap<String, ProviderDetails>()
    private var providerDetailsByInstanceId = emptyMap<String, ProviderDetails>()
    private val providerIconsCache = LruCache<String, ProviderIconModel>(10)

    init {
        viewModelScope.launch {
            providerRepository.providers.collect { state ->
                // A failed fetch or a sign-out keeps the names: they only label items.
                val named = state.dataOrNull?.filter { it.type in NAMED_PROVIDER_TYPES } ?: return@collect
                providerDetailsByDomain = named.associate { it.domain to ProviderDetails(it.name) }
                providerDetailsByInstanceId = named.associate { it.instanceId to ProviderDetails(it.name) }
            }
        }
    }

    fun getProviderDetails(domainOrInstanceId: String): ProviderDetails? {
        return providerDetailsByDomain[domainOrInstanceId]
            ?: providerDetailsByInstanceId[domainOrInstanceId]
    }

    fun getProviderIcon(domain: String, variant: String): StateFlow<ProviderIconModel?> {
        val stateFlow = MutableStateFlow<ProviderIconModel?>(null)

        viewModelScope.launch {
            val iconModel = providerIconsCache.getOrPut(domain) {
                if (domain == ServerMediaItem.LIBRARY_PROVIDER) {
                    ProviderIconModel.Mdi(BookshelfIcon, Color.White)
                } else {
                    val iconSvg = fetchIconSvg(domain, variant)
                    if (iconSvg != null) ProviderIconModel.fromSvg(iconSvg) else null
                }
            }

            if (iconModel != null) {
                stateFlow.value = iconModel
            }
        }

        return stateFlow
    }

    /**
     * Fetches [variant] version of provider icon and fallback to default if it that doesn't exist
     */
    private suspend fun fetchIconSvg(domain: String, variant: String): String? {
        val darkIconResult =
            serviceClient.sendRequest(Request.Provider.icon(domain, variant)).resultAs<String>()
        val iconSvg =
            darkIconResult ?: serviceClient.sendRequest(Request.Provider.icon(domain))
                .resultAs<String>()
        return iconSvg
    }

    private companion object {
        // Metadata and plugin providers (e.g. Last.fm) are artist top-tracks sources.
        val NAMED_PROVIDER_TYPES = setOf("music", "metadata", "plugin")
    }
}
