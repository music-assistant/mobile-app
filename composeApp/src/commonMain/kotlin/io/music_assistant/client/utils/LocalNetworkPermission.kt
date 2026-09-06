package io.music_assistant.client.utils

import org.jetbrains.compose.resources.StringResource

data class LocalNetworkOnboardingResources(
    val title: StringResource,
    val body: StringResource,
)

/** Connection orchestration stays in common code; actuals own probing and error classification. */
expect class LocalNetworkPermissionGate() {
    val isAvailable: Boolean
    val onboardingResources: LocalNetworkOnboardingResources?
    suspend fun probe(timeoutMs: Long): Boolean?
    fun isLikelyLocalNetworkBlocked(error: Throwable): Boolean
    fun guidanceFor(
        error: Throwable?,
        probeGranted: Boolean?,
        locallyBlocked: Boolean,
    ): StringResource?
}
