package io.music_assistant.client.utils

import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.darwin.Darwin

class IosHttpClientFactory : HttpClientFactory {
    override fun create(block: HttpClientConfig<*>.() -> Unit): HttpClient = HttpClient(Darwin, block)
}
