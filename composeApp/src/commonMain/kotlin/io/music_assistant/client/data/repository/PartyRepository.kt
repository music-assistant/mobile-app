package io.music_assistant.client.data.repository

import io.music_assistant.client.api.Answer
import io.music_assistant.client.api.Request
import io.music_assistant.client.api.ServiceClient
import io.music_assistant.client.data.model.client.Dependency
import io.music_assistant.client.data.model.client.PartyInvite
import io.music_assistant.client.data.model.client.PartySetting
import io.music_assistant.client.data.model.client.PartySettingInput
import io.music_assistant.client.data.model.client.PartySettings
import io.music_assistant.client.data.model.server.GrantedScopes
import io.music_assistant.client.data.model.server.PARTY_DOMAIN
import io.music_assistant.client.data.model.server.Scope
import io.music_assistant.client.data.model.server.ServerConfigEntry
import io.music_assistant.client.data.model.server.ServerPartyConfig
import io.music_assistant.client.data.model.server.ServerProviderConfig
import io.music_assistant.client.data.model.server.events.ProvidersUpdatedEvent
import io.music_assistant.client.utils.resultAs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive

/**
 * The host side of the optional Party plugin: the link that guests open to join the party, and
 * the host settings. The guest side stays in the web frontend.
 */
class PartyRepository(
    private val apiClient: ServiceClient,
    providerRepository: ProviderRepository,
    grantedScopes: StateFlow<GrantedScopes>,
    scope: CoroutineScope,
) {
    /** The loaded and available plugin instance, or null. */
    private val instanceId: StateFlow<String?> = providerRepository.providers
        .map { state -> state.dataOrNull?.firstOrNull { it.domain == PARTY_DOMAIN && it.available }?.instanceId }
        .stateIn(scope, SharingStarted.Eagerly, null)

    /**
     * True while the plugin is loaded and available. The `providers` command and both party
     * commands need `providers.read`, so a role without it fails closed here.
     */
    val available: StateFlow<Boolean> = instanceId
        .map { it != null }
        .stateIn(scope, SharingStarted.Eagerly, false)

    /**
     * True when the user may edit the host settings. Saving a plugin config needs
     * `config.providers.write`, because a plugin is not a music source that a member owns.
     */
    val canConfigure: StateFlow<Boolean> = grantedScopes
        .map { it.hasAll(listOf(Scope.CONFIG_PROVIDERS_READ, Scope.CONFIG_PROVIDERS_WRITE)) }
        .stateIn(scope, SharingStarted.Eagerly, false)

    /**
     * Emits on each `providers_updated`. A save reloads the plugin: the server sends one event
     * when it unloads the plugin and one when the reloaded plugin has its commands again.
     */
    val changes: Flow<Unit> = apiClient.events
        .filterIsInstance<ProvidersUpdatedEvent>()
        .map { }
        .shareIn(scope, SharingStarted.WhileSubscribed())

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

    suspend fun settings(): Result<PartySettings> = withInstance { id ->
        apiClient.sendRequest(Request.Provider.config(id)).toSettings()
    }

    /**
     * Saves the new text value of each setting in [edits], typed as its input needs, and answers
     * with the settings that the server stored.
     */
    suspend fun save(edits: Map<PartySetting, String>): Result<PartySettings> = withInstance { id ->
        val values = runCatching { edits.entries.associate { (setting, text) -> setting.key to setting.input.toJson(text) } }
            .getOrElse { return@withInstance Result.failure(it) }
        apiClient.sendRequest(Request.Provider.saveConfig(PARTY_DOMAIN, id, values)).toSettings()
    }

    private suspend fun withInstance(block: suspend (String) -> Result<PartySettings>): Result<PartySettings> =
        instanceId.value?.let { block(it) } ?: Result.failure(IllegalStateException("The party plugin is not loaded"))

    private fun Result<Answer>.toSettings(): Result<PartySettings> = mapCatching { answer ->
        answer.resultAs<ServerProviderConfig>()?.toPartySettings() ?: error("Missing or undecodable party config")
    }

    private fun String?.nonBlank(): String? = this?.trim()?.takeIf { it.isNotEmpty() }

    private companion object {
        const val GUEST_ACCESS = "enable_guest_access"

        /** The core host settings, in the order of the web settings page. */
        val FIELD_KEYS = listOf("mode", "player", "party_name", "qr_text", "party_duration", "karaoke_mode")

        fun ServerProviderConfig.toPartySettings(): PartySettings? {
            val guestAccess = values[GUEST_ACCESS]?.toSetting(GUEST_ACCESS) ?: return null
            return PartySettings(guestAccess, FIELD_KEYS.mapNotNull { key -> values[key]?.toSetting(key) })
        }

        /** Null for an entry type that the sheet does not edit. */
        fun ServerConfigEntry.toSetting(key: String): PartySetting? {
            val input = when {
                type == "boolean" -> PartySettingInput.Switch
                options.isNotEmpty() -> PartySettingInput.Choice(
                    options.mapNotNull { option ->
                        option.value.text()?.let { PartySettingInput.Option(it, option.title ?: it) }
                    },
                )
                type == "integer" -> PartySettingInput.Number(intRange)
                type == "string" -> PartySettingInput.Text
                else -> return null
            }
            return PartySetting(
                key = key,
                label = label ?: key,
                description = description,
                input = input,
                value = effective?.content.orEmpty(),
                dependsOn = dependsOn?.let { Dependency(it, dependsOnValue?.text()) },
            )
        }

        fun JsonElement.text(): String? = (this as? JsonPrimitive)?.content

        fun PartySettingInput.toJson(text: String): JsonPrimitive = when (this) {
            PartySettingInput.Switch -> JsonPrimitive(text.toBoolean())
            is PartySettingInput.Number -> parse(text)?.let(::JsonPrimitive) ?: error("Not a valid number: $text")
            is PartySettingInput.Choice, PartySettingInput.Text -> JsonPrimitive(text)
        }
    }
}
