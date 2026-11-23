package io.music_assistant.client.wear.data

import io.music_assistant.client.watchlink.WatchLinkContract
import io.music_assistant.client.watchlink.WatchLinkState
import kotlin.test.Test
import kotlin.test.assertEquals

class PhoneLinkTest {
    @Test
    fun `reads the server and token that the phone sends`() {
        val sent = WatchLinkContract.toMap(
            WatchLinkState.Configured(
                host = "ma.local",
                port = 8095,
                isTls = true,
                basePath = "/ma",
                token = "token",
                serverId = "server-1",
            ),
        )

        assertEquals(
            PhoneLink.Configured(
                WearServerConfig(host = "ma.local", port = 8095, isTls = true, basePath = "/ma", token = "token"),
            ),
            phoneLinkOf(sent),
        )
    }

    @Test
    fun `reads remote only and signed out`() {
        assertEquals(PhoneLink.RemoteOnly, phoneLinkOf(WatchLinkContract.toMap(WatchLinkState.RemoteOnly)))
        assertEquals(PhoneLink.SignedOut, phoneLinkOf(WatchLinkContract.toMap(WatchLinkState.SignedOut)))
    }

    @Test
    fun `an incomplete or unknown item is no server`() {
        assertEquals(PhoneLink.Unknown, phoneLinkOf(emptyMap()))
        assertEquals(PhoneLink.Unknown, phoneLinkOf(mapOf(WatchLinkContract.KEY_STATUS to "something new")))
        assertEquals(
            PhoneLink.Unknown,
            phoneLinkOf(
                mapOf(
                    WatchLinkContract.KEY_STATUS to WatchLinkContract.STATUS_CONFIGURED,
                    WatchLinkContract.KEY_HOST to "ma.local",
                    WatchLinkContract.KEY_PORT to 8095,
                ),
            ),
        )
    }
}
