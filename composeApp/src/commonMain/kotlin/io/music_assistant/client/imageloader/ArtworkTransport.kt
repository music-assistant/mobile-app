package io.music_assistant.client.imageloader

import io.ktor.client.HttpClient
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.Headers
import io.ktor.http.Url
import io.ktor.http.isSuccess
import io.ktor.utils.io.readAvailable
import io.music_assistant.client.api.ServiceClient
import io.music_assistant.client.webrtc.WebRTCHttpProxy
import kotlinx.coroutines.withTimeoutOrNull
import okio.Buffer

internal data class ArtworkResponse(
    val bytes: ByteArray,
    val headers: Headers,
    val status: Int,
)

internal data class ArtworkRequestContext(
    val serverId: String?,
    val proxy: WebRTCHttpProxy?,
)

/** Bounded fetches turn transport-owned timeouts into ordinary failures; caller cancellation propagates. */
internal interface ArtworkTransport {
    suspend fun fetch(url: String, context: ArtworkRequestContext): ArtworkResponse
}

internal class KtorArtworkTransport(
    private val httpClient: HttpClient,
    private val serviceClient: ServiceClient,
    private val timeoutMs: Long = 30_000L,
) : ArtworkTransport {
    override suspend fun fetch(url: String, context: ArtworkRequestContext): ArtworkResponse {
        val parsed = Url(url)
        return when (parsed.protocol.name) {
            "mawebrtc" -> fetchProxy(parsed, context)
            "http", "https" -> fetchHttp(url)
            else -> error("unsupported artwork URL")
        }
    }

    private suspend fun fetchProxy(
        url: Url,
        context: ArtworkRequestContext,
    ): ArtworkResponse {
        val proxy = context.proxy ?: error("WebRTC artwork proxy unavailable")
        check(serviceClient.artworkContextIsCurrent(context))
        val path = url.encodedPath.let { path ->
            url.encodedQuery.takeIf { it.isNotEmpty() }?.let { "$path?$it" } ?: path
        }
        val response = withTimeoutOrNull(timeoutMs) { proxy.get(path) }
            ?: error("artwork proxy timed out after ${timeoutMs}ms")
        if (response.status !in 200..299) error("artwork proxy status ${response.status}")
        if (response.body.isEmpty() || response.body.size > ARTWORK_MAX_BODY_BYTES) {
            error("artwork proxy body rejected")
        }
        return ArtworkResponse(
            response.body,
            Headers.build {
                response.headers.forEach { (key, value) -> append(key, value) }
            },
            response.status,
        )
    }

    private suspend fun fetchHttp(url: String): ArtworkResponse =
        withTimeoutOrNull(timeoutMs) {
            httpClient.prepareGet(url).execute { response ->
                if (!response.status.isSuccess()) error("artwork HTTP response rejected")
                val contentLength = response.headers["Content-Length"]?.toLongOrNull()
                if (contentLength != null && contentLength > ARTWORK_MAX_BODY_BYTES) {
                    error("artwork content length rejected")
                }
                val body = response.readBoundedBody()
                if (body.size == 0L) error("artwork HTTP response rejected")
                ArtworkResponse(body.readByteArray(), response.headers, response.status.value)
            }
        } ?: error("artwork HTTP request timed out after ${timeoutMs}ms")

    private suspend fun HttpResponse.readBoundedBody(): Buffer {
        val channel = bodyAsChannel()
        val buffer = Buffer()
        val chunk = ByteArray(8192)
        while (true) {
            val read = channel.readAvailable(chunk, 0, chunk.size)
            if (read <= 0) break
            buffer.write(chunk, 0, read)
            if (buffer.size > ARTWORK_MAX_BODY_BYTES) error("artwork body rejected")
        }
        return buffer
    }

    private companion object {
        const val ARTWORK_MAX_BODY_BYTES = 16L * 1024L * 1024L
    }
}

private fun ServiceClient.artworkContextIsCurrent(context: ArtworkRequestContext): Boolean {
    val current = webRTCHttpProxy
    val currentId = (sessionState.value as? io.music_assistant.client.utils.HasConnectionData)?.serverInfo?.serverId
    return current === context.proxy && currentId == context.serverId
}
