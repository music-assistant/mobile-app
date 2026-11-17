package io.music_assistant.client.discovery

import co.touchlab.kermit.Logger
import io.ktor.http.parseUrl
import io.music_assistant.client.api.ConnectionInfo
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/** DNS-SD type that a Music Assistant server advertises (server 2.x, `controllers/discovery`). */
const val MUSIC_ASSISTANT_SERVICE_TYPE = "_mass._tcp"

/**
 * One service instance as the platform browser reports it. [host] and [port] are the resolved
 * address, or null where the platform reads only the TXT record (iOS).
 */
data class MdnsService(
    val txt: Map<String, String>,
    val host: String? = null,
    val port: Int? = null,
)

/** Browses the local network for one DNS-SD type while the flow is collected. Each value is the current set. */
fun interface MdnsBrowser {
    fun browse(serviceType: String): Flow<List<MdnsService>>
}

data class DiscoveredServer(
    val serverId: String,
    val name: String,
    val connection: ConnectionInfo,
    val isHomeAssistantAddon: Boolean,
)

/** The servers on the local network. A browser failure ends the flow with an empty list. */
fun MdnsBrowser.musicAssistantServers(): Flow<List<DiscoveredServer>> =
    browse(MUSIC_ASSISTANT_SERVICE_TYPE)
        .map { services ->
            services.mapNotNull { it.toDiscoveredServer() }
                .distinctBy { it.serverId }
                .sortedBy { it.name.lowercase() }
        }
        .distinctUntilChanged()
        .catch { e ->
            Logger.withTag("ServerDiscovery").w(e) { "Server discovery failed" }
            emit(emptyList())
        }

/**
 * The TXT record holds the server info of `get_server_info`. Its `base_url` is the address that the
 * server publishes, with the scheme and the base path; the resolved address is the fallback.
 */
internal fun MdnsService.toDiscoveredServer(): DiscoveredServer? {
    val serverId = txt["server_id"]?.takeIf { it.isNotBlank() } ?: return null
    val connection = txt["base_url"]?.let(::connectionFromBaseUrl)
        ?: host?.takeIf { it.isUsableHost() }?.let { h -> port?.let { ConnectionInfo(h, it, isTls = false) } }
        ?: return null
    return DiscoveredServer(
        serverId = serverId,
        name = txt["name"]?.takeIf { it.isNotBlank() } ?: connection.host,
        connection = connection,
        // Python zeroconf writes a bool property as "True" or "False".
        isHomeAssistantAddon = txt["homeassistant_addon"].equals("true", ignoreCase = true),
    )
}

internal fun connectionFromBaseUrl(raw: String): ConnectionInfo? {
    val url = parseUrl(raw.trim()) ?: return null
    val isTls = when (url.protocol.name) {
        "https" -> true
        "http" -> false
        else -> return null
    }
    return url.host.takeIf { it.isUsableHost() }?.let {
        ConnectionInfo(it, url.port, isTls, ConnectionInfo.normalizeBasePath(url.encodedPath))
    }
}

/** `ConnectionInfo` writes the host without brackets, so an IPv6 literal cannot go in. */
private fun String.isUsableHost(): Boolean = isNotBlank() && ':' !in this
