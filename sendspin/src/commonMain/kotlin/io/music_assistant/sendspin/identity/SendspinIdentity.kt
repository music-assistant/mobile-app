package io.music_assistant.sendspin.identity

import io.music_assistant.sendspin.noise.SendspinBase64
import io.music_assistant.sendspin.noise.crypto.X25519KeyPair

/**
 * The device's long-lived Sendspin identity: a static X25519 keypair whose
 * public key *is* the encrypted-protocol `client_id` (base64url, no padding,
 * 43 chars). Servers key persistence (groups, settings, pairing records) on
 * it, so it must be stable across restarts; losing it (reinstall/restore)
 * makes this device appear as a new player.
 */
class SendspinIdentity(val keyPair: X25519KeyPair) {
    /** base64url (no padding) form of the static public key. */
    val clientId: String = SendspinBase64.encode(keyPair.publicKey)
}
