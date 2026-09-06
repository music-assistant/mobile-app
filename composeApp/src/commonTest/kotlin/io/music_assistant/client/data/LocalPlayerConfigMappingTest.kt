package io.music_assistant.client.data

import io.music_assistant.client.data.LocalPlayerAdapter.Companion.localPlayerConfig
import io.music_assistant.client.settings.SettingsRepository
import io.music_assistant.sendspin.api.Endpoint
import io.music_assistant.sendspin.wire.AudioCodec
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class LocalPlayerConfigMappingTest {
    private val endpoint = Endpoint.WebSocket("wss://ma/sendspin", "token")

    private fun config(staticDelayMs: Int, enabled: Boolean = true, endpoint: Endpoint? = this.endpoint) =
        localPlayerConfig(enabled, endpoint, "Phone", AudioCodec.FLAC, 15, staticDelayMs)

    @Test
    fun persistedPositiveCompensationPlaysEarlierAndNegativePlaysLater() {
        // The setting predates the module: positive has always meant "play earlier".
        assertEquals(-250, config(staticDelayMs = 250)?.userDelayMs)
        assertEquals(300, config(staticDelayMs = -300)?.userDelayMs)
        assertEquals(0, config(staticDelayMs = 0)?.userDelayMs)
    }

    @Test
    fun disabledOrEndpointlessMeansNoConfig() {
        assertNull(config(staticDelayMs = 0, enabled = false))
        assertNull(config(staticDelayMs = 0, endpoint = null))
    }

    @Test
    fun bufferCapacityIsMegabytesToBytes() {
        assertEquals(15 * SettingsRepository.BYTES_PER_MB, config(staticDelayMs = 0)?.bufferCapacityBytes)
    }
}
