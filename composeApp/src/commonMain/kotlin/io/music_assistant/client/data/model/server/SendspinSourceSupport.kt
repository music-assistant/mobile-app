package io.music_assistant.client.data.model.server

/** The plugin that has the Sendspin sources on the server releases 2.10 and 2.11 beta. */
private const val SOURCE_PLUGIN_DOMAIN = "sendspin_source"

/** The Sendspin player provider, which has the sources itself on a newer server. */
private const val SENDSPIN_DOMAIN = "sendspin"
private const val AUDIO_SOURCE_FEATURE = "audio_source"

/**
 * The provider instance that lists this device as a live input, or null if the server has none.
 * The commits that added the feature did not change the schema version, so the provider list
 * is the only sign of it.
 */
fun sendspinSourceInstanceId(providers: List<ServerProviderInstance>): String? {
    val available = providers.filter { it.available }
    return (
        available.firstOrNull { it.domain == SOURCE_PLUGIN_DOMAIN }
            ?: available.firstOrNull { it.domain == SENDSPIN_DOMAIN && AUDIO_SOURCE_FEATURE in it.supportedFeatures }
        )?.instanceId
}

/** The uri of the live input that the Sendspin client [clientId] is, on the provider [instanceId]. */
fun sendspinSourceUri(instanceId: String, clientId: String): String = "$instanceId://audio_source/$clientId"
