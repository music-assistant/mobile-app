package io.music_assistant.sendspin.session

import io.music_assistant.sendspin.noise.PskCategory

/**
 * The spec's activation admission rules, in order: pairing_required only when
 * enabling unpaired access would have admitted the activation; every other
 * inadmissible activation is unauthorized; an admissible pairing activation
 * with an unsupported method is answered with pair/abort and stays open.
 */
internal object ActivationPolicy {
    sealed interface Decision {
        data class Admit(val activeRoles: List<String>, val pairing: Boolean) : Decision
        data class Reject(val goodbyeReason: String) : Decision
        data object AbortPairing : Decision
    }

    class Input(
        val category: PskCategory,
        val activities: Set<String>,
        val explicitRoles: List<String>?,
        /** Roles granted by an earlier activation on these keys. */
        val persistedRoles: List<String>,
        val pairingMethod: String?,
        val unpairedAccessEnabled: Boolean,
        val pairingPskEnabled: Boolean,
    )

    const val ACTIVITY_PAIRING = "pairing"
    const val ACTIVITY_PLAYBACK = "playback"
    const val ACTIVITY_MANAGEMENT = "management"
    const val PAIR_METHOD_PSK = "pairing_psk"
    const val GOODBYE_PAIRING_REQUIRED = "pairing_required"
    const val GOODBYE_UNAUTHORIZED = "unauthorized"
    private const val ROLE_SOURCE_V1 = "source@v1"
    private val PLAYBACK_MANAGEMENT = setOf(ACTIVITY_PLAYBACK, ACTIVITY_MANAGEMENT)

    fun decide(input: Input): Decision {
        val trust = TrustLevel.of(input.category)
        val allowed = allowedActivities(input.category, input.activities, input.unpairedAccessEnabled)
        val playbackCapable = allowed &&
            allowedActivities(input.category, input.activities + ACTIVITY_PLAYBACK, input.unpairedAccessEnabled)
        val explicit = input.explicitRoles
        val effectiveRoles = explicit ?: if (playbackCapable) input.persistedRoles else emptyList()
        val rolesOk = !(trust == TrustLevel.NONE && ROLE_SOURCE_V1 in effectiveRoles)

        val admissible = allowed && (explicit.isNullOrEmpty() || playbackCapable) && rolesOk
        if (!admissible) {
            val admissibleWithUnpairedAccess =
                allowedActivities(input.category, input.activities, unpairedAccess = true) &&
                    (
                        explicit.isNullOrEmpty() ||
                            allowedActivities(input.category, input.activities + ACTIVITY_PLAYBACK, unpairedAccess = true)
                        ) &&
                    rolesOk
            val pairingRequired = input.category == PskCategory.SENTINEL &&
                !input.unpairedAccessEnabled &&
                admissibleWithUnpairedAccess
            return Decision.Reject(if (pairingRequired) GOODBYE_PAIRING_REQUIRED else GOODBYE_UNAUTHORIZED)
        }

        val pairing = ACTIVITY_PAIRING in input.activities
        if (pairing) {
            val method = input.pairingMethod
            val methodMatchesPsk = (method == PAIR_METHOD_PSK) == (input.category == PskCategory.PAIRING)
            val methodOffered = method == PAIR_METHOD_PSK && input.pairingPskEnabled
            if (method == null || !methodMatchesPsk || !methodOffered) return Decision.AbortPairing
        }
        return Decision.Admit(effectiveRoles, pairing)
    }

    private fun allowedActivities(
        category: PskCategory,
        activities: Set<String>,
        unpairedAccess: Boolean,
    ): Boolean = when (category) {
        PskCategory.LONG_TERM_STORED, PskCategory.LONG_TERM_SHARED ->
            activities == setOf(ACTIVITY_PAIRING) || PLAYBACK_MANAGEMENT.containsAll(activities)

        PskCategory.PAIRING -> activities == setOf(ACTIVITY_PAIRING)

        PskCategory.SENTINEL ->
            activities.isEmpty() ||
                activities == setOf(ACTIVITY_PAIRING) ||
                (activities == setOf(ACTIVITY_PLAYBACK) && unpairedAccess)
    }
}
