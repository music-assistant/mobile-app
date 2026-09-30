package io.music_assistant.client.utils

import io.music_assistant.client.player.PlatformContext

/** Version of the installed app: the user-facing [name] and the build [code]. */
data class AppVersion(val name: String, val code: String)

expect fun appVersion(platformContext: PlatformContext): AppVersion
