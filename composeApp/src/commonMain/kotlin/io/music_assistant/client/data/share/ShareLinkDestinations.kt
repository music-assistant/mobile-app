package io.music_assistant.client.data.share

import io.ktor.http.URLProtocol
import io.ktor.http.Url
import io.music_assistant.client.utils.myJson
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.io.encoding.Base64

private const val MAX_PAYLOAD_SIZE = 65_536
private val base64Url = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT)
private val payloadCharacters = Regex("[A-Za-z0-9_-]+")
private val httpsUrl = Regex("""https://[^/\s\\][^\s\\]*""", RegexOption.IGNORE_CASE)

@Serializable
private data class ShareLinkPayload(
    val type: String,
    val title: String,
    @SerialName("provider_urls") val providerUrls: Map<String, String> = emptyMap(),
)

internal fun providerLinksFromShareUrl(url: String): Map<String, String> {
    val fragment = url.substringAfter('#', "")
    if (fragment.length !in 2..MAX_PAYLOAD_SIZE || fragment.first() != '1') return emptyMap()
    val encoded = fragment.drop(1)
    if (!payloadCharacters.matches(encoded)) return emptyMap()
    return runCatching {
        val compressed = base64Url.decode(encoded)
        require(base64Url.encode(compressed) == encoded)
        val bytes = inflateSharePayload(compressed, MAX_PAYLOAD_SIZE) ?: return emptyMap()
        val payload = myJson.decodeFromString<ShareLinkPayload>(bytes.decodeToString(throwOnInvalidSequence = true))
        require(payload.title.isNotBlank() && payload.type in setOf("track", "album", "artist"))
        payload.providerUrls.filterValues(::isPublicShareUrl)
    }.getOrDefault(emptyMap())
}

private fun isPublicShareUrl(raw: String): Boolean =
    httpsUrl.matches(raw) && runCatching {
        val url = Url(raw)
        url.protocol == URLProtocol.HTTPS && url.host.isNotBlank() && url.user == null && url.password == null
    }.getOrDefault(false)

internal expect fun inflateSharePayload(input: ByteArray, maxOutput: Int): ByteArray?
