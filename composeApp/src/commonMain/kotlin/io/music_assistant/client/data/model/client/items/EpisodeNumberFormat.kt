package io.music_assistant.client.data.model.client.items

import musicassistantclient.composeapp.generated.resources.Res
import musicassistantclient.composeapp.generated.resources.episode_number
import musicassistantclient.composeapp.generated.resources.episode_number_season
import org.jetbrains.compose.resources.getString

/**
 * Localized templates for the "S2 E5" / "E5" episode label. The phone resolves them per
 * composition; Android Auto and CarPlay load them once, because their item builders are
 * synchronous. Every surface formats through [label] and [subtitleWith].
 */
data class EpisodeNumberFormat(
    val seasonEpisode: String,
    val episodeOnly: String,
) {
    /** Null when the publisher does not number the episode, like the web. */
    fun label(season: Int?, episode: Int?): String? = episode?.let { number ->
        season?.let { seasonEpisode.replace("%1\$d", "$it").replace("%2\$d", "$number") }
            ?: episodeOnly.replace("%1\$d", "$number")
    }

    companion object {
        /** Source-language seed for a surface that renders before [load] completes. */
        val SOURCE = EpisodeNumberFormat(seasonEpisode = "S%1\$d E%2\$d", episodeOnly = "E%1\$d")

        suspend fun load(): EpisodeNumberFormat = EpisodeNumberFormat(
            seasonEpisode = getString(Res.string.episode_number_season),
            episodeOnly = getString(Res.string.episode_number),
        )
    }
}

/** [AppMediaItem.subtitle], prefixed with the episode label for a numbered podcast episode. */
fun AppMediaItem.subtitleWith(format: EpisodeNumberFormat): String? =
    (this as? PodcastEpisode)?.let { episode ->
        listOfNotNull(format.label(episode.season, episode.episodeNumber), episode.subtitle)
            .joinToString(" · ")
            .ifEmpty { null }
    } ?: subtitle
