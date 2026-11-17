package io.music_assistant.client.discovery

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import co.touchlab.kermit.Logger
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.net.Inet4Address
import kotlin.coroutines.resume

/**
 * [MdnsBrowser] on the system mDNS responder. Resolves run one at a time, because before API 34
 * a second `resolveService` fails with `FAILURE_ALREADY_ACTIVE`.
 */
class NsdMdnsBrowser(context: Context) : MdnsBrowser {
    private val nsd = context.getSystemService(NsdManager::class.java)
    private val log = Logger.withTag("NsdMdnsBrowser")

    private sealed interface Change {
        data class Found(val info: NsdServiceInfo) : Change
        data class Lost(val name: String) : Change
    }

    override fun browse(serviceType: String): Flow<List<MdnsService>> = channelFlow {
        val changes = Channel<Change>(Channel.UNLIMITED)
        val listener = object : NsdManager.DiscoveryListener {
            override fun onServiceFound(info: NsdServiceInfo) {
                changes.trySend(Change.Found(info))
            }

            override fun onServiceLost(info: NsdServiceInfo) {
                changes.trySend(Change.Lost(info.serviceName))
            }

            override fun onStartDiscoveryFailed(type: String, errorCode: Int) {
                log.w { "Discovery of $type failed: $errorCode" }
                changes.close()
            }

            override fun onStopDiscoveryFailed(type: String, errorCode: Int) = Unit
            override fun onDiscoveryStarted(type: String) = Unit
            override fun onDiscoveryStopped(type: String) = Unit
        }
        nsd.discoverServices(serviceType, NsdManager.PROTOCOL_DNS_SD, listener)
        launch {
            val services = mutableMapOf<String, MdnsService>()
            send(emptyList())
            for (change in changes) {
                when (change) {
                    is Change.Found -> resolve(change.info)?.let { services[change.info.serviceName] = it }
                    is Change.Lost -> services.remove(change.name)
                }
                send(services.values.toList())
            }
        }
        awaitClose { runCatching { nsd.stopServiceDiscovery(listener) } }
    }

    private suspend fun resolve(info: NsdServiceInfo): MdnsService? = withTimeoutOrNull(RESOLVE_TIMEOUT_MS) {
        suspendCancellableCoroutine { cont ->
            @Suppress("DEPRECATION") // registerServiceInfoCallback needs API 34.
            nsd.resolveService(
                info,
                object : NsdManager.ResolveListener {
                    override fun onServiceResolved(resolved: NsdServiceInfo) {
                        if (cont.isActive) cont.resume(resolved.toMdnsService())
                    }

                    override fun onResolveFailed(failed: NsdServiceInfo, errorCode: Int) {
                        log.w { "Resolve of ${failed.serviceName} failed: $errorCode" }
                        if (cont.isActive) cont.resume(null)
                    }
                },
            )
        }
    }

    @Suppress("DEPRECATION") // getHostAddresses needs API 34.
    private fun NsdServiceInfo.toMdnsService(): MdnsService = MdnsService(
        txt = attributes.mapValues { (_, value) -> value?.decodeToString().orEmpty() },
        host = (host as? Inet4Address)?.hostAddress,
        port = port,
    )

    private companion object {
        const val RESOLVE_TIMEOUT_MS = 5_000L
    }
}
