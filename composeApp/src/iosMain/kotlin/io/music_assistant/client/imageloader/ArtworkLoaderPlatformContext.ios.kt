package io.music_assistant.client.imageloader

import io.music_assistant.client.player.PlatformContext

internal actual fun toArtworkLoaderPlatformContext(p: PlatformContext): coil3.PlatformContext =
    coil3.PlatformContext.INSTANCE
