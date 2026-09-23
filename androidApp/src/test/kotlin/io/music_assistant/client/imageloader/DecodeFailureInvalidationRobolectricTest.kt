package io.music_assistant.client.imageloader

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import coil3.BitmapImage
import coil3.request.CachePolicy
import coil3.request.ErrorResult
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class DecodeFailureInvalidationRobolectricTest {
    @Test
    fun decode_failure_versioned_invalidation() {
        val url = "https://artwork.test/decode-failure-${System.nanoTime()}.png"
        val fixture = tinyPng(0xff00897b.toInt())
        val corruptBytes = fixture.bytes.copyOf().also {
            it[16] = 0
            it[17] = 0
            it[18] = 0
            it[19] = 0
            it[20] = 0
            it[21] = 0
            it[22] = 0
            it[23] = 0
        }
        val corrupt = ArtworkFixture(corruptBytes, 0)
        val fake = ArtworkHttpFake(mapOf(url to fixture))
        withArtworkKoin(fake) { loader ->
            runBlocking {
                val context = ApplicationProvider.getApplicationContext<Context>()
                val request = ImageRequest.Builder(context)
                    .data(url)
                    .memoryCachePolicy(CachePolicy.DISABLED)
                    .networkCachePolicy(CachePolicy.ENABLED)
                    .build()

                val first = loader.execute(request)
                assertTrue("first result=$first", first is SuccessResult)
                assertEquals(1, fake.calls.get())

                // Persist a corrupt replacement, then let the real decoder fail on the disk payload.
                fake.enqueue(url, corrupt)
                val corruptRequest = request.newBuilder()
                    .networkCachePolicy(CachePolicy.WRITE_ONLY)
                    .build()
                val decodeFailure = loader.execute(corruptRequest)
                assertTrue("corrupt payload unexpectedly decoded: $decodeFailure", decodeFailure is ErrorResult)
                assertEquals("corrupt payload should be persisted by the real loader", 2, fake.calls.get())

                val recovered = loader.execute(request)
                assertTrue("recovery result=$recovered", recovered is SuccessResult)
                assertEquals("post-invalidation demand must refetch valid bytes", 3, fake.calls.get())
                val recoveredBitmap = ((recovered as SuccessResult).image as BitmapImage).bitmap
                recoveredBitmap.assertUniformColor(fixture.color)
            }
        }
    }
}
