package io.music_assistant.client.utils

import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig

/**
 * Creates a platform-specific HttpClient.
 * - Android: Uses CIO engine
 * - iOS: Uses Darwin engine (NSURLSession) for proper TLS/WSS support
 */
expect fun createPlatformHttpClient(
    config: HttpClientConfig<*>.() -> Unit = {}
): HttpClient