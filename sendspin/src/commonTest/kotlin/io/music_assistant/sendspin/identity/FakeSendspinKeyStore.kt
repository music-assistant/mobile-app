package io.music_assistant.sendspin.identity

import io.music_assistant.sendspin.api.SendspinKeyStore

/** In-memory [SendspinKeyStore] with corruption injection for recovery tests. */
class FakeSendspinKeyStore : SendspinKeyStore {
    val entries = mutableMapOf<String, ByteArray>()
    var writeCount = 0
        private set

    override fun read(key: String): ByteArray? = entries[key]?.copyOf()

    override fun write(key: String, value: ByteArray) {
        writeCount++
        entries[key] = value.copyOf()
    }

    override fun delete(key: String) {
        entries.remove(key)
    }

    fun corrupt(key: String) {
        entries[key] = "corrupted \u0000 blob".encodeToByteArray()
    }
}
