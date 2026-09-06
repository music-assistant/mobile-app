package io.music_assistant.sendspin.identity

/**
 * Byte-blob persistence for the Sendspin identity and trust state.
 *
 * The app provides the implementation over its own settings storage. The
 * identity secrets deliberately share storage with the MA auth token: a
 * separate hardened vault would protect the least valuable secrets in the app
 * while adding platform-keystore failure modes.
 *
 * Reads of missing or undecodable data return null and never throw: storage
 * loss must lead to clean identity regeneration, not a crash.
 */
interface SendspinKeyStore {
    /** Returns the stored bytes, or null when absent or unreadable. */
    fun read(key: String): ByteArray?

    /** Stores [value] under [key], replacing any previous value. */
    fun write(key: String, value: ByteArray)

    fun delete(key: String)
}
