package io.music_assistant.client.imageloader

import android.content.Context
import coil3.disk.DiskCache
import io.music_assistant.client.api.ServiceClient
import io.music_assistant.client.data.CarConnectionMonitor
import io.music_assistant.client.di.androidModule
import io.music_assistant.client.di.initKoin
import io.music_assistant.client.player.PlatformContext
import io.music_assistant.client.utils.SessionState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.stopKoin
import org.koin.core.qualifier.named
import org.koin.dsl.module
import org.koin.mp.KoinPlatform
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ArtworkKoinStartupHostTest {
    @Test
    fun artworkRepositoryIsEagerlyCreatedByTheRealGraph() {
        var startedRepository: ArtworkRepository? = null
        initKoin(
            androidModule(),
            module {
                single<ServiceClient> { proxyServiceClient() }
                single<Context> { RuntimeEnvironment.getApplication() }
                single<PlatformContext> { PlatformContext(get()) }
                single<CarConnectionMonitor> { FakeCarConnectionMonitor() }
                single(named("artworkRepositoryCanResolveDuringEagerStartup"), createdAtStart = true) {
                    get<ArtworkRepository>().also { startedRepository = it }
                }
            },
        )
        try {
            val koin = KoinPlatform.getKoin()
            assertSame(startedRepository, koin.get<ArtworkRepository>())
            assertNull(koin.getOrNull<DiskCache>())
        } finally {
            stopKoin()
        }
    }

    private class FakeCarConnectionMonitor : CarConnectionMonitor {
        override val connected = MutableStateFlow(false)
    }

    @Suppress("UNCHECKED_CAST")
    private fun proxyServiceClient(): ServiceClient = java.lang.reflect.Proxy.newProxyInstance(
        ServiceClient::class.java.classLoader,
        arrayOf(ServiceClient::class.java),
    ) { _, method, _ ->
        when (method.name) {
            "getSessionState" -> MutableStateFlow<SessionState>(SessionState.Disconnected.Initial)
            "getWebRTCHttpProxy" -> null
            "getIsReadyForCommands", "isReadyForCommands", "getExternalConsumerActive" -> MutableStateFlow(false)
            "getEvents", "getForegroundEvents" -> emptyFlow<Any>()
            "getWebrtcSendspinChannel" -> null
            "toString" -> "host-test-service"
            "hashCode" -> 1
            "equals" -> false
            else -> error("Unexpected ServiceClient call: ${method.name}")
        }
    } as ServiceClient
}
