package io.music_assistant.client.wear

import android.app.Application
import android.os.Build
import io.music_assistant.client.wear.data.ArtworkLoader
import io.music_assistant.client.wear.data.MaRepository
import io.music_assistant.client.wear.data.MaSession
import io.music_assistant.client.wear.data.WearPreferences
import io.music_assistant.client.wear.data.WearServerConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * The watch's object graph, built by hand.
 *
 * There is no Koin here on purpose: this module has four long-lived objects and no
 * platform-specific bindings to resolve, so a container would be more machinery than the
 * app has moving parts.
 */
class WearApplication : Application() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    lateinit var session: MaSession
        private set

    lateinit var repository: MaRepository
        private set

    val artworkLoader: ArtworkLoader by lazy { ArtworkLoader() }

    override fun onCreate() {
        super.onCreate()
        val config = WearServerConfig.fromBuildConfig()
        session = MaSession(
            config = config,
            scope = scope,
            // Shown in the server's session list, so the user can tell the watch from the phone.
            deviceName = "${Build.MANUFACTURER} ${Build.MODEL} (Wear)",
        )
        repository = MaRepository(
            session = session,
            config = config,
            preferences = WearPreferences(this),
            scope = scope,
        )
    }
}
