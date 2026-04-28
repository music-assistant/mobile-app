package io.music_assistant.client.player

interface MediaPlayerListener {
    fun onReady()
    fun onAudioCompleted()
    fun onError(error: Throwable? = null)
}
