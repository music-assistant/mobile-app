package io.music_assistant.client.support

import androidx.compose.ui.test.junit4.ComposeContentTestRule
import io.music_assistant.client.support.pages.ConnectPage
import io.music_assistant.client.support.pages.HomePage
import io.music_assistant.client.support.pages.assertOnPage
import io.music_assistant.client.ui.compose.App

fun launchApp(composeTestRule: ComposeContentTestRule): ConnectPage {
    composeTestRule.setContent {
        App()
    }

    return ConnectPage(composeTestRule).assertOnPage()
}

fun launchLoggedInApp(
    composeTestRule: ComposeContentTestRule,
    fakeServiceClient: FakeServiceClient,
): HomePage {
    return launchApp(composeTestRule)
        .connect()
        .login(fakeServiceClient.username, fakeServiceClient.password)
}
