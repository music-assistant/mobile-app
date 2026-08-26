package io.music_assistant.client.data.model.server

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Highest server schema_version this client is built and tested against. */
const val LOCAL_SCHEMA_VERSION = 59

@Serializable
data class ServerInfo(
    @SerialName("server_id") var serverId: String,
    @SerialName("server_version") var serverVersion: String? = null,
    @SerialName("schema_version") var schemaVersion: Int? = null,
    @SerialName("min_supported_schema_version") var minSupportedSchemaVersion: Int? = null,
    /** Deprecated in favour of [internalUrl]. Old servers send only this field. */
    @SerialName("base_url") var baseUrl: String? = null,
    @SerialName("name") var name: String? = null,
    @SerialName("internal_url") var internalUrl: String? = null,
    @SerialName("external_url") var externalUrl: String? = null,
    @SerialName("has_remote_access") var hasRemoteAccess: Boolean = false,
    // @SerialName("homeassistant_addon") var homeassistantAddon: Boolean? = null,
    // @SerialName("onboard_done") var onboardDone: Boolean? = null
)
