package io.music_assistant.client.data.model.client.items

import io.music_assistant.client.data.model.client.testPodcast
import io.music_assistant.client.data.model.client.testPodcastEpisode
import io.music_assistant.client.utils.formatIsoDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class EpisodeNumberFormatTest {
    private val format = EpisodeNumberFormat.SOURCE
    private val date = "2026-10-03"

    @Test
    fun labelsSeasonAndEpisode() {
        assertEquals("S2 E5", format.label(season = 2, episode = 5))
    }

    @Test
    fun labelsEpisodeWithoutSeason() {
        assertEquals("E5", format.label(season = null, episode = 5))
    }

    // Like the web: a season alone says nothing about the episode.
    @Test
    fun noLabelWithoutEpisodeNumber() {
        assertNull(format.label(season = 2, episode = null))
        assertNull(format.label(season = null, episode = null))
    }

    @Test
    fun usesTheGivenTemplates() {
        val localized = EpisodeNumberFormat(seasonEpisode = "Staffel %1\$d, Folge %2\$d", episodeOnly = "Folge %1\$d")

        assertEquals("Staffel 1, Folge 12", localized.label(season = 1, episode = 12))
        assertEquals("Folge 12", localized.label(season = null, episode = 12))
    }

    @Test
    fun subtitleJoinsLabelAndDate() {
        val episode = testPodcastEpisode().copy(episodeNumber = 5, season = 2, releaseDate = date)

        assertEquals("S2 E5 · ${formatIsoDate(date)}", episode.subtitleWith(format))
    }

    @Test
    fun subtitleIsLabelWithoutDate() {
        val episode = testPodcastEpisode().copy(episodeNumber = 5)

        assertEquals("E5", episode.subtitleWith(format))
    }

    // Server 2.10 sends no numbers: the subtitle stays the date.
    @Test
    fun subtitleIsDateWithoutNumbers() {
        val episode = testPodcastEpisode().copy(releaseDate = date)

        assertEquals(episode.subtitle, episode.subtitleWith(format))
    }

    @Test
    fun subtitleIsNullWithoutNumbersOrDate() {
        assertNull(testPodcastEpisode().subtitleWith(format))
    }

    @Test
    fun otherItemsKeepTheirSubtitle() {
        val podcast = testPodcast()

        assertEquals(podcast.subtitle, podcast.subtitleWith(format))
    }
}
