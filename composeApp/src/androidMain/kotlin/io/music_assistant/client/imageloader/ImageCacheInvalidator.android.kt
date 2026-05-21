package io.music_assistant.client.imageloader

import io.music_assistant.client.player.PlatformContext

internal actual fun toCoilPlatformContext(p: PlatformContext): coil3.PlatformContext =
    p.applicationContext
