package io.music_assistant.client.data.repository

import io.music_assistant.client.api.Request
import io.music_assistant.client.api.ServiceClient
import io.music_assistant.client.data.model.client.PartyInvite
import io.music_assistant.client.data.model.server.PARTY_DOMAIN
import io.music_assistant.client.data.model.server.ServerPartyConfig
import io.music_assistant.client.utils.resultAs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * The host side of the optional Party plugin: the link that guests open to join the party.
 * The guest side stays in the web frontend.
 */
class PartyRepository(
    private val apiClient: ServiceClient,
    providerRepository: ProviderRepository,
    scope: CoroutineScope,
) {
    /**
     * True while the plugin is loaded and available. The `providers` command and both party
     * commands need `providers.read`, so a role without it fails closed here.
     */
    val available: StateFlow<Boolean> = providerRepository.providers
        .map { state -> state.dataOrNull.orEmpty().any { it.domain == PARTY_DOMAIN && it.available } }
        .stateIn(scope, SharingStarted.Eagerly, false)

    /**
     * Fails only when `party/url` fails. A failed `party/config` only drops the party name and
     * the QR text, because the link alone lets guests join.
     */
    suspend fun invite(): Result<PartyInvite> = coroutineScope {
        val config = async { apiClient.sendRequest(Request.Party.config()).resultAs<ServerPartyConfig>() }
        apiClient.sendRequest(Request.Party.url()).map { answer ->
            answer.resultAs<String>()
                ?.let { url ->
                    val party = config.await()
                    PartyInvite.Open(url, party?.partyName.nonBlank(), party?.qrText.nonBlank())
                }
                ?: PartyInvite.GuestAccessOff.also { config.cancel() }
        }.onFailure { config.cancel() }
    }

    private fun String?.nonBlank(): String? = this?.trim()?.takeIf { it.isNotEmpty() }
}
