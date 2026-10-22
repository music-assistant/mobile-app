package io.music_assistant.client.auto

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.music_assistant.client.data.model.client.items.EpisodeNumberFormat
import io.music_assistant.client.data.model.client.items.PodcastEpisode
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AndroidAutoEpisodeSubtitleTest {
    private val episode = PodcastEpisode(
        itemId = "e1", provider = "podcastfeed--x", name = "Episode", providerMappings = null,
        metadata = null, favorite = null, uri = null, images = emptyMap(), duration = null,
        isPlayable = true, podcast = null, fullyPlayed = null, resumePositionMs = null, version = null,
        episodeNumber = 5, season = 2,
    )

    @Test
    fun `episode row subtitle carries the localized episode label`() {
        val description = episode.toMediaDescription(
            defaultIconUri = DEFAULT_ICON,
            artworkUri = { null },
            episodeNumbers = EpisodeNumberFormat(
                seasonEpisode = "Staffel %1\$d, Folge %2\$d",
                episodeOnly = "Folge %1\$d",
            ),
        )

        assertEquals("Staffel 2, Folge 5", description.subtitle)
    }

    private companion object {
        val DEFAULT_ICON: Uri = Uri.parse("android.resource://io.music_assistant.client/drawable/default")
    }
}
