package io.music_assistant.client.utils

import android.content.Context
import android.os.Build
import android.provider.Settings
import org.koin.core.context.GlobalContext

actual fun platformDeviceName(): String {
    val context: Context = GlobalContext.get().get()
    return Settings.Global.getString(context.contentResolver, Settings.Global.DEVICE_NAME)
        ?.takeIf { it.isNotBlank() }
        ?: Build.MODEL
}
