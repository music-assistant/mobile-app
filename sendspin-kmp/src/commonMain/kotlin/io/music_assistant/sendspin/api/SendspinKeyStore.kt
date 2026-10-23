package io.music_assistant.sendspin.api

/**
 * Byte-blob persistence for the Sendspin identity and trust state.
 *
 * The app provides the implementation over its own settings storage. The
 * identity secrets can deliberately share storage with the app's server
 * credentials: a separate hardened vault would protect the least valuable
 * secrets in the app while adding platform-keystore failure modes.
 *
 * Reads of missing or undecodable data return null and never throw: storage
 * loss must lead to clean identity regeneration, not a crash.
 */
public interface SendspinKeyStore {
    /** Returns the stored bytes, or null when absent or unreadable. */
    public fun read(key: String): ByteArray?

    /** Stores [value] under [key], replacing any previous value. */
    public fun write(key: String, value: ByteArray)

    public fun delete(key: String)
}
