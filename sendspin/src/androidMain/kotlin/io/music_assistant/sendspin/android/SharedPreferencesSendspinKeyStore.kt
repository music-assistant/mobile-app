package io.music_assistant.sendspin.android

import android.content.Context
import io.music_assistant.sendspin.api.SendspinKeyStore
import kotlin.io.encoding.Base64

/**
 * [SendspinKeyStore] over a private [android.content.SharedPreferences] file, Base64
 * encoded. The security posture is the one the [SendspinKeyStore] KDoc documents.
 */
public class SharedPreferencesSendspinKeyStore(context: Context) : SendspinKeyStore {
    private val prefs = context.applicationContext.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    override fun read(key: String): ByteArray? = runCatching {
        prefs.getString(key, null)?.let { Base64.decode(it) }
    }.getOrNull()

    override fun write(key: String, value: ByteArray) {
        prefs.edit().putString(key, Base64.encode(value)).apply()
    }

    override fun delete(key: String) {
        prefs.edit().remove(key).apply()
    }

    private companion object {
        const val FILE_NAME = "io.music_assistant.sendspin.keys"
    }
}
