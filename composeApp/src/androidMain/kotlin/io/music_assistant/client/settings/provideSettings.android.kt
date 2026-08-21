package io.music_assistant.client.settings

import android.content.Context
import android.content.SharedPreferences
import com.russhwolf.settings.Settings
import com.russhwolf.settings.SharedPreferencesSettings
import org.koin.core.context.GlobalContext

private fun sharedPreferences(name: String): SharedPreferences {
    val context: Context = GlobalContext.get().get()
    return context.getSharedPreferences(name, Context.MODE_PRIVATE)
}

actual fun provideSettings(): Settings =
    SharedPreferencesSettings(sharedPreferences("AppPreferences"))

// The file name must stay "AppSecrets". The backup rules exclude
// "AppSecrets.xml" by name. A rename here silently backs up the tokens again.
actual fun provideSecretSettings(): Settings =
    SharedPreferencesSettings(sharedPreferences("AppSecrets"))
