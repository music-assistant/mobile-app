package io.music_assistant.client.data.model.server

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Provider domain of the Party plugin, as reported by the `providers` command. */
const val PARTY_DOMAIN = "party"

/**
 * The fields of `party/config` that the host invite shows. The server sends more (the guest
 * limits, the badge colors, the mode); the guest view would need them, the invite does not.
 */
@Serializable
data class ServerPartyConfig(
    @SerialName("party_name") val partyName: String? = null,
    @SerialName("qr_text") val qrText: String? = null,
)
