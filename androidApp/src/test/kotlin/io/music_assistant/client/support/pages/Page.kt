package io.music_assistant.client.support.pages

interface Page {
    fun assert()
}

fun <T : Page> T.assertOnPage(): T {
    assert()
    return this
}
