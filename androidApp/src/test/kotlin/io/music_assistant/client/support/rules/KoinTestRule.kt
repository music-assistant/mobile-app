package io.music_assistant.client.support.rules

import androidx.test.core.app.ApplicationProvider
import io.music_assistant.client.api.ErrorMessageBus
import io.music_assistant.client.api.ServiceClient
import io.music_assistant.client.data.play.PlayDispatcher
import io.music_assistant.client.di.androidModule
import io.music_assistant.client.di.appModule
import io.music_assistant.client.di.sharedModule
import io.music_assistant.client.di.webrtcModule
import io.music_assistant.client.settings.SettingsRepository
import io.music_assistant.client.support.FakeServiceClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module
import org.koin.test.KoinTestRule

fun createKoinTestRule(
    serviceClientConstructor: (SettingsRepository, ErrorMessageBus) -> ServiceClient = ::createFakeServiceClient,
): KoinTestRule {
    return KoinTestRule.create {
        androidContext(ApplicationProvider.getApplicationContext())
        modules(
            sharedModule(serviceClientConstructor),
            webrtcModule,
            androidModule(),
            appModule(),
            testOverrides,
        )
    }
}

/**
 * Play requests run on the main looper, which the Compose test rule idles, so a test can read
 * the fake server's queue right after a tap.
 */
private val testOverrides = module {
    single { PlayDispatcher(get(), CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)) }
}

private fun createFakeServiceClient(
    @Suppress("UNUSED_PARAMETER")settingsRepository: SettingsRepository,
    @Suppress("UNUSED_PARAMETER") errorBus: ErrorMessageBus,
): FakeServiceClient {
    return FakeServiceClient()
}
