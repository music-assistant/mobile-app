package io.music_assistant.client.settings

import android.content.Context
import android.content.SharedPreferences
import com.russhwolf.settings.Settings
import com.russhwolf.settings.SharedPreferencesSettings
import org.koin.core.context.GlobalContext

actual fun provideSettings(): Settings {
    val context: Context = GlobalContext.get().get()
    val sharedPreferences: SharedPreferences =
        context.getSharedPreferences("AppPreferences", Context.MODE_PRIVATE)
    return SharedPreferencesSettings(sharedPreferences)
}
