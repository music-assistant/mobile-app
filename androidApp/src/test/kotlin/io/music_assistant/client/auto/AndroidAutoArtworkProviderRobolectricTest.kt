package io.music_assistant.client.auto

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import co.touchlab.kermit.LogWriter
import co.touchlab.kermit.Logger
import co.touchlab.kermit.Severity
import io.music_assistant.client.imageloader.ArtworkHttpFake
import io.music_assistant.client.imageloader.decodeBitmap
import io.music_assistant.client.imageloader.tinyPng
import io.music_assistant.client.imageloader.withArtworkKoin
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.annotation.Config
import java.io.IOException
import java.lang.reflect.InvocationTargetException
import kotlin.coroutines.Continuation
import kotlin.coroutines.intrinsics.COROUTINE_SUSPENDED
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class AndroidAutoArtworkProviderRobolectricTest {
    private class PayloadBearingArtworkException(message: String) : IOException(message)

    private val logLines = mutableListOf<String>()
    private val logWriter = object : LogWriter() {
        override fun log(severity: Severity, message: String, tag: String, throwable: Throwable?) {
            logLines += "$tag: $message ${throwable?.message.orEmpty()}"
        }
    }

    @After
    fun restoreLogWriters() {
        Logger.setLogWriters()
    }

    @Test
    fun provider_failure_logs_redacted_message_without_throwable() {
        val signedUrl = "https://cdn.example.test/signed.png?X-Amz-Signature=signed-secret&token=token-secret"
        val webrtcUrl = "mawebrtc://imageproxy/art.png?token=token-secret"
        val payload = "payload-secret"
        val fake = ArtworkHttpFake(
            emptyMap(),
            failure = PayloadBearingArtworkException("$signedUrl $webrtcUrl $payload"),
        )
        withArtworkKoin(fake) { _ ->
            Logger.setLogWriters(logWriter)
            val provider = Robolectric.buildContentProvider(AndroidAutoArtworkProvider::class.java).create().get()
            provider.openFile(AndroidAutoArtwork.uriFor(signedUrl)!!, "r").use {
                var attempts = 0
                while (logLines.isEmpty() && attempts < 50) {
                    attempts++
                    Thread.sleep(20)
                }
            }
            val logs = logLines.joinToString("\\n")
            val providerLog = logLines.firstOrNull { it.contains("Unable to serve Android Auto artwork") }
                ?: error("Provider failure log missing: $logs")
            assertTrue(providerLog.matches(Regex(".*Unable to serve Android Auto artwork \\([A-Za-z0-9_.]+\\).*")))
            assertTrue(!logs.contains(signedUrl))
            assertTrue(!logs.contains(webrtcUrl))
            assertTrue(!logs.contains("token-secret"))
            assertTrue(!logs.contains(payload))
        }
    }

    @Test
    fun `auto_provider_serves_cached_jpeg`() {
        val source = "https://artwork.test/auto-${System.nanoTime()}.png"
        val fixture = tinyPng(0xff1565c0.toInt())
        val fake = ArtworkHttpFake(mapOf(source to fixture))

        withArtworkKoin(fake) { _ ->
            val provider = Robolectric.buildContentProvider(AndroidAutoArtworkProvider::class.java)
                .create()
                .get()
            val context = ApplicationProvider.getApplicationContext<Context>()
            val first = runBlocking { loadArtworkDirect(provider, context, source) }

            assertEquals(1, fake.calls.get())
            assertEquals(listOf(source), synchronized(fake.requestedUrls) { fake.requestedUrls.toList() })
            assertTrue("size=${first.size} head=${first.take(8)}", first.size >= 2)
            assertTrue("size=${first.size}", first.size <= MAX_JPEG_BYTES)
            assertEquals(0xff, first[0].toInt() and 0xff)
            assertEquals(0xd8, first[1].toInt() and 0xff)

            val bitmap = decodeBitmap(first)
            try {
                assertTrue(bitmap.width > 0)
                assertTrue(bitmap.height > 0)
                assertTrue(bitmap.width <= MAX_ARTWORK_DIMENSION)
                assertTrue(bitmap.height <= MAX_ARTWORK_DIMENSION)
            } finally {
                bitmap.recycle()
            }

            val second = runBlocking { loadArtworkDirect(provider, context, source) }
            assertArrayEquals(first, second)
            assertEquals(1, fake.calls.get())
        }
    }

    private companion object {
        const val MAX_ARTWORK_DIMENSION = 512
        const val MAX_JPEG_BYTES = 16 * 1024 * 1024

        private suspend fun loadArtworkDirect(
            provider: AndroidAutoArtworkProvider,
            context: Context,
            sourceUrl: String,
        ): ByteArray = suspendCoroutine { continuation ->
            val method = AndroidAutoArtworkProvider::class.java.getDeclaredMethod(
                "loadArtwork",
                Context::class.java,
                String::class.java,
                Continuation::class.java,
            ).apply { isAccessible = true }
            try {
                val result = method.invoke(provider, context, sourceUrl, continuation)
                if (result !== COROUTINE_SUSPENDED) {
                    continuation.resume(result as ByteArray)
                }
            } catch (error: InvocationTargetException) {
                continuation.resumeWithException(error.targetException)
            } catch (error: Throwable) {
                continuation.resumeWithException(error)
            }
        }
    }
}
