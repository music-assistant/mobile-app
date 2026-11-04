package io.music_assistant.client.support

import androidx.compose.ui.test.junit4.ComposeContentTestRule
import io.music_assistant.client.support.pages.ConnectPage
import io.music_assistant.client.support.pages.HomePage
import io.music_assistant.client.support.pages.assertOnPage
import io.music_assistant.client.ui.compose.App
import io.music_assistant.client.ui.utils.DeviceChrome

/** [deviceChrome] stands in for the window geometry that iOS reports. It can read state. */
fun launchApp(
    composeTestRule: ComposeContentTestRule,
    deviceChrome: () -> DeviceChrome = { DeviceChrome.None },
): ConnectPage {
    composeTestRule.setContent {
        App(deviceChrome = deviceChrome())
    }

    return ConnectPage(composeTestRule).assertOnPage()
}

fun launchLoggedInApp(
    composeTestRule: ComposeContentTestRule,
    fakeServiceClient: FakeServiceClient,
    deviceChrome: () -> DeviceChrome = { DeviceChrome.None },
): HomePage {
    return launchApp(composeTestRule, deviceChrome)
        .connect()
        .login(fakeServiceClient.username, fakeServiceClient.password)
}
