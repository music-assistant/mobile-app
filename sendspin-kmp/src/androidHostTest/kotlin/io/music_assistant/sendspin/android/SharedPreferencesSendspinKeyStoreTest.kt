package io.music_assistant.sendspin.android

import android.content.Context
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertNull

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SharedPreferencesSendspinKeyStoreTest {
    private val context: Context = RuntimeEnvironment.getApplication()

    // The file that holds the player identity of existing installs: a rename loses it.
    private val prefs = context.getSharedPreferences("io.music_assistant.sendspin.keys", Context.MODE_PRIVATE)

    @Test
    fun aWrittenValueReadsBackInANewStore() {
        val value = ByteArray(256) { it.toByte() }
        SharedPreferencesSendspinKeyStore(context).write("identity", value)

        assertContentEquals(value, SharedPreferencesSendspinKeyStore(context).read("identity"))
    }

    @Test
    fun aMissingKeyReadsAsNull() {
        assertNull(SharedPreferencesSendspinKeyStore(context).read("identity"))
    }

    @Test
    fun aDeletedKeyReadsAsNull() {
        val store = SharedPreferencesSendspinKeyStore(context)
        store.write("identity", byteArrayOf(1, 2, 3))

        store.delete("identity")

        assertNull(store.read("identity"))
    }

    @Test
    fun anUndecodableValueReadsAsNull() {
        prefs.edit().putString("text", "not base64 !").putInt("number", 1).commit()
        val store = SharedPreferencesSendspinKeyStore(context)

        assertNull(store.read("text"), "a value that is not Base64")
        assertNull(store.read("number"), "a value that is not a string")
    }
}
