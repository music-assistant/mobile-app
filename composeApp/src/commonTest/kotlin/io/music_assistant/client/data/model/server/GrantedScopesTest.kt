package io.music_assistant.client.data.model.server

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Pins the write gates against the server's builtin role table (`ROLE_SCOPES`). */
class GrantedScopesTest {
    private val guest = listOf(
        "config.players.read",
        "library.read",
        "players.control",
        "players.read",
        "providers.read",
        "queues.control",
        "queues.read",
    )
    private val roleScopes = mapOf(
        "admin" to listOf("*"),
        "user" to guest + listOf("config.core.read", "config.providers.read", "library.write"),
        "guest" to guest,
        // A 2.11 custom role holds the guest scopes plus the scopes granted to it.
        "dj" to guest + "config.players.write",
    )

    @Test
    fun adminHoldsEveryScopeThroughTheWildcard() {
        val admin = GrantedScopes.of(roleScopes, "admin")
        assertTrue(admin.canEditLibrary)
        assertTrue(admin.canConfigurePlayers)
    }

    @Test
    fun userEditsTheLibraryButNotPlayerSettings() {
        val user = GrantedScopes.of(roleScopes, "user")
        assertTrue(user.canEditLibrary)
        assertFalse(user.canConfigurePlayers)
    }

    @Test
    fun guestSeesNoWriteAction() {
        val guest = GrantedScopes.of(roleScopes, "guest")
        assertFalse(guest.canEditLibrary)
        assertFalse(guest.canConfigurePlayers)
    }

    @Test
    fun customRoleIsGatedOnItsScopesNotItsName() {
        val dj = GrantedScopes.of(roleScopes, "dj")
        assertFalse(dj.canEditLibrary)
        assertTrue(dj.canConfigurePlayers)
    }

    @Test
    fun roleAbsentFromTheTableOrUnknownGrantsNothing() {
        assertFalse(GrantedScopes.of(roleScopes, "kiosk").has(Scope.LIBRARY_READ))
        assertFalse(GrantedScopes.of(roleScopes, null).has(Scope.LIBRARY_READ))
        assertFalse(GrantedScopes.of(emptyMap(), "admin").canEditLibrary)
    }
}
