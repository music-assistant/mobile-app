package io.music_assistant.client.support

import android.app.Application
import io.music_assistant.client.ui.Timings

class TestApplication : Application() {

    lateinit var serviceClient: FakeServiceClient

    override fun onCreate() {
        super.onCreate()
        setTestState()
    }

    private fun setTestState() {
        Timings.DEBOUNCE = 0
    }
}