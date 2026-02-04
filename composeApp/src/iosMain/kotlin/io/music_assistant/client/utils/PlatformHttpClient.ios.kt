package io.music_assistant.client.utils

import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.darwin.Darwin

/**
 * iOS implementation using Darwin engine (NSURLSession).
 * Darwin engine properly supports TLS/WSS connections on iOS.
 */
actual fun createPlatformHttpClient(
    config: HttpClientConfig<*>.() -> Unit
): HttpClient = HttpClient(Darwin) {
    config(this)

    engine {
        configureRequest {
            // Allow cellular access for network requests
            setAllowsCellularAccess(true)
        }
    }
}