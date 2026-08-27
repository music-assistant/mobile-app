package io.music_assistant.client.settings

import com.russhwolf.settings.MapSettings
import io.music_assistant.client.api.ConnectionInfo
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SecretsMigrationTest {
    private fun legacyStore() = MapSettings(
        "host" to "nas.local",
        "port" to 8095,
        "isTls" to true,
        // Address-keyed credentials of an older version. Both must go.
        "token_direct:wss://nas.local:8095" to "address-token",
        "id_direct:wss://nas.local:8095" to "server-id",
        // Server-id-keyed token of the current format. It must survive.
        "token_$SERVER_ID" to "secret-token",
        "webrtc_remote_id" to "remote-abc",
        "last_connection_mode" to "direct",
        "connection_history" to "[]",
        "sendspin_host" to "192.168.1.9",
        "theme" to "Dark",
    )

    @Test
    fun movesSecretsOutOfTheGeneralStore() {
        val settings = legacyStore()
        val secrets = MapSettings()

        SettingsRepository(settings, secrets)

        listOf(
            "host",
            "port",
            "isTls",
            "token_$SERVER_ID",
            "webrtc_remote_id",
            "last_connection_mode",
            "connection_history",
            "sendspin_host",
        ).forEach { key ->
            assertFalse(settings.hasKey(key), "$key must not stay in the backed-up store")
            assertTrue(secrets.hasKey(key), "$key must move to the secrets store")
        }
    }

    @Test
    fun purgesAddressKeyedCredentials() {
        val settings = legacyStore()
        val secrets = MapSettings(
            // A restored backup can put a legacy key straight into the secrets store.
            "token_direct:wss://other.local:8095" to "another-address-token",
        )

        val repo = SettingsRepository(settings, secrets)

        listOf(
            "token_direct:wss://nas.local:8095",
            "id_direct:wss://nas.local:8095",
            "token_direct:wss://other.local:8095",
        ).forEach { key ->
            assertFalse(settings.hasKey(key), "$key must be dropped from the general store")
            assertFalse(secrets.hasKey(key), "$key must be dropped from the secrets store")
        }
        assertNull(repo.getTokenForServer("direct:wss://nas.local:8095"))
    }

    @Test
    fun keepsGeneralPreferencesInPlace() {
        val settings = legacyStore()

        SettingsRepository(settings, MapSettings())

        assertEquals("Dark", settings.getStringOrNull("theme"))
    }

    @Test
    fun readsMigratedValuesBackAfterTheMove() {
        val settings = legacyStore()
        val secrets = MapSettings()

        val repo = SettingsRepository(settings, secrets)

        assertEquals(ConnectionInfo("nas.local", 8095, true), repo.connectionInfo.value)
        assertEquals("secret-token", repo.getTokenForServer(SERVER_ID))
        assertEquals("192.168.1.9", repo.sendspinHost.value)
        assertEquals("remote-abc", repo.webrtcRemoteId.value)
    }

    @Test
    fun runsOnceAndDoesNotOverwriteANewerSecret() {
        val settings = legacyStore()
        val secrets = MapSettings()
        SettingsRepository(settings, secrets)

        // A stale copy comes back, for example from a restored backup of an
        // older version. The newer secret must win.
        settings.putString("token_$SERVER_ID", "stale-token")
        val repo = SettingsRepository(settings, secrets)

        assertEquals("secret-token", repo.getTokenForServer(SERVER_ID))
        assertFalse(settings.hasKey("token_$SERVER_ID"))
    }

    @Test
    fun toleratesAnEmptyStore() {
        val settings = MapSettings()
        val repo = SettingsRepository(settings, MapSettings())

        assertNull(repo.connectionInfo.value)
    }

    private companion object {
        const val SERVER_ID = "abc-server-id"
    }
}
