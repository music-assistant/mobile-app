package io.music_assistant.client.utils

import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig

/** The single source of HTTP clients, so every connection gets the same platform TLS setup. */
interface HttpClientFactory {
    fun create(block: HttpClientConfig<*>.() -> Unit = {}): HttpClient
}
