package io.music_assistant.client.settings

import io.music_assistant.client.data.model.client.ItemKind
import io.music_assistant.client.data.model.client.QueueOption
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CarActionPrefsTest {
    // PLAY_FROM_HERE needs a parent container + start track, only resolvable for a TRACK tap
    // inside an album/playlist drilldown — and only Android Auto threads that context through.
    @Test
    fun playFromHereSupportedOnlyForAndroidAutoTrackTap() {
        assertTrue(
            DefaultClickOption.PLAY_FROM_HERE.isCarSupported(CarPlatform.ANDROID_AUTO, ItemKind.TRACK),
        )
        assertFalse(
            DefaultClickOption.PLAY_FROM_HERE.isCarSupported(CarPlatform.CARPLAY, ItemKind.TRACK),
        )
    }

    @Test
    fun playFromHereNotSupportedForNonTrackKinds() {
        // It must never surface as a bulk action: bulk kinds are browsable containers, never TRACK.
        ItemKind.entries.filter { it != ItemKind.TRACK }.forEach { kind ->
            CarPlatform.entries.forEach { platform ->
                assertFalse(
                    DefaultClickOption.PLAY_FROM_HERE.isCarSupported(platform, kind),
                    "PLAY_FROM_HERE must be unsupported for $kind on $platform",
                )
            }
        }
    }

    @Test
    fun carBulkActionsNeverContainPlayFromHere() {
        // The stored list is filtered through isCarSupported; even if a caller seeds it, it drops out.
        val seeded = mapOf(
            ItemKind.ALBUM to listOf(DefaultClickOption.PLAY_NOW, DefaultClickOption.PLAY_FROM_HERE),
            ItemKind.PLAYLIST to DefaultClickOption.entries.toList(),
        )
        carBrowsableKinds.forEach { kind ->
            CarPlatform.entries.forEach { platform ->
                assertFalse(
                    DefaultClickOption.PLAY_FROM_HERE in seeded.carBulkActions(kind, platform),
                    "bulk actions for $kind on $platform must not include PLAY_FROM_HERE",
                )
            }
        }
    }

    @Test
    fun playFromHereOfferedAsTrackTapOptionOnAndroidAuto() {
        // Mirrors how Settings → Car builds the tap-action dropdown.
        val offered = DefaultClickOption.entries
            .filter { it.isCarSupported(CarPlatform.ANDROID_AUTO, ItemKind.TRACK) }
        assertTrue(DefaultClickOption.PLAY_FROM_HERE in offered)
    }

    @Test
    fun toCarDispatchRejectsPlayFromHere() {
        // It's resolved at the AA call site (parent URI + start item), never via toCarDispatch.
        val error = runCatching { DefaultClickOption.PLAY_FROM_HERE.toCarDispatch() }.exceptionOrNull()
        assertTrue(error is IllegalArgumentException)
    }

    @Test
    fun toCarDispatchMapsPlainOptions() {
        assertEquals(QueueOption.REPLACE, DefaultClickOption.PLAY_NOW.toCarDispatch().option)
        assertFalse(DefaultClickOption.PLAY_NOW.toCarDispatch().radioMode)
        assertTrue(DefaultClickOption.START_RADIO.toCarDispatch().radioMode)
    }
}
