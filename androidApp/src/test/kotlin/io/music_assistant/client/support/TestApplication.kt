package io.music_assistant.client.support

import android.app.Application
import io.music_assistant.client.di.androidModule
import io.music_assistant.client.di.appModule
import io.music_assistant.client.di.sharedModule
import io.music_assistant.client.di.webrtcModule
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class TestApplication : Application() {

    val serviceClient = FakeServiceClient()

    override fun onCreate() {
        super.onCreate()

        startKoin {
            androidContext(this@TestApplication)
            modules(
                sharedModule { _ -> FakeServiceClient() },
                webrtcModule,
                androidModule(),
                appModule()
            )
        }
    }
}