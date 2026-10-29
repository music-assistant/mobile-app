package io.music_assistant.client.feature

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.music_assistant.client.api.ServiceClient
import io.music_assistant.client.data.model.server.SERVER_2_11_SCHEMA
import io.music_assistant.client.support.FakeServiceClient
import io.music_assistant.client.support.Qualifiers
import io.music_assistant.client.support.ServerMediaItemFixtures
import io.music_assistant.client.support.launchLoggedInApp
import io.music_assistant.client.support.pages.assertMediaNotDisplayed
import io.music_assistant.client.support.pages.clickPodcasts
import io.music_assistant.client.support.rules.createTestRuleChain
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.java.KoinJavaComponent.inject
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(qualifiers = Qualifiers.MEDIUM_PHONE)
class PodcastTest {
    @get:Rule
    val testRuleChain = createTestRuleChain()

    @get:Rule
    val composeTestRule = createComposeRule()

    private val serviceClient: FakeServiceClient by inject(ServiceClient::class.java)

    // The fake ignores `fully_played` below schema 96, like a 2.10 server, so the app filters.
    @Test
    fun `can hide the played episodes on an older server`() {
        assertHidesPlayedEpisodes()
    }

    @Test
    fun `can hide the played episodes on server 2_11`() {
        serviceClient.schemaVersion = SERVER_2_11_SCHEMA
        assertHidesPlayedEpisodes()
    }

    private fun assertHidesPlayedEpisodes() {
        val podcast = ServerMediaItemFixtures.podcast()
        val played = ServerMediaItemFixtures.podcastEpisode(podcast, fullyPlayed = true)
        val unplayed = ServerMediaItemFixtures.podcastEpisode(podcast, fullyPlayed = false)
        val untouched = ServerMediaItemFixtures.podcastEpisode(podcast)
        serviceClient.addItems(podcast, played, unplayed, untouched)
        serviceClient.addToLibrary(podcast)

        launchLoggedInApp(composeTestRule, serviceClient)
            .clickPodcasts()
            .clickOnMedia(podcast)
            .assertMediaDisplayed(played)
            .assertMediaDisplayed(unplayed)
            .assertMediaDisplayed(untouched)
            .toggleHidePlayedEpisodes()
            .assertMediaDisplayed(unplayed)
            .assertMediaDisplayed(untouched)
            .assertMediaNotDisplayed(played)
    }
}
