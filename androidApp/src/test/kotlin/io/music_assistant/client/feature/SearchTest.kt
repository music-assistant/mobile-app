package io.music_assistant.client.feature

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.music_assistant.client.data.model.client.AppMediaItemFixtures
import io.music_assistant.client.support.Qualifiers
import io.music_assistant.client.support.TestApplication
import io.music_assistant.client.support.pages.ConnectPage
import io.music_assistant.client.ui.compose.App
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(qualifiers = Qualifiers.MEDIUM_PHONE)
class SearchTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val serviceClient =
        ApplicationProvider.getApplicationContext<TestApplication>().serviceClient

    @Test
    fun `can search for items`() {
        val album = AppMediaItemFixtures.album(name = "The Exploding Onion Conspiracy")
        serviceClient.addToLibrary(album)

        composeTestRule.setContent {
            App()
        }

        ConnectPage(composeTestRule)
            .connect()
            .login(serviceClient.username, serviceClient.password)
            .clickSearch()
            .search("onion")
            .assertResult(album.name)
    }
}