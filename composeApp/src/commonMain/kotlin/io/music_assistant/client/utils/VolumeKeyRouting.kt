package io.music_assistant.client.utils

/**
 * Whether the platform can give the hardware volume keys to the selected remote player.
 * iOS rejects apps that change the function of the volume buttons (guideline 2.5.9).
 */
expect val supportsVolumeKeyRouting: Boolean
