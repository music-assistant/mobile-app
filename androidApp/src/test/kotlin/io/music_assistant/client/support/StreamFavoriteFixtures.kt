package io.music_assistant.client.support

import io.music_assistant.client.data.model.client.PlayerData
import io.music_assistant.client.data.model.client.PlayerDataFixtures
import io.music_assistant.client.data.model.client.PlayerDataFixtures.toPlayerMedia
import io.music_assistant.client.data.model.client.PlayerDataFixtures.toQueue
import io.music_assistant.client.data.model.client.PlayerDataFixtures.toQueueTrack
import io.music_assistant.client.data.model.client.items.RadioStation
import io.music_assistant.client.data.model.client.items.canBeFavorited
import kotlin.test.assertTrue

fun radioStreamPlayer(title: String): PlayerData {
    val station = RadioStation(
        itemId = "station",
        provider = "test",
        name = "Station",
        providerMappings = null,
        metadata = null,
        favorite = true,
        uri = "test://radio/station",
        images = emptyMap(),
        version = null,
        isPlayable = true,
        isDynamic = false,
    )
    assertTrue(station.canBeFavorited)
    val currentItem = station.toQueueTrack()
    val base = PlayerDataFixtures.playerData(listOf(currentItem).toQueue())
    val player = base.copy(player = base.player.copy(id = "player"))
    return player.copy(
        player = player.player.copy(currentMedia = currentItem.toPlayerMedia().copy(title = title)),
    )
}
