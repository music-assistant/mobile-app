package io.music_assistant.client.feature

import android.content.ClipboardManager
import android.content.Context
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.music_assistant.client.api.ServiceClient
import io.music_assistant.client.data.model.server.Scope
import io.music_assistant.client.support.FakeServiceClient
import io.music_assistant.client.support.Qualifiers
import io.music_assistant.client.support.ServerMediaItemFixtures
import io.music_assistant.client.support.get
import io.music_assistant.client.support.launchLoggedInApp
import io.music_assistant.client.support.pages.clickParty
import io.music_assistant.client.support.rules.createTestRuleChain
import musicassistantclient.composeapp.generated.resources.Res
import musicassistantclient.composeapp.generated.resources.nav_party
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.java.KoinJavaComponent.inject
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
@Config(qualifiers = Qualifiers.MEDIUM_PHONE)
class PartyTest {
    @get:Rule
    val testRuleChain = createTestRuleChain()

    @get:Rule
    val composeTestRule = createComposeRule()

    val serviceClient: FakeServiceClient by inject(ServiceClient::class.java)

    private val link = "https://app.music-assistant.io/?remote_id=ABCDEFGHIJKL&join=ABCDEFGHJKLM"

    @Before
    fun loadPlugin() {
        serviceClient.addProvider(ServerMediaItemFixtures.partyPlugin())
    }

    @Test
    fun `the host shows the join QR and copies the link`() {
        serviceClient.partyUrl = link
        serviceClient.partyName = "Summer party"

        launchLoggedInApp(composeTestRule, serviceClient)
            .clickParty()
            .assertInvite(title = "Summer party", link = link)
            .copyLink()

        val clipboard = ApplicationProvider.getApplicationContext<Context>()
            .getSystemService(ClipboardManager::class.java)
        assertEquals(link, clipboard.primaryClip?.getItemAt(0)?.text?.toString())
    }

    @Test
    fun `an unnamed party has the default title`() {
        serviceClient.partyUrl = link

        launchLoggedInApp(composeTestRule, serviceClient)
            .clickParty()
            .assertInvite(title = Res.string.nav_party.get(), link = link)
    }

    @Test
    fun `the sheet says when guest access is off`() {
        launchLoggedInApp(composeTestRule, serviceClient)
            .clickParty()
            .assertGuestAccessOff()
            .assertNoSettings()
    }

    @Test
    fun `the host renames the party and ends it from the sheet`() {
        serviceClient.extraScopes += listOf(Scope.CONFIG_PROVIDERS_READ, Scope.CONFIG_PROVIDERS_WRITE)
        serviceClient.partyUrl = link

        launchLoggedInApp(composeTestRule, serviceClient)
            .clickParty()
            .assertInvite(title = Res.string.nav_party.get(), link = link)
            .expandSettings()
            .type(label = "Party Name", text = "Summer party")
            .save()
            .assertInvite(title = "Summer party", link = link)
            .toggleGuestAccess(label = "Guest access")
            .assertHostGuestAccessOff()

        assertEquals("Summer party", serviceClient.partyName)
        assertFalse(serviceClient.partyGuestAccess)
    }

    @Test
    fun `the host sets up the party and starts it with the switch`() {
        serviceClient.extraScopes += listOf(Scope.CONFIG_PROVIDERS_READ, Scope.CONFIG_PROVIDERS_WRITE)
        serviceClient.partyUrl = link
        serviceClient.partyGuestAccess = false

        launchLoggedInApp(composeTestRule, serviceClient)
            .clickParty()
            .assertHostGuestAccessOff()
            .type(label = "Party Name", text = "Summer party")
            .toggleGuestAccess(label = "Guest access")
            .assertInvite(title = "Summer party", link = link)

        assertEquals("Summer party", serviceClient.partyName)
        assertTrue(serviceClient.partyGuestAccess)
    }
}
