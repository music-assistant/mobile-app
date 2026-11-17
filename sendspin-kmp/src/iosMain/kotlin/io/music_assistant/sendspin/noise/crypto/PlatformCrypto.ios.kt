package io.music_assistant.sendspin.noise.crypto

import dev.whyoleg.cryptography.CryptographyProvider

/** CryptoKit registers X25519 and ChaCha20-Poly1305 on every supported iOS version. */
internal actual fun platformCrypto(provider: CryptographyProvider): PlatformCrypto =
    PlatformCrypto(x25519 = XdhX25519Backend(provider), aead = CryptographyKotlinAead(provider))
