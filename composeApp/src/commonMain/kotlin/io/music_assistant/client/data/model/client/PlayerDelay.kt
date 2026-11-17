package io.music_assistant.client.data.model.client

/**
 * The playback delay compensation of one player, in milliseconds. A higher value plays earlier,
 * on every kind: the local player's static delay and the server's `sendspin_static_delay`. So
 * "speed up" adds to [valueMs] and "slow down" subtracts from it, within [range].
 */
data class PlayerDelay(
    val valueMs: Int,
    val range: IntRange,
) {
    val canSpeedUp: Boolean get() = valueMs < range.last
    val canSlowDown: Boolean get() = valueMs > range.first
}
