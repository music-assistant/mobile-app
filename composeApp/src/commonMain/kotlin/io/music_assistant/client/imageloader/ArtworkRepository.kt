package io.music_assistant.client.imageloader

import io.music_assistant.client.api.ServiceClient
import io.music_assistant.client.utils.HasConnectionData
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class ArtworkToken internal constructor(
    internal val identity: ArtworkIdentity,
    internal val digest: String,
)

internal enum class ArtworkSource { DISK, NETWORK }

internal data class ArtworkResult(
    val bytes: ByteArray,
    val mimeType: String?,
    val digest: String,
    val reusable: Boolean,
    val source: ArtworkSource,
    val token: ArtworkToken,
)

internal enum class ArtworkReadPolicy(val canRead: Boolean, val canWrite: Boolean) {
    READ_WRITE(canRead = true, canWrite = true),
    READ_ONLY(canRead = true, canWrite = false),
    WRITE_ONLY(canRead = false, canWrite = true),
    DISABLED(canRead = false, canWrite = false),
}

internal class ArtworkRepository(
    private val store: ArtworkDiskStore,
    private val transport: ArtworkTransport,
    private val serviceClient: ServiceClient,
    private val now: () -> Long,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) {
    private val mutex = Mutex()
    private val flights = mutableMapOf<ArtworkFlightKey, ArtworkFlight>()

    suspend fun load(url: String, policy: ArtworkReadPolicy = ArtworkReadPolicy.READ_WRITE): ArtworkResult {
        val context = captureContext(url)
        val identity = identityFor(url, context)
        if (policy.canRead) {
            store.read(identity, now())?.let {
                ArtworkDiagnostics.logProvenance("disk", identity.key, it.digest)
                return it.toResult(true, ArtworkSource.DISK)
            }
        }
        if (policy == ArtworkReadPolicy.READ_ONLY) {
            ArtworkDiagnostics.cacheFailure("read-only-miss", identity.key)
            error("artwork cache miss")
        }

        val key = ArtworkFlightKey(identity, policy)
        val flight = mutex.withLock {
            flights[key]?.also {
                it.waiters++
                ArtworkDiagnostics.logJoined(identity.key)
            } ?: ArtworkFlight(scope.async { fetchAndPersist(url, context, identity, policy) }).also {
                it.waiters = 1
                flights[key] = it
            }
        }
        try {
            return flight.deferred.await()
        } catch (error: CancellationException) {
            val cancel = mutex.withLock {
                flight.waiters--
                if (flight.waiters == 0 && flights[key] === flight) {
                    flights.remove(key)
                    true
                } else {
                    false
                }
            }
            if (cancel) flight.deferred.cancel()
            throw error
        } catch (error: Throwable) {
            ArtworkDiagnostics.cacheFailure("load", identity.key)
            throw error
        } finally {
            mutex.withLock {
                if (flight.deferred.isCompleted && flights[key] === flight) flights.remove(key)
            }
        }
    }

    suspend fun invalidate(token: ArtworkToken): Boolean = store.invalidate(token.identity, token.digest)

    private suspend fun fetchAndPersist(
        url: String,
        context: ArtworkRequestContext,
        identity: ArtworkIdentity,
        policy: ArtworkReadPolicy,
    ): ArtworkResult {
        val response = transport.fetch(url, context)
        val fetchedAt = now()
        val headers = response.headers
        val freshness = artworkFreshness(headers, fetchedAt)
        val digest = artworkSha256Hex(response.bytes)
        ArtworkDiagnostics.logProvenance("network", identity.key, digest)
        if (!freshness.reusable) {
            return ArtworkResult(
                response.bytes,
                response.headers["Content-Type"],
                digest,
                false,
                ArtworkSource.NETWORK,
                ArtworkToken(identity, digest),
            )
        }
        if (!policy.canWrite) {
            return ArtworkResult(
                response.bytes,
                response.headers["Content-Type"],
                digest,
                false,
                ArtworkSource.NETWORK,
                ArtworkToken(identity, digest),
            )
        }
        val stored = store.write(
            identity,
            response.bytes,
            response.headers["Content-Type"],
            fetchedAt,
            freshness.expiresAtMs,
            digest,
        )
        if (stored == null) ArtworkDiagnostics.cacheFailure("write", identity.key)
        return (stored ?: StoredArtwork(identity, response.bytes, response.headers["Content-Type"], digest, freshness.expiresAtMs))
            .toResult(stored != null, ArtworkSource.NETWORK)
    }

    private fun captureContext(url: String): ArtworkRequestContext {
        if (!url.startsWith("mawebrtc://")) return ArtworkRequestContext(null, null)
        val initialState = serviceClient.sessionState.value as? HasConnectionData
        val initialId = initialState?.serverInfo?.serverId
            ?: error("WebRTC server identity unavailable")
        val initialProxy = serviceClient.webRTCHttpProxy
        val finalState = serviceClient.sessionState.value as? HasConnectionData
        val finalId = finalState?.serverInfo?.serverId
        val finalProxy = serviceClient.webRTCHttpProxy
        check(initialId == finalId && initialProxy === finalProxy) {
            "WebRTC artwork connection changed while capturing context"
        }
        return ArtworkRequestContext(initialId, initialProxy)
    }

    private suspend fun identityFor(url: String, context: ArtworkRequestContext): ArtworkIdentity {
        val qualification = if (url.startsWith(
                "mawebrtc://",
            )
        ) {
                context.serverId ?: error("missing server identity")
            } else {
                null
            }
        val raw = if (qualification == null) url else "$qualification\u0000$url"
        val key = "artwork-v1-" + artworkSha256Hex(raw.encodeToByteArray())
        return ArtworkIdentity(key, url, qualification)
    }

    private fun StoredArtwork.toResult(reusable: Boolean, source: ArtworkSource) = ArtworkResult(
        bytes = bytes.copyOf(),
        mimeType = mimeType,
        digest = digest,
        reusable = reusable,
        source = source,
        token = ArtworkToken(identity, digest),
    )

    private class ArtworkFlight(val deferred: Deferred<ArtworkResult>) {
        var waiters: Int = 0
    }

    private data class ArtworkFlightKey(val identity: ArtworkIdentity, val policy: ArtworkReadPolicy)
}
