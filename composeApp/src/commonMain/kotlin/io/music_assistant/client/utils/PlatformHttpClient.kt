package io.music_assistant.client.utils

import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig

expect fun createPlatformHttpClient(
    block: HttpClientConfig<*>.() -> Unit = {},
): HttpClient
