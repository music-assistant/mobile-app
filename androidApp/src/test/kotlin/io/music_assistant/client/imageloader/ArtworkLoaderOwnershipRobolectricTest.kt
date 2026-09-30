package io.music_assistant.client.imageloader

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import coil3.request.CachePolicy
import coil3.request.ErrorResult
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.bitmapConfig
import coil3.request.transformations
import coil3.size.Scale
import coil3.toUri
import coil3.transform.CircleCropTransformation
import coil3.transform.RoundedCornersTransformation
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.File

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class ArtworkLoaderOwnershipRobolectricTest {
    @Test
    fun real_loader_ownership_matrix() {
        val http = "https://artwork.test/ownership.png"
        val uppercaseHttp = "HTTPS://artwork.test/uppercase.png"
        val normalizedUppercaseHttp = "https://artwork.test/uppercase.png"
        val fixture = tinyPng(0xff4527a0.toInt())
        val fake = ArtworkHttpFake(mapOf(http to fixture, normalizedUppercaseHttp to fixture))
        withArtworkKoin(fake) { loader ->
            assertNull(loader.diskCache)
            runBlocking {
                val context = ApplicationProvider.getApplicationContext<Context>()
                val first = loader.execute(ImageRequest.Builder(context).data(http).build())
                assertTrue(first is SuccessResult)
                val duplicate = loader.execute(ImageRequest.Builder(context).data(http).build())
                assertTrue(duplicate is SuccessResult)
                assertEquals(1, fake.calls.get())
                val uppercase = loader.execute(ImageRequest.Builder(context).data(uppercaseHttp).build())
                assertTrue(uppercase is SuccessResult)
                assertEquals(2, fake.calls.get())
                assertEquals(listOf(http, normalizedUppercaseHttp), fake.requestedUrls)

                val file = File.createTempFile("artwork-", ".png")
                try {
                    file.writeBytes(fixture.bytes)
                    val request = ImageRequest.Builder(context)
                        .data(file.toURI().toString().toUri())
                    val fileResult = loader.execute(
                        request.bitmapConfig(android.graphics.Bitmap.Config.RGB_565).build(),
                    )
                    assertTrue(fileResult is SuccessResult)
                    assertEquals(2, fake.calls.get())
                } finally {
                    file.delete()
                }

                val bytes = loader.execute(
                    ImageRequest.Builder(context).data(fixture.bytes).build(),
                )
                assertTrue(bytes is SuccessResult)
                assertEquals(2, fake.calls.get())

                for (malformed in listOf("mawebrtc://", "http://")) {
                    val result = loader.execute(ImageRequest.Builder(context).data(malformed).build())
                    assertTrue("$malformed should fail", result is ErrorResult)
                }
                assertEquals(2, fake.calls.get())

                val cacheOnly = loader.execute(
                    ImageRequest.Builder(context)
                        .data("https://artwork.test/missing-cache-only.png")
                        .networkCachePolicy(CachePolicy.READ_ONLY)
                        .build(),
                )
                assertTrue(cacheOnly is ErrorResult)
                assertEquals(2, fake.calls.get())
            }
        }
    }

    @Test
    fun real_loader_network_cache_policies_preserve_their_read_write_contract() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val policies = listOf(
            CachePolicy.ENABLED to 1,
            CachePolicy.READ_ONLY to 1,
            CachePolicy.WRITE_ONLY to 2,
            CachePolicy.DISABLED to 2,
        )
        policies.forEachIndexed { index, (policy, expectedCalls) ->
            val url = "https://artwork.test/policy-$index-${System.nanoTime()}.png"
            val original = tinyPng((0xff100000L + index).toInt())
            val replacement = tinyPng((0xff200000L + index).toInt())
            val fake = ArtworkHttpFake(mapOf(url to original))
            withArtworkKoin(fake) { loader ->
                runBlocking {
                    fun request(cachePolicy: CachePolicy) = ImageRequest.Builder(context)
                        .data(url)
                        .memoryCachePolicy(CachePolicy.DISABLED)
                        .networkCachePolicy(cachePolicy)
                        .build()
                    assertTrue(loader.execute(request(CachePolicy.ENABLED)) is SuccessResult)
                    fake.enqueue(url, replacement)
                    assertTrue(loader.execute(request(policy)) is SuccessResult)
                    assertTrue(loader.execute(request(CachePolicy.ENABLED)) is SuccessResult)
                    assertEquals(expectedCalls, fake.calls.get())
                }
            }
        }
    }

    @Test
    fun real_transformations_and_sizes_are_isolated_while_source_is_reused() {
        val url = "https://artwork.test/transform-${System.nanoTime()}.png"
        val fixture = tinyPng(0xff008577.toInt())
        val fake = ArtworkHttpFake(mapOf(url to fixture))
        withArtworkKoin(fake) { loader ->
            runBlocking {
                val context = ApplicationProvider.getApplicationContext<Context>()
                val circle = ImageRequest.Builder(context)
                    .data(url)
                    .size(32, 32)
                    .transformations(CircleCropTransformation())
                    .build()
                val rounded = ImageRequest.Builder(context)
                    .data(url)
                    .size(32, 32)
                    .transformations(RoundedCornersTransformation(8f))
                    .build()
                val circleResult = loader.execute(circle) as SuccessResult
                val roundedResult = loader.execute(rounded) as SuccessResult
                assertNotEquals(circleResult.memoryCacheKey, roundedResult.memoryCacheKey)
                assertEquals(32, circleResult.image.width)
                assertEquals(32, circleResult.image.height)
                assertEquals(32, roundedResult.image.width)
                assertEquals(32, roundedResult.image.height)
                assertEquals(1, fake.calls.get())

                val largerCircle = ImageRequest.Builder(context)
                    .data(url)
                    .size(48, 48)
                    .transformations(CircleCropTransformation())
                    .build()
                val largerResult = loader.execute(largerCircle) as SuccessResult
                assertNotEquals(circleResult.memoryCacheKey, largerResult.memoryCacheKey)
                assertEquals(48, largerResult.image.width)
                assertEquals(48, largerResult.image.height)
                assertEquals(1, fake.calls.get())
            }
        }
    }

    @Test
    fun caller_custom_key_is_preserved_and_isolates_variants() {
        val url = "https://artwork.test/custom-key-${System.nanoTime()}.png"
        val fake = ArtworkHttpFake(mapOf(url to tinyPng(0xff008577.toInt())))
        withArtworkKoin(fake) { loader ->
            runBlocking {
                val context = ApplicationProvider.getApplicationContext<Context>()
                fun request(key: String) = ImageRequest.Builder(context)
                    .data(url)
                    .memoryCacheKey(key)
                    .build()
                val first = loader.execute(request("caller-one")) as SuccessResult
                val second = loader.execute(request("caller-two")) as SuccessResult
                assertNotEquals(first.memoryCacheKey, second.memoryCacheKey)
                assertTrue(first.memoryCacheKey?.key?.contains("caller-one") == true)
                assertTrue(second.memoryCacheKey?.key?.contains("caller-two") == true)
                assertEquals(1, fake.calls.get())
            }
        }
    }

    @Test
    fun extras_and_size_scale_each_have_distinct_decoded_identity() {
        val url = "https://artwork.test/variants-${System.nanoTime()}.png"
        val fake = ArtworkHttpFake(mapOf(url to tinyPng(0xff008577.toInt())))
        withArtworkKoin(fake) { loader ->
            runBlocking {
                val context = ApplicationProvider.getApplicationContext<Context>()
                val base = ImageRequest.Builder(context).data(url).build()
                val extra = base.newBuilder().memoryCacheKeyExtra("variant", "two").build()
                val sized = base.newBuilder().size(16, 16).scale(Scale.FILL).build()
                val transformed = base.newBuilder()
                    .transformations(CircleCropTransformation())
                    .build()
                val baseResult = loader.execute(base) as SuccessResult
                val extraResult = loader.execute(extra) as SuccessResult
                val sizedResult = loader.execute(sized) as SuccessResult
                val transformedResult = loader.execute(transformed) as SuccessResult
                assertEquals(baseResult.memoryCacheKey?.key, extraResult.memoryCacheKey?.key)
                assertEquals("two", extraResult.memoryCacheKey?.extras?.get("variant"))
                assertEquals(baseResult.memoryCacheKey?.key, sizedResult.memoryCacheKey?.key)
                assertNotEquals(baseResult.memoryCacheKey, transformedResult.memoryCacheKey)
                assertEquals(1, fake.calls.get())
                assertTrue(baseResult.image != sizedResult.image)
            }
        }
    }
}
