package io.music_assistant.client.data.model.server

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Pins the AI Radio gate against the server's builtin role table. The scopes below are the
 * ones `ROLE_SCOPES` actually grants, so a role that cannot start a station must never see
 * the picker.
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

    private fun granted(role: String?) =
        grantsScope(roleScopes, role, AI_RADIO_REQUIRED_SCOPE)

    @Test
    fun adminIsGrantedThroughTheWildcardScope() {
        assertTrue(granted("admin"))
    }

    @Test
    fun userMayReadProviderConfigButStillCannotRunAStation() {
        // The trap this gate exists for: `user` can list stations, so gating on the plugin
        // alone would render a picker where every tap fails.
        assertFalse(granted("user"))
    }

    @Test
    fun guestIsDenied() {
        assertFalse(granted("guest"))
    }

    @Test
    fun customRoleAbsentFromTheTableGrantsNothing() {
        assertFalse(granted("kiosk"))
    }

    @Test
    fun unknownRoleIsDenied() {
        assertFalse(granted(null))
    }

    @Test
    fun anExplicitScopeGrantIsHonouredWithoutTheWildcard() {
        // Guards the intent: if the server ever moves start/stop to a scope a plain user
        // holds, the gate opens with no client change.
        val widened = mapOf("user" to listOf("config.providers.read", AI_RADIO_REQUIRED_SCOPE))
        assertTrue(grantsScope(widened, "user", AI_RADIO_REQUIRED_SCOPE))
    }
}
