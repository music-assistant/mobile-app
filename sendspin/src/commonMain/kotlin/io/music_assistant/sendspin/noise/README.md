# Sendspin Noise layer

Implements the encrypted Sendspin connection's cryptographic core:
`Noise_KKpsk2_25519_ChaChaPoly_SHA256` (Noise Protocol Framework rev. 34),
with the server as Noise initiator and this client as responder, regardless of
which side opened the WebSocket.

## Spec mapping

| Spec section | Code |
| --- | --- |
| connection.md §Encryption / §Pattern / §Cipher Suites | `NoiseProtocol.kt` |
| connection.md §Pre-Shared Key (psk_id, sentinel, categories) | `SendspinPsk.kt` |
| connection.md §Prologue, §Failure Handling; messaging.md §client/init, §server/init, §noise/handshake | `SendspinHandshake.kt` |
| messaging.md §Binary Message ID Structure, §Fragmentation | `NoiseFraming.kt` |
| pairing.md §Pairing Token | `../pairing/PairingToken.kt` |
| crypto primitives | `crypto/NoiseCrypto.kt` (interface), `crypto/CryptographyKotlinNoiseCrypto.kt` |

## Provenance

The Noise state machines (`CipherState`, `SymmetricState`, `HandshakeState`)
were written greenfield against the Noise specification. The JVM-only
`sander/noise-kotlin` library (MIT) was assessed for vendoring and used as a
design reference for the state-machine split, but no code was reused: it
implements neither PSK tokens nor the KK pattern.

Primitives come from cryptography-kotlin (pinned, pre-1.0), which wraps
OS-native crypto: the JDK/JCA provider on Android and CryptoKit on iOS. Note
that on iOS the `cryptography-provider-cryptokit` artifact is required — the
`cryptography-provider-apple` (CommonCrypto) artifact does not register XDH or
ChaCha20-Poly1305. The `NoiseCrypto` interface exists so the backend is
swappable (e.g. libsodium bindings) and so tests can inject fixed keys.

## Test vectors

Correctness is pinned by reference vectors in
`commonTest/.../noise/NoiseTestVectors.kt`, covering
`Noise_KKpsk2_25519_ChaChaPoly_SHA256` plus `KK` and `NNpsk2` siblings — all
handshake messages, transport ciphertexts, and the final handshake hash, in
both roles (`NoiseKkpsk2VectorTest`). **Do not modify `NoiseProtocol.kt`
without re-running them** (they are part of the common test suite, executed
by the Android host-test and iOS simulator test lanes).

To regenerate the fixture:

1. Fetch the cacophony vector set distributed with snow:
   `https://raw.githubusercontent.com/mcginty/snow/main/tests/vectors/cacophony.txt`
   (the sibling `snow.txt` set contains no PSK-pattern vectors).
2. Filter to the relevant protocols:
   `jq '{vectors: [.vectors[] | select(.protocol_name | test("^Noise_(KKpsk2|KK|NNpsk2)_25519_ChaChaPoly_SHA256$"))]}' cacophony.txt`
3. Embed the resulting JSON in the `NoiseTestVectors.json` raw string.

Published constants (sentinel PSK, sentinel psk_id, the pairing-token
reference vector) are asserted against the spec's literal values in
`PskIdDerivationTest` and `PairingTokenCodecTest`.
