package io.music_assistant.client.utils

import co.touchlab.kermit.Logger
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

private val gateLog = Logger.withTag("LocalNetworkGate")

/**
 * iOS gates local network access behind a runtime permission; Android does not.
 */
expect val localNetworkPermissionGateExists: Boolean

/**
 * Probes the local-network permission: true = granted, false = denied, null = inconclusive.
 * On iOS an undetermined state raises the system prompt and the probe waits out the answer.
 * An interface, not `expect class`: Swift conforms to the exported Obj-C protocol.
 */
interface LocalNetworkPermissionProber {
    fun probe(timeoutMs: Long, completion: (Boolean?) -> Unit)
}

/** Reports "granted" — correct for Android; iOS replaces it with a real prober at bootstrap. */
class DefaultLocalNetworkPermissionProber : LocalNetworkPermissionProber {
    override fun probe(timeoutMs: Long, completion: (Boolean?) -> Unit) = completion(true)
}

/**
 * Owner of the active [LocalNetworkPermissionProber]. A mutable holder rather than a
 * Koin definition swap so instances resolved before the Swift registration still see it.
 */
class LocalNetworkPermissionGate {
    var prober: LocalNetworkPermissionProber = DefaultLocalNetworkPermissionProber()

    suspend fun probe(timeoutMs: Long): Boolean? {
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
}
