package io.music_assistant.client.settings

import com.russhwolf.settings.Settings
import io.music_assistant.sendspin.identity.SendspinKeyStore
import kotlin.io.encoding.Base64

/**
 * [SendspinKeyStore] over the app's shared [Settings] storage. Deliberately the
 * same storage as the MA auth token: a separate hardened vault would protect
 * the least valuable secrets in the app while adding keystore failure modes.
 */
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
