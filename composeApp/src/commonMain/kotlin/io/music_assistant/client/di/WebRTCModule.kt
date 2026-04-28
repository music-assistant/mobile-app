package io.music_assistant.client.di

import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.serialization.kotlinx.KotlinxWebsocketSerializationConverter
import io.music_assistant.client.utils.createPlatformHttpClient
import io.music_assistant.client.utils.myJson
import io.music_assistant.client.webrtc.SignalingClient
import kotlinx.coroutines.CoroutineScope
import org.koin.core.qualifier.named
import org.koin.dsl.module

/**
 * Koin module for WebRTC components.
 *
 * Provides:
 * - Shared HttpClient for signaling server WebSocket connections
 * - SignalingClient factory (receives scope from caller)
 *
 * Note: WebRTCConnectionManager is NOT registered here - it's created directly by ServiceClient.
 */
val webrtcModule = module {
    // Shared HttpClient for WebRTC signaling
    // Uses default engine (platform-specific: CIO on JVM/Android, Darwin on iOS)
    // Configured with WebSockets support for signaling server connection
    single(named("webrtcHttpClient")) {
        createPlatformHttpClient {
            install(WebSockets) {
                contentConverter = KotlinxWebsocketSerializationConverter(myJson)
            }
        }
    }

    // SignalingClient factory
    // Takes HttpClient and CoroutineScope as parameters
    // Usage: SignalingClient(get(named("webrtcHttpClient")), scope)
    factory { (scope: CoroutineScope) ->
        SignalingClient(
            client = get(named("webrtcHttpClient")),
            scope = scope,
        )
    }
}
