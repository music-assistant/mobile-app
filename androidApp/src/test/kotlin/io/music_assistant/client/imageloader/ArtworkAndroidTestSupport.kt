package io.music_assistant.client.imageloader

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.test.core.app.ApplicationProvider
import coil3.SingletonImageLoader
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.music_assistant.client.di.androidModule
import io.music_assistant.client.di.appModule
import io.music_assistant.client.di.initKoin
import io.music_assistant.client.di.sharedModule
import io.music_assistant.client.di.webrtcModule
import io.music_assistant.client.support.FakeServiceClient
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.stopKoin
import org.koin.core.qualifier.named
import org.koin.dsl.module
import java.io.ByteArrayOutputStream
import java.util.concurrent.atomic.AtomicInteger

internal data class ArtworkFixture(val bytes: ByteArray, val color: Int)

internal fun tinyPng(color: Int): ArtworkFixture {
    val bitmap = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888)
    bitmap.eraseColor(color)
    return ByteArrayOutputStream().use { output ->
        check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output))
        ArtworkFixture(output.toByteArray(), color)
    }.also { bitmap.recycle() }
}

internal class ArtworkHttpFake(
    private val fixtures: Map<String, ArtworkFixture>,
    private val failure: Throwable? = null,
) {
    val calls = AtomicInteger(0)
    val requestedUrls = mutableListOf<String>()
    private val queuedFixtures = mutableMapOf<String, ArrayDeque<ArtworkFixture>>()

    fun enqueue(url: String, fixture: ArtworkFixture) {
        queuedFixtures.getOrPut(url) { ArrayDeque() }.addLast(fixture)
    }

    fun client(): HttpClient = HttpClient(
        MockEngine { request ->
            calls.incrementAndGet()
            synchronized(requestedUrls) { requestedUrls += request.url.toString() }
            val url = request.url.toString()
            failure?.let { throw it }
            val fixture = queuedFixtures[url]?.removeFirstOrNull() ?: fixtures[url]
                ?: error("Unexpected artwork request: ${request.url}")
            respond(
                content = fixture.bytes,
                status = HttpStatusCode.OK,
                headers = Headers.build {
                    append(HttpHeaders.CacheControl, "max-age=3600")
                    append(HttpHeaders.ContentType, "image/png")
                },
            )
        },
    )
}

internal fun <T> withArtworkKoin(fake: ArtworkHttpFake, block: (coil3.ImageLoader) -> T): T {
    SingletonImageLoader.reset()
    initKoin(
        sharedModule { _, _ -> FakeServiceClient() },
        webrtcModule,
        androidModule(),
        appModule(),
        module {
            single(named("webrtcHttpClient")) { fake.client() }
        },
    ) {
        androidContext(ApplicationProvider.getApplicationContext())
    }
    return try {
        block(SingletonImageLoader.get(ApplicationProvider.getApplicationContext()))
    } finally {
        SingletonImageLoader.reset()
        stopKoin()
    }
}

internal fun Bitmap.assertUniformColor(expected: Int) {
    for (x in 0 until width) {
        for (y in 0 until height) {
            check(getPixel(x, y) == expected) {
                "Pixel ($x,$y) was ${getPixel(x, y)}, expected $expected"
            }
        }
    }
}

internal fun decodeBitmap(bytes: ByteArray): Bitmap =
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        ?: error("Expected decodable bitmap")
