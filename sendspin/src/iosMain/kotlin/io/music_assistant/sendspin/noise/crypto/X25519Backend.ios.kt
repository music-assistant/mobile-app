package io.music_assistant.sendspin.noise.crypto

import dev.whyoleg.cryptography.CryptographyProvider

/** CryptoKit registers X25519 on every supported iOS version. */
internal actual fun defaultX25519Backend(provider: CryptographyProvider): X25519Backend =
    XdhX25519Backend(provider)
