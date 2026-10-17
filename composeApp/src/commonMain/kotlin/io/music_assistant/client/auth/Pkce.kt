package io.music_assistant.client.auth

import dev.whyoleg.cryptography.CryptographyProvider
import dev.whyoleg.cryptography.algorithms.SHA256
import dev.whyoleg.cryptography.random.CryptographyRandom
import kotlin.io.encoding.Base64

/** A PKCE (RFC 7636) code verifier and its S256 code challenge. */
internal class Pkce private constructor(val verifier: String, val challenge: String) {
    companion object {
        suspend fun create(): Pkce {
            val verifier = base64Url.encode(CryptographyRandom.nextBytes(VERIFIER_BYTES))
            return Pkce(verifier, challengeOf(verifier))
        }

        suspend fun challengeOf(verifier: String): String = base64Url.encode(
            CryptographyProvider.Default.get(SHA256).hasher().hash(verifier.encodeToByteArray()),
        )
    }
}

private val base64Url = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT)

/** 32 random bytes encode to the 43-character minimum verifier, as the web frontend sends. */
private const val VERIFIER_BYTES = 32
