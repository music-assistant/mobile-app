package io.music_assistant.client.data.model.server

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Pins the AI Radio gates against the server's builtin role table. The scopes below are the
 * ones `ROLE_SCOPES` actually grants.
 */
class AiRadioSupportTest {
    private val roleScopes = mapOf(
        "admin" to listOf("*"),
        "user" to listOf(
            "library.read",
            "library.write",
            "players.control",
            "queues.control",
            "config.providers.read",
        ),
        "guest" to listOf("library.read", "players.control", "queues.control"),
    )

    private fun grantedStations(role: String?) =
        AI_RADIO_STATIONS_SCOPES.all { grantsScope(roleScopes, role, it) }

    @Test
    fun everyBuiltinRoleMayListAndPlayStations() {
        // A show plays like any radio, so the write scope that start/stop needed is gone.
        assertTrue(grantedStations("admin"))
        assertTrue(grantedStations("user"))
        assertTrue(grantedStations("guest"))
    }

    @Test
    fun aRoleWithoutTheQueueScopeCannotPlayAStation() {
        val readOnly = mapOf("viewer" to listOf("library.read"))
        assertFalse(AI_RADIO_STATIONS_SCOPES.all { grantsScope(readOnly, "viewer", it) })
    }

    @Test
    fun customRoleAbsentFromTheTableGrantsNothing() {
        assertFalse(grantedStations("kiosk"))
    }

    @Test
    fun unknownRoleIsDenied() {
        assertFalse(grantedStations(null))
    }

    private fun grantedQueueDj(role: String?) =
        AI_RADIO_QUEUE_DJ_SCOPES.all { grantsScope(roleScopes, role, it) }

    @Test
    fun userMayUseTheQueueDj() {
        assertTrue(grantedQueueDj("user"))
        assertTrue(grantedQueueDj("admin"))
    }

    @Test
    fun guestLacksTheProviderReadScopeTheHostListNeeds() {
        assertFalse(grantedQueueDj("guest"))
        assertFalse(grantedQueueDj(null))
    }

    @Test
    fun onlyTheRadioItemApiIsSupported() {
        assertFalse(supportsAiRadio(null))
        assertFalse(supportsAiRadio(87))
        assertTrue(supportsAiRadio(88))
    }
}
