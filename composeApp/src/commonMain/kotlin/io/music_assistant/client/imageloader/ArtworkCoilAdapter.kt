package io.music_assistant.client.imageloader

import coil3.ImageLoader
import coil3.Uri
import coil3.decode.DataSource
import coil3.decode.DecodeResult
import coil3.decode.Decoder
import coil3.decode.ImageSource
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.SourceFetchResult
import coil3.intercept.Interceptor
import coil3.key.Keyer
import coil3.request.ErrorResult
import coil3.request.ImageRequest
import coil3.request.Options
import io.ktor.http.Url
import kotlinx.coroutines.CancellationException
import okio.Buffer

private const val ARTWORK_DATA_KEY = "io.music_assistant.client.artwork"
private val OWNED_ARTWORK_SCHEMES = setOf("http", "https", "mawebrtc")

internal fun isOwnedArtworkUrl(raw: String): Boolean =
    raw.substringBefore("://", missingDelimiterValue = "").lowercase() in OWNED_ARTWORK_SCHEMES

internal data class ResolvedArtworkData(
    val result: ArtworkResult,
    val callerMemoryCacheKey: String?,
)

internal class ArtworkKeyer : Keyer<ResolvedArtworkData> {
    override fun key(data: ResolvedArtworkData, options: Options): String = buildString {
        append(ARTWORK_DATA_KEY)
        append(':')
        append(data.result.token.identity.key)
        append(':')
        append(data.result.digest)
        data.callerMemoryCacheKey?.let {
            append(':')
            append(it)
        }
    }
}

internal class ArtworkResolvingInterceptor(
    private val repository: ArtworkRepository,
) : Interceptor {
    override suspend fun intercept(chain: Interceptor.Chain): coil3.request.ImageResult {
        val original = chain.request
        val candidate = artworkCandidate(original.data)
        if (candidate != null && candidate.url == null) {
            return ErrorResult(image = null, request = original, throwable = candidate.error!!)
        }
        val url = candidate?.url ?: return chain.proceed()
        val policy = artworkPolicy(original)
        val result = try {
            repository.load(url, policy)
        } catch (error: kotlinx.coroutines.CancellationException) {
            throw error
        } catch (error: Throwable) {
            return ErrorResult(image = null, request = original, throwable = error)
        }
        val updated = original.newBuilder()
            .data(ResolvedArtworkData(result, original.memoryCacheKey))
            .memoryCacheKey(null as String?)
            .memoryCachePolicy(if (result.reusable) original.memoryCachePolicy else coil3.request.CachePolicy.DISABLED)
            .diskCachePolicy(coil3.request.CachePolicy.DISABLED)
            .decoderFactory(ArtworkDecoderFactory())
            .build()
        val imageResult = chain.withRequest(updated).proceed()
        if (imageResult is ErrorResult && imageResult.throwable.hasArtworkDecodeFailure()) {
            repository.invalidate(result.token)
        }
        return imageResult
    }

    private data class ArtworkCandidate(val url: String?, val error: Throwable? = null)

    private fun artworkCandidate(data: Any?): ArtworkCandidate? {
        val raw = when (data) {
            is String -> data.takeIf(::isOwnedArtworkUrl)
            is Uri -> data.toString().takeIf(::isOwnedArtworkUrl)
            else -> null
        } ?: return null
        return runCatching {
            val authorityStart = raw.indexOf("://") + 3
            val remainder = raw.substring(authorityStart)
            val authorityLength = remainder.indexOfFirst { it == '/' || it == '?' || it == '#' }
                .let { if (it == -1) remainder.length else it }
            require(authorityLength > 0)
            val authority = remainder.substring(0, authorityLength)
            require(authority.isNotBlank())
            Url(raw).also { require(it.host.isNotBlank()) }
        }.fold(
                onSuccess = { ArtworkCandidate(raw) },
                onFailure = { ArtworkCandidate(null, it) },
            )
    }

    private fun artworkPolicy(request: ImageRequest): ArtworkReadPolicy = when (request.networkCachePolicy) {
        coil3.request.CachePolicy.ENABLED -> ArtworkReadPolicy.READ_WRITE
        coil3.request.CachePolicy.READ_ONLY -> ArtworkReadPolicy.READ_ONLY
        coil3.request.CachePolicy.WRITE_ONLY -> ArtworkReadPolicy.WRITE_ONLY
        coil3.request.CachePolicy.DISABLED -> ArtworkReadPolicy.DISABLED
    }
}

private class ArtworkDecodeFailure(cause: Throwable) : RuntimeException(cause)

private fun Throwable.hasArtworkDecodeFailure(): Boolean {
    var current: Throwable? = this
    while (current != null) {
        if (current is ArtworkDecodeFailure) return true
        current = current.cause
    }
    return false
}

private class ArtworkDecoderFactory : Decoder.Factory {
    override fun create(result: SourceFetchResult, options: Options, imageLoader: ImageLoader): Decoder? {
        val first = imageLoader.components.newDecoder(result, options, imageLoader, startIndex = 1) ?: return null
        return ArtworkDecoder(result, options, imageLoader, first.first, first.second)
    }
}

private class ArtworkDecoder(
    private val result: SourceFetchResult,
    private val options: Options,
    private val imageLoader: ImageLoader,
    private var decoder: Decoder,
    private var decoderIndex: Int,
) : Decoder {
    override suspend fun decode(): DecodeResult? {
        while (true) {
            try {
                decoder.decode()?.let { return it }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                throw ArtworkDecodeFailure(error)
            }
            val next = imageLoader.components.newDecoder(
                result,
                options,
                imageLoader,
                startIndex = decoderIndex + 1,
            ) ?: return null
            decoder = next.first
            decoderIndex = next.second
        }
    }
}

internal class ArtworkPayloadFetcher(
    private val data: ResolvedArtworkData,
    private val options: Options,
) : Fetcher {
    override suspend fun fetch(): FetchResult = SourceFetchResult(
        source = ImageSource(
            source = Buffer().apply { write(data.result.bytes) },
            fileSystem = options.fileSystem,
        ),
        mimeType = data.result.mimeType,
        dataSource = when (data.result.source) {
            ArtworkSource.DISK -> DataSource.DISK
            ArtworkSource.NETWORK -> DataSource.NETWORK
        },
    )

    class Factory : Fetcher.Factory<ResolvedArtworkData> {
        override fun create(data: ResolvedArtworkData, options: Options, imageLoader: ImageLoader): Fetcher =
            ArtworkPayloadFetcher(data, options)
    }
}
