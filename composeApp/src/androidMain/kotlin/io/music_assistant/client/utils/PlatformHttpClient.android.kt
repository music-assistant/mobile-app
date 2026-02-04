package io.music_assistant.client.utils

import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.cio.CIO
import io.ktor.client.engine.cio.endpoint

/**
 * Android implementation using CIO engine.
 */
actual fun createPlatformHttpClient(
    config: HttpClientConfig<*>.() -> Unit
): HttpClient = HttpClient(CIO) {
    config(this)

    engine {
        // TCP socket options for resilient connection during network transitions
        endpoint {
            keepAliveTime = 5000  // 5 seconds - maintain connection like VPN
            connectTimeout = 10000
            socketTimeout = 10000
        }
    }
}