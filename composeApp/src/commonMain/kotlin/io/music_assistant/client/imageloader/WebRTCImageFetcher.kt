package io.music_assistant.client.imageloader

import coil3.ImageLoader
import coil3.Uri
import coil3.decode.DataSource
import coil3.decode.ImageSource
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.SourceFetchResult
import coil3.request.Options
import io.music_assistant.client.api.ServiceClient
import okio.Buffer

/**
 * Coil [Fetcher] that resolves `mawebrtc://` URIs through the active WebRTC HTTP proxy.
 *
 * The URI's path + query is forwarded verbatim as the proxy request path
 * (e.g. `/imageproxy?path=...&provider=...&checksum=`). Returns null on any non-2xx
 * response so Coil falls through to its error handler.
 */
class WebRTCImageFetcher(
    private val serviceClient: ServiceClient,
    private val data: Uri,
    private val options: Options,
) : Fetcher {
    override suspend fun fetch(): FetchResult? {
        val proxy = serviceClient.webRTCHttpProxy ?: return null
        val proxyPath = buildProxyPath() ?: return null
        val response = proxy.get(proxyPath)
        if (response.status !in HTTP_OK_RANGE) return null
        return SourceFetchResult(
            source = ImageSource(
                source = Buffer().apply { write(response.body) },
                fileSystem = options.fileSystem,
            ),
            mimeType = response.headers.entries.firstOrNull { it.key.equals("content-type", ignoreCase = true) }?.value,
            dataSource = DataSource.NETWORK,
        )
    }

    private fun buildProxyPath(): String? {
        val path = data.path ?: return null
        val query = data.query
        return if (query.isNullOrEmpty()) path else "$path?$query"
    }

    class Factory(private val serviceClient: ServiceClient) : Fetcher.Factory<Uri> {
        override fun create(data: Uri, options: Options, imageLoader: ImageLoader): Fetcher? {
            if (data.scheme != SCHEME) return null
            return WebRTCImageFetcher(serviceClient, data, options)
        }
    }

    companion object {
        const val SCHEME = "mawebrtc"
        private val HTTP_OK_RANGE = 200..299
    }
}
