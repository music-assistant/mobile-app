package io.music_assistant.client.data.model.client

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class M3uPlaylistFileTest {
    @Test
    fun anExtendedM3uIsAPlaylistAfterLeadingWhitespace() {
        assertTrue(isM3uPlaylist("\n  #EXTM3U\n#EXTINF:1,Song\nlibrary://track/1"))
    }

    @Test
    fun aFileWithoutTheHeaderIsNotAPlaylist() {
        assertFalse(isM3uPlaylist("library://track/1\n#EXTM3U"))
    }

    @Test
    fun theNameComesFromThePlaylistTag() {
        assertEquals("Road trip", m3uPlaylistName("#EXTM3U\n#PLAYLIST: Road trip \nx", "file.m3u8"))
    }

    @Test
    fun theNameFallsBackToTheFileNameWithoutItsExtension() {
        assertEquals("Mix", m3uPlaylistName("#EXTM3U\n#PLAYLIST:  \nx", "Mix.M3U8"))
        assertEquals("Old", m3uPlaylistName("#EXTM3U\nx", "Old.m3u"))
    }

    @Test
    fun theExportFileNameReplacesUnsafeCharacters() {
        assertEquals("AC_DC _ best_.m3u8", m3uExportFileName("AC/DC : best?"))
    }

    @Test
    fun aBlankNameExportsAsPlaylist() {
        assertEquals("playlist.m3u8", m3uExportFileName("  "))
    }
}
