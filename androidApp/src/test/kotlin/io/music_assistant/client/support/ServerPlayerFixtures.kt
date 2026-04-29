package io.music_assistant.client.support

import io.music_assistant.client.data.model.server.ServerPlayer
import io.music_assistant.client.utils.UniqueIdGenerator

object ServerPlayerFixtures {
    private val uniqueIdGenerator = UniqueIdGenerator()

    fun player(): ServerPlayer {
        val playerId = uniqueIdGenerator.nextInt().toString()
        return ServerPlayer(
            playerId = playerId,
            displayName = "Player $playerId",
            available = true,
            hidden = false,
            enabled = true,
            currentMedia = null,
            activeSource = playerId,
        )
    }
}
