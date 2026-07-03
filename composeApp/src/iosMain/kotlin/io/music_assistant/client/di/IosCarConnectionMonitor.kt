package io.music_assistant.client.di

import io.music_assistant.client.api.ServiceClient
import io.music_assistant.client.data.CarConnectionMonitor

/**
 * iOS [CarConnectionMonitor]: the CarPlay `CPTemplateApplicationScene` connect/disconnect callbacks
 * drive [ServiceClient.onExternalConsumerActive]/`Inactive`, so its `externalConsumerActive` flow is
 * already a precise car-connection edge on iOS.
 */
class IosCarConnectionMonitor(serviceClient: ServiceClient) : CarConnectionMonitor {
    override val connected = serviceClient.externalConsumerActive
}
