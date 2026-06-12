package io.music_assistant.client.settings

import com.russhwolf.settings.MapSettings
import io.music_assistant.client.data.model.client.ClickContext
import io.music_assistant.client.data.model.client.ItemKind
import kotlin.test.Test
import kotlin.test.assertEquals

class DefaultClickActionsRepoTest {
    @Test
    fun `defaults to empty when nothing stored`() {
        val repo = SettingsRepository(MapSettings())
        assertEquals(emptyMap(), repo.defaultClickActions.value)
    }

    @Test
    fun `set then read back the per-context table for a kind`() {
        val repo = SettingsRepository(MapSettings())
        repo.setDefaultClickActions(
            ItemKind.TRACK,
            mapOf(ClickContext.SEARCH to DefaultClickAction.ADD_TO_QUEUE),
        )
        assertEquals(
            DefaultClickAction.ADD_TO_QUEUE,
            repo.defaultClickActions.value[ItemKind.TRACK]?.get(ClickContext.SEARCH),
        )
    }

    @Test
    fun `saving one kind preserves the others`() {
        val repo = SettingsRepository(MapSettings())
        repo.setDefaultClickActions(ItemKind.TRACK, mapOf(ClickContext.SEARCH to DefaultClickAction.INSERT_NEXT))
        repo.setDefaultClickActions(ItemKind.ALBUM, mapOf(ClickContext.DETAIL to DefaultClickAction.ADD_TO_QUEUE))

        assertEquals(
            DefaultClickAction.INSERT_NEXT,
            repo.defaultClickActions.value[ItemKind.TRACK]?.get(ClickContext.SEARCH),
        )
        assertEquals(
            DefaultClickAction.ADD_TO_QUEUE,
            repo.defaultClickActions.value[ItemKind.ALBUM]?.get(ClickContext.DETAIL),
        )
    }

    @Test
    fun `survives a fresh repository over the same storage`() {
        val settings = MapSettings()
        SettingsRepository(settings).setDefaultClickActions(
            ItemKind.RADIO,
            mapOf(ClickContext.HOME to DefaultClickAction.INSERT_NEXT_AND_PLAY),
        )
        val reopened = SettingsRepository(settings)
        assertEquals(
            DefaultClickAction.INSERT_NEXT_AND_PLAY,
            reopened.defaultClickActions.value[ItemKind.RADIO]?.get(ClickContext.HOME),
        )
    }

    @Test
    fun `malformed stored json degrades to empty`() {
        val settings = MapSettings().apply { putString("default_click_actions", "{not valid json") }
        assertEquals(emptyMap(), SettingsRepository(settings).defaultClickActions.value)
    }
}
