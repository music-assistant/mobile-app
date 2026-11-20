package io.music_assistant.client.data.model.server

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SendspinSourceSupportTest {
    private fun provider(
        domain: String,
        instanceId: String = domain,
        available: Boolean = true,
        features: List<String> = emptyList(),
    ) = ServerProviderInstance(
        instanceId = instanceId,
        name = domain,
        domain = domain,
        available = available,
        supportedFeatures = features,
    )

    @Test
    fun `the source plugin of a released server gives the instance`() {
        val providers = listOf(provider("sendspin"), provider("sendspin_source", instanceId = "sendspin_source--1"))

        assertEquals("sendspin_source--1", sendspinSourceInstanceId(providers))
    }

    @Test
    fun `a sendspin provider with the audio source feature gives the instance`() {
        val providers = listOf(provider("sendspin", features = listOf("audio_source")))

        assertEquals("sendspin", sendspinSourceInstanceId(providers))
    }

    @Test
    fun `a server without sendspin sources gives none`() {
        assertNull(sendspinSourceInstanceId(listOf(provider("sendspin"), provider("spotify"))))
    }

    @Test
    fun `an unavailable provider does not count`() {
        assertNull(sendspinSourceInstanceId(listOf(provider("sendspin_source", available = false))))
    }

    @Test
    fun `the uri names the provider instance and the client`() {
        assertEquals("sendspin://audio_source/phone-1", sendspinSourceUri("sendspin", "phone-1"))
    }
}
