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
import coil3.request.CachePolicy
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
    val url: String,
    val policy: ArtworkReadPolicy,
    val resolution: ArtworkResolution,
    val callerMemoryCacheKey: String?,
) {
    val keyToken: ArtworkToken
        get() = resolution.keyToken
}

internal sealed interface ArtworkResolution {
    val keyToken: ArtworkToken

    data class Lazy(override val keyToken: ArtworkToken) : ArtworkResolution
    data class Captured(val result: ArtworkResult) : ArtworkResolution {
        override val keyToken: ArtworkToken
            get() = result.token
    }
}

internal class ArtworkKeyer : Keyer<ResolvedArtworkData> {
    override fun key(data: ResolvedArtworkData, options: Options): String = buildString {
        append(ARTWORK_DATA_KEY)
        append(':')
        append(data.keyToken.identity.key)
        append(':')
        append(data.keyToken.digest)
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
        val url = when (candidate) {
            null -> return chain.proceed()
            is ArtworkCandidate.Invalid -> {
                return ErrorResult(image = null, request = original, throwable = candidate.error)
            }
            is ArtworkCandidate.Valid -> candidate.url
        }
        val policy = artworkPolicy(original)
        val data = try {
            val resolution = if (policy.canRead) {
                repository.resolveFreshToken(url, policy)?.let(ArtworkResolution::Lazy)
                    ?: ArtworkResolution.Captured(repository.load(url, policy))
            } else {
                ArtworkResolution.Captured(repository.load(url, policy))
            }
            ResolvedArtworkData(url, policy, resolution, original.memoryCacheKey)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            return ErrorResult(image = null, request = original, throwable = error)
        }

        val firstResult = proceed(chain, original, data)
        val actualData = firstResult.artworkFreshnessMismatch()?.let { mismatch ->
            ResolvedArtworkData(
                url = url,
                policy = policy,
                resolution = ArtworkResolution.Captured(mismatch.actual),
                callerMemoryCacheKey = original.memoryCacheKey,
            )
        }
        val imageResult = actualData?.let { proceed(chain, original, it) } ?: firstResult
        if (imageResult is ErrorResult && imageResult.throwable.hasArtworkDecodeFailure()) {
            repository.invalidate(actualData?.keyToken ?: data.keyToken)
        }
        return imageResult
    }

    private suspend fun proceed(
        chain: Interceptor.Chain,
        original: ImageRequest,
        data: ResolvedArtworkData,
    ): coil3.request.ImageResult {
        val result = (data.resolution as? ArtworkResolution.Captured)?.result
        val memoryCachePolicy = if (result == null || result.reusable) {
            original.memoryCachePolicy
        } else {
            CachePolicy.DISABLED
        }
        val updated = original.newBuilder()
            .data(data)
            .memoryCacheKey(null as String?)
            .memoryCachePolicy(memoryCachePolicy)
            .diskCachePolicy(CachePolicy.DISABLED)
            .decoderFactory(ArtworkDecoderFactory())
            .build()
        return chain.withRequest(updated).proceed()
    }

    private sealed interface ArtworkCandidate {
        data class Valid(val url: String) : ArtworkCandidate
        data class Invalid(val error: Throwable) : ArtworkCandidate
    }

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
            onSuccess = { ArtworkCandidate.Valid(raw) },
            onFailure = { ArtworkCandidate.Invalid(it) },
        )
    }

    private fun artworkPolicy(request: ImageRequest): ArtworkReadPolicy = when (request.networkCachePolicy) {
        CachePolicy.ENABLED -> ArtworkReadPolicy.READ_WRITE
        CachePolicy.READ_ONLY -> ArtworkReadPolicy.READ_ONLY
        CachePolicy.WRITE_ONLY -> ArtworkReadPolicy.WRITE_ONLY
        CachePolicy.DISABLED -> ArtworkReadPolicy.DISABLED
    }
}

private class ArtworkFreshnessMismatch(val actual: ArtworkResult) : RuntimeException()

private fun coil3.request.ImageResult.artworkFreshnessMismatch(): ArtworkFreshnessMismatch? =
    (this as? ErrorResult)?.throwable?.let { throwable ->
        var current: Throwable? = throwable
        while (current != null) {
            if (current is ArtworkFreshnessMismatch) return current
            current = current.cause
        }
        null
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
    private val repository: ArtworkRepository,
) : Fetcher {
    override suspend fun fetch(): FetchResult {
        val result = when (val resolution = data.resolution) {
            is ArtworkResolution.Captured -> resolution.result
            is ArtworkResolution.Lazy -> repository.load(data.url, data.policy).also { loaded ->
                if (loaded.token != resolution.keyToken || !loaded.reusable) {
                    throw ArtworkFreshnessMismatch(loaded)
                }
            }
        }
        return SourceFetchResult(
            source = ImageSource(
                source = Buffer().apply { write(result.bytes) },
                fileSystem = options.fileSystem,
            ),
            mimeType = result.mimeType,
            dataSource = when (result.source) {
                ArtworkSource.DISK -> DataSource.DISK
                ArtworkSource.NETWORK -> DataSource.NETWORK
            },
        )
    }

    class Factory(private val repository: ArtworkRepository) : Fetcher.Factory<ResolvedArtworkData> {
        override fun create(data: ResolvedArtworkData, options: Options, imageLoader: ImageLoader): Fetcher =
            ArtworkPayloadFetcher(data, options, repository)
    }
}
