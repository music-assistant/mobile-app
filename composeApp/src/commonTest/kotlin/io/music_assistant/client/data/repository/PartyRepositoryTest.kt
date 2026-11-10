package io.music_assistant.client.data.repository

import io.music_assistant.client.api.APICommands
import io.music_assistant.client.data.model.client.PartyInvite
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PartyRepositoryTest {
    private fun provider(domain: String, available: Boolean = true) =
        """{"instance_id":"$domain","name":"$domain","domain":"$domain","type":"plugin","available":$available}"""

    private val link = "http://ma.local:8095/?join=ABCDEFGHJKLM"

    private val client = FakeAiRadioClient(
        mutableMapOf(
            APICommands.PROVIDERS to "[${provider("party")}]",
            APICommands.PARTY_URL to "\"$link\"",
            APICommands.PARTY_CONFIG to """{"party_name":"Summer party","qr_text":"Add your songs","mode":"venue"}""",
        ),
    )

    private fun TestScope.repository(): PartyRepository {
        val providers = ProviderRepository(client, backgroundScope)
        return PartyRepository(client, providers, backgroundScope).also { runCurrent() }
    }

    @Test
    fun aLoadedPluginIsAvailable() = runTest {
        assertTrue(repository().available.value)
    }

    @Test
    fun anUnavailableOrMissingPluginIsNotAvailable() = runTest {
        client.results[APICommands.PROVIDERS] = "[${provider("party", available = false)},${provider("spotify")}]"

        assertFalse(repository().available.value)
    }

    @Test
    fun aFailedProviderListIsNotAvailable() = runTest {
        client.failures += APICommands.PROVIDERS

        assertFalse(repository().available.value)
    }

    @Test
    fun anInviteCarriesTheLinkTheNameAndTheText() = runTest {
        assertEquals(
            Result.success(PartyInvite.Open(link, "Summer party", "Add your songs")),
            repository().invite(),
        )
    }

    @Test
    fun aBlankNameOrTextIsNull() = runTest {
        client.results[APICommands.PARTY_CONFIG] = """{"party_name":"  ","qr_text":""}"""

        val invite = repository().invite().getOrNull() as PartyInvite.Open

        assertEquals(null, invite.partyName)
        assertEquals(null, invite.qrText)
    }

    @Test
    fun aNullLinkMeansGuestAccessIsOff() = runTest {
        client.results[APICommands.PARTY_URL] = "null"

        assertEquals(Result.success(PartyInvite.GuestAccessOff), repository().invite())
    }

    @Test
    fun aFailedConfigKeepsTheLink() = runTest {
        client.failures += APICommands.PARTY_CONFIG

        assertEquals(
            Result.success(PartyInvite.Open(link, null, null)),
            repository().invite(),
        )
    }

    @Test
    fun aFailedLinkFails() = runTest {
        client.failures += APICommands.PARTY_URL

        assertTrue(repository().invite().isFailure)
    }
}
