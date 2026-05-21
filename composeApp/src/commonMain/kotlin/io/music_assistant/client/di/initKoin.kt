package io.music_assistant.client.di

import co.touchlab.kermit.Logger
import coil3.SingletonImageLoader
import io.music_assistant.client.api.ServiceClient
import io.music_assistant.client.imageloader.WebRTCImageFetcher
import io.music_assistant.client.imageloader.buildAppImageLoader
import io.music_assistant.client.logging.InMemoryLogWriter
import org.koin.core.context.startKoin
import org.koin.core.module.Module
import org.koin.dsl.KoinAppDeclaration
import org.koin.mp.KoinPlatform

fun initKoin(
    vararg platformModules: Module,
    config: KoinAppDeclaration? = null,
) {
    Logger.addLogWriter(InMemoryLogWriter)
    startKoin {
        config?.invoke(this)
        modules(sharedModule(), webrtcModule, *platformModules)
    }
    val webrtcFetcherFactory = WebRTCImageFetcher.Factory(KoinPlatform.getKoin().get<ServiceClient>())
    SingletonImageLoader.setSafe { context -> buildAppImageLoader(context, webrtcFetcherFactory) }
}
