package io.music_assistant.client.watchlink

/**
 * What the phone tells its watch app about the server.
 *
 * The watches cannot sign in and cannot speak WebRTC, so the phone sends them the address and
 * the token of its own direct session. The watch shares the phone token: a sign-out on the
 * phone also signs the watch out.
 */
sealed interface WatchLinkState {
    /** A direct session with a token. The watch connects to the same address. */
    data class Configured(
        val host: String,
        val port: Int,
        val isTls: Boolean,
        /** "" or "/seg[/seg]", like [io.music_assistant.client.api.ConnectionInfo.basePath]. */
        val basePath: String,
        val token: String,
        val serverId: String,
    ) : WatchLinkState

    /** The phone reaches the server through WebRTC only, which the watches cannot use. */
    data object RemoteOnly : WatchLinkState

    /** The phone signed out, or the server rejected the token. */
    data object SignedOut : WatchLinkState
}

/** Sends the [WatchLinkState] to the paired watch. One implementation for each platform. */
fun interface WatchLink {
    fun publish(state: WatchLinkState)
}

/** The wire format that both watch apps read. Change it on the phone and on both watches. */
object WatchLinkContract {
    /** The Wearable Data Layer path of the data item (Wear OS). */
    const val DATA_PATH = "/ma/server-config"

    const val KEY_STATUS = "status"
    const val KEY_HOST = "host"
    const val KEY_PORT = "port"
    const val KEY_TLS = "tls"
    const val KEY_BASE_PATH = "basePath"
    const val KEY_TOKEN = "token"
    const val KEY_SERVER_ID = "serverId"

    const val STATUS_CONFIGURED = "configured"
    const val STATUS_REMOTE_ONLY = "remote_only"
    const val STATUS_SIGNED_OUT = "signed_out"

    /** The state as a flat map of the keys above. Both platforms send this map. */
    fun toMap(state: WatchLinkState): Map<String, Any> = when (state) {
        is WatchLinkState.Configured -> mapOf(
            KEY_STATUS to STATUS_CONFIGURED,
            KEY_HOST to state.host,
            KEY_PORT to state.port,
            KEY_TLS to state.isTls,
            KEY_BASE_PATH to state.basePath,
            KEY_TOKEN to state.token,
            KEY_SERVER_ID to state.serverId,
        )

        WatchLinkState.RemoteOnly -> mapOf(KEY_STATUS to STATUS_REMOTE_ONLY)
        WatchLinkState.SignedOut -> mapOf(KEY_STATUS to STATUS_SIGNED_OUT)
    }
}
