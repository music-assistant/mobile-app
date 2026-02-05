package io.music_assistant.client.player.sendspin.protocol

import io.music_assistant.client.player.sendspin.model.ClientHelloPayload

/**
 * Configuration for MessageDispatcher.
 * Groups all configuration parameters separate from runtime dependencies.
 *
 * @param clientCapabilities Client capabilities and metadata sent in client/hello
 * @param initialVolume Initial volume level (0-100) to report to server
 * @param authToken Authentication token for proxy mode (null for custom mode)
 * @param requiresAuth Whether authentication is required before protocol handshake (proxy mode detection)
 */
data class MessageDispatcherConfig(
    val clientCapabilities: ClientHelloPayload,
    val initialVolume: Int = 100,
    val authToken: String? = null,
    val requiresAuth: Boolean = false
)
