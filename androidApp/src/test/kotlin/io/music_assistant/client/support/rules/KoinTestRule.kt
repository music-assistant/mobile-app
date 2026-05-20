package io.music_assistant.client.support.rules

import androidx.test.core.app.ApplicationProvider
import io.music_assistant.client.api.ErrorMessageBus
import io.music_assistant.client.di.androidModule
import io.music_assistant.client.di.appModule
import io.music_assistant.client.di.sharedModule
import io.music_assistant.client.di.webrtcModule
import io.music_assistant.client.settings.SettingsRepository
import io.music_assistant.client.support.FakeServiceClient
import org.koin.android.ext.koin.androidContext
import org.koin.test.KoinTestRule

fun createKoinTestRule(): KoinTestRule {
    return KoinTestRule.create {
        androidContext(ApplicationProvider.getApplicationContext())
        modules(
            sharedModule(::createFakeServiceClient),
            webrtcModule,
            androidModule(),
            appModule(),
        )
    }
}

private fun createFakeServiceClient(
    settingsRepository: SettingsRepository,
    @Suppress("UNUSED_PARAMETER") errorBus: ErrorMessageBus,
): FakeServiceClient {
    return FakeServiceClient(settingsRepository)
}
