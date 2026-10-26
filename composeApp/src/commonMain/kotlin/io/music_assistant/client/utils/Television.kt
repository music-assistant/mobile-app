package io.music_assistant.client.utils

import androidx.compose.runtime.Composable

/**
 * Whether the app is running on a TV form factor (Android TV / Google TV).
 *
 * TV remotes expose D-pad keys instead of a touchscreen, so TV UI must be navigable by focus;
 * Compose's automatic focus search is unreliable there and screens opt into explicit navigation.
 */
@Composable
expect fun isTelevisionDevice(): Boolean
