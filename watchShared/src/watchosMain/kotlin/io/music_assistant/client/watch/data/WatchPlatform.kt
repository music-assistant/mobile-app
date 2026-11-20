package io.music_assistant.client.watch.data

import io.ktor.client.HttpClient
import io.ktor.client.engine.darwin.Darwin
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.pingInterval
import platform.Foundation.NSUserDefaults
import kotlin.time.Duration.Companion.seconds

/**
 * `NSURLSession` behind Ktor. A half-open link over Bluetooth can sit there for minutes before
 * the OS notices, so the session pings; a missed pong is what makes the watch reconnect rather
 * than keep showing a track that stopped.
 */
internal actual fun createWebSocketClient(): HttpClient = HttpClient(Darwin) {
    install(WebSockets) {
        pingInterval = PING_SECONDS.seconds
    }
}

/** The selected player, kept in `NSUserDefaults` — the one thing the watch remembers. */
internal class UserDefaultsPreferences : WatchPreferences {
    private val defaults = NSUserDefaults.standardUserDefaults

    override var selectedPlayerId: String?
        get() = defaults.stringForKey(KEY_SELECTED_PLAYER)
        set(value) {
            value?.let { defaults.setObject(it, forKey = KEY_SELECTED_PLAYER) }
                ?: defaults.removeObjectForKey(KEY_SELECTED_PLAYER)
        }

    private companion object {
        const val KEY_SELECTED_PLAYER = "selected_player_id"
    }
}

private const val PING_SECONDS = 10L
