package io.music_assistant.client.utils

import org.jetbrains.compose.resources.StringResource

actual class LocalNetworkPermissionGate actual constructor() {
    actual val isAvailable: Boolean = false
    actual val onboardingResources: LocalNetworkOnboardingResources? = null

    actual suspend fun probe(timeoutMs: Long): Boolean? = true

    actual fun isLikelyLocalNetworkBlocked(error: Throwable): Boolean = false

    actual fun guidanceFor(
        error: Throwable?,
        probeGranted: Boolean?,
        locallyBlocked: Boolean,
    ): StringResource? = null
}
