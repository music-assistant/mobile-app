package io.music_assistant.client.settings

import com.russhwolf.settings.MapSettings
import io.music_assistant.client.ui.theme.AccentColor
import kotlin.test.Test
import kotlin.test.assertEquals

class AccentColorPersistenceTest {
    @Test
    fun `nothing stored reads as the default accent`() {
        val repo = SettingsRepository(MapSettings(), MapSettings())
        assertEquals(AccentColor.DEFAULT, repo.accentColor.value)
    }

    @Test
    fun `set then read back the accent`() {
        val settings = MapSettings()
        SettingsRepository(settings, MapSettings()).setAccentColor(AccentColor.TEAL)
        assertEquals(AccentColor.TEAL, SettingsRepository(settings, MapSettings()).accentColor.value)
    }

    @Test
    fun `an unknown stored value reads as the default accent`() {
        val settings = MapSettings("accent_color" to "CHARTREUSE")
        assertEquals(AccentColor.DEFAULT, SettingsRepository(settings, MapSettings()).accentColor.value)
    }
}
