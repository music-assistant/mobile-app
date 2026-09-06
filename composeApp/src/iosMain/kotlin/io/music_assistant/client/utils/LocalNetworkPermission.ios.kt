package io.music_assistant.client.utils

import co.touchlab.kermit.Logger
import kotlinx.coroutines.suspendCancellableCoroutine
import musicassistantclient.composeapp.generated.resources.Res
import musicassistantclient.composeapp.generated.resources.settings_error_local_network_ios
import musicassistantclient.composeapp.generated.resources.settings_error_offline_ios
import musicassistantclient.composeapp.generated.resources.settings_local_network_onboarding_body_ios
import musicassistantclient.composeapp.generated.resources.settings_local_network_onboarding_title_ios
import org.jetbrains.compose.resources.StringResource
import kotlin.coroutines.resume

interface LocalNetworkPermissionProber {
    fun probe(timeoutMs: Long, completion: (Boolean?) -> Unit)
}

private class DefaultLocalNetworkPermissionProber : LocalNetworkPermissionProber {
    override fun probe(timeoutMs: Long, completion: (Boolean?) -> Unit) = completion(true)
}

private const val MAX_CAUSE_DEPTH = 8
private val gateLog = Logger.withTag("LocalNetworkGate")

actual class LocalNetworkPermissionGate actual constructor() {
    private var prober: LocalNetworkPermissionProber = DefaultLocalNetworkPermissionProber()

    actual val isAvailable: Boolean = true
    actual val onboardingResources: LocalNetworkOnboardingResources? = LocalNetworkOnboardingResources(
        title = Res.string.settings_local_network_onboarding_title_ios,
        body = Res.string.settings_local_network_onboarding_body_ios,
    )

    fun setProber(prober: LocalNetworkPermissionProber) {
        this.prober = prober
    }

    actual suspend fun probe(timeoutMs: Long): Boolean? {
        val proberName = prober::class.simpleName
        val start = currentTimeMillis()
        gateLog.i { "probe start (prober=$proberName, timeoutMs=$timeoutMs)" }
        val result = suspendCancellableCoroutine { cont ->
            prober.probe(timeoutMs) { result ->
                if (cont.isActive) cont.resume(result)
            }
        }
        gateLog.i { "probe result=$result in ${currentTimeMillis() - start}ms" }
        return result
    }

    actual fun isLikelyLocalNetworkBlocked(error: Throwable): Boolean {
        var current: Throwable? = error
        var depth = 0
        while (current != null && depth < MAX_CAUSE_DEPTH) {
            current.message?.let { message ->
                if (message.contains("NSURLErrorDomain") &&
                    (message.contains("Code=1009") || message.contains("Code=-1009"))
                ) {
                    return true
                }
                if (message.contains("The internet connection appears to be offline")) {
                    return true
                }
            }
            current = current.cause
            depth++
        }
        return false
    }

    actual fun guidanceFor(
        error: Throwable?,
        probeGranted: Boolean?,
        locallyBlocked: Boolean,
    ): StringResource? {
        if (locallyBlocked) return Res.string.settings_error_local_network_ios
        if (error != null && isLikelyLocalNetworkBlocked(error)) {
            return if (probeGranted == true) {
                Res.string.settings_error_offline_ios
            } else {
                Res.string.settings_error_local_network_ios
            }
        }
        return null
    }
}
