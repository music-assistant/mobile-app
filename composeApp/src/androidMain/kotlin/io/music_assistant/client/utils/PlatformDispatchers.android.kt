package io.music_assistant.client.utils

import io.music_assistant.sendspin.android.sendspinAudioDispatcher
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

actual val mainDispatcher: CoroutineDispatcher = Dispatchers.Main

/** The single high-priority playback thread, for the life of the process. */
actual val audioDispatcher: CoroutineDispatcher = sendspinAudioDispatcher()
