package io.music_assistant.client.utils

import android.os.Build

/** AudioTrack plays 24-bit packed PCM, and MediaCodec outputs it, from Android 12 (API 31). */
actual val supportsHiResAudio: Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
