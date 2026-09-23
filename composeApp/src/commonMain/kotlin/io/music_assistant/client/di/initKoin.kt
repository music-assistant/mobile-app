package io.music_assistant.client.di

import co.touchlab.kermit.Logger
import co.touchlab.kermit.Severity
import coil3.SingletonImageLoader
import io.music_assistant.client.api.ServiceClient
import io.music_assistant.client.imageloader.ArtworkDiskStore
import io.music_assistant.client.imageloader.ArtworkRepository
import io.music_assistant.client.imageloader.ArtworkTransport
import io.music_assistant.client.imageloader.KtorArtworkTransport
import io.music_assistant.client.imageloader.buildAppImageLoader
import io.music_assistant.client.imageloader.buildArtworkDiskCache
import io.music_assistant.client.imageloader.toArtworkLoaderPlatformContext
import io.music_assistant.client.logging.InMemoryLogWriter
import io.music_assistant.client.logging.platformLogWriters
import io.music_assistant.client.player.PlatformContext
import org.koin.core.context.startKoin
import org.koin.core.module.Module
import org.koin.dsl.KoinAppDeclaration
import org.koin.mp.KoinPlatform

fun initKoin(
    vararg platformModules: Module,
    verboseLogging: Boolean = false,
    config: KoinAppDeclaration? = null,
) {
    // Release builds drop Debug/Verbose logs: the WebSocket layer emits ~4 debug
    // lines/sec for the idle clock-sync heartbeat, which otherwise floods (and
    // evicts useful entries from) InMemoryLogWriter for the entire session.
    Logger.setMinSeverity(if (verboseLogging) Severity.Verbose else Severity.Info)
    // setLogWriters (not addLogWriter) so each platform's console sink replaces
    // Kermit's default rather than doubling it: on iOS the default NSLog writer
    // renders every line as <private>, which would otherwise duplicate the
    // public os.Logger bridge.
    Logger.setLogWriters(listOf(InMemoryLogWriter) + platformLogWriters())
    startKoin {
        config?.invoke(this)
        modules(sharedModule(), webrtcModule, *platformModules)
    }
    val koin = KoinPlatform.getKoin()
    koin.declare(buildArtworkDiskCache(toArtworkLoaderPlatformContext(koin.get<PlatformContext>())))
    koin.declare(ArtworkDiskStore(koin.get()))
    koin.declare<ArtworkTransport>(
        KtorArtworkTransport(
            httpClient = koin.get(org.koin.core.qualifier.named("webrtcHttpClient")),
            serviceClient = koin.get<ServiceClient>(),
        ),
    )
    SingletonImageLoader.setSafe { context -> buildAppImageLoader(context, koin.get<ArtworkRepository>()) }
}
