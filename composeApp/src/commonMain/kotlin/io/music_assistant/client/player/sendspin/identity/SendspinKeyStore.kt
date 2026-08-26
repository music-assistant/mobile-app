package io.music_assistant.client.player.sendspin.identity

import com.russhwolf.settings.Settings
import kotlin.io.encoding.Base64

/**
 * Byte-blob persistence for the Sendspin identity and trust state.
 *
 * Deliberately backed by the same app settings storage as everything else
 * (SharedPreferences on Android, NSUserDefaults on iOS) — the MA auth token
 * already lives there, so a separate hardened vault for the Sendspin PSKs
 * would protect the least valuable secrets in the app while adding
 * platform-keystore failure modes. If secrets ever move to secure storage,
 * they should all move together.
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

/** [SendspinKeyStore] over the app's shared [Settings] storage. */
class SettingsSendspinKeyStore(private val settings: Settings) : SendspinKeyStore {
    override fun read(key: String): ByteArray? {
        val encoded = settings.getStringOrNull(key) ?: return null
        return try {
            Base64.decode(encoded)
        } catch (_: IllegalArgumentException) {
            null
        }
    }

    override fun write(key: String, value: ByteArray) {
        settings.putString(key, Base64.encode(value))
    }

    override fun delete(key: String) {
        settings.remove(key)
    }
}
