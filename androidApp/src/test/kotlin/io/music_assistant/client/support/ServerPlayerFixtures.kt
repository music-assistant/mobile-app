package io.music_assistant.client.support

import io.music_assistant.client.data.model.server.ServerPlayer
import io.music_assistant.client.utils.UniqueIdGenerator

object ServerPlayerFixtures {
    private val uniqueIdGenerator = UniqueIdGenerator()

    fun player(
        id: String = uniqueIdGenerator.nextInt().toString(),
        name: String = "Player $id",
    ): ServerPlayer {
        return ServerPlayer(
            playerId = id,
            displayName = name,
            available = true,
            hidden = false,
            enabled = true,
            currentMedia = null,
            activeSource = id,
        )
    }
}
