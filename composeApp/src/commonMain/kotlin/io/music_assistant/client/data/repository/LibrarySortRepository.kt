package io.music_assistant.client.data.repository

import co.touchlab.kermit.Logger
import io.music_assistant.client.api.Request
import io.music_assistant.client.api.ServiceClient
import io.music_assistant.client.data.model.client.MediaType
import io.music_assistant.client.data.model.client.SortConfig
import io.music_assistant.client.data.model.client.SortField
import io.music_assistant.client.data.model.client.SortOffer
import io.music_assistant.client.data.model.client.SortOption
import io.music_assistant.client.data.model.server.ListSort
import io.music_assistant.client.data.model.server.ServerInfo
import io.music_assistant.client.data.model.server.ServerSortOption
import io.music_assistant.client.data.model.server.SortWire
import io.music_assistant.client.data.model.server.sortWireFor
import io.music_assistant.client.utils.HasConnectionData
import io.music_assistant.client.utils.resultAs
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** The sort choices of one library list on the connected server. */
data class LibrarySortMenu(
    val offers: List<SortOffer>,
    val default: SortOption,
    val wire: SortWire,
) {
    /** [chosen] while the list offers its field, else the list default. */
    fun resolve(chosen: SortOption?): SortOption =
        chosen?.takeIf { option -> offers.any { it.field == option.field } } ?: default

    fun sortFor(option: SortOption) = ListSort(option, wire)
}

/**
 * The sort choices of the library lists, for every surface that sorts one.
 *
 * From server 2.11 (`sortWireFor`), `music/sort_options` gives the fields of each list, the
 * default first. The answer is a fixed table of the server version, so it is loaded once per
 * list and server. An older server, or a failed load, gets the static [SortConfig] table, whose
 * fields every 2.11 library list also offers.
 */
class LibrarySortRepository(private val apiClient: ServiceClient) {
    private val log = Logger.withTag("LibrarySort")
    private val mutex = Mutex()
    private val cache = mutableMapOf<CacheKey, List<SortOffer>>()

    suspend fun menu(mediaType: MediaType): LibrarySortMenu {
        val serverInfo = (apiClient.sessionState.value as? HasConnectionData)?.serverInfo
        val wire = sortWireFor(serverInfo?.schemaVersion)
        val appDefault = SortConfig.defaultFor(mediaType)
        return serverOffers(mediaType, serverInfo, wire)
            ?.let { offers ->
                // The app default wins while the server offers it, so a list keeps its order
                // across server versions (podcasts: newest first). Else the server default applies.
                val default = appDefault.takeIf { option -> offers.any { it.field == option.field } }
                    ?: SortOption(offers.first().field, offers.first().defaultDescending)
                LibrarySortMenu(offers, default, wire)
            }
            ?: LibrarySortMenu(
                offers = SortConfig.fieldsFor(mediaType).map(::SortOffer),
                default = appDefault,
                wire = wire,
            )
    }

    private suspend fun serverOffers(mediaType: MediaType, serverInfo: ServerInfo?, wire: SortWire): List<SortOffer>? {
        if (wire != SortWire.SORT_FIELD) return null
        val info = serverInfo ?: return null
        val listing = libraryListingOf(mediaType) ?: return null
        val key = CacheKey(info.serverId, info.schemaVersion, listing)
        mutex.withLock { cache[key] }?.let { return it }
        // A failure is not cached, so the next list load asks again.
        val offers = apiClient.sendRequest(Request.Sort.options(listing))
            .resultAs<List<ServerSortOption>>()
            ?.toSortOffers()
            ?.takeIf { it.isNotEmpty() }
            ?: run {
                log.w { "No sort options for $listing, the static table applies" }
                return null
            }
        mutex.withLock { cache[key] = offers }
        return offers
    }

    private fun libraryListingOf(mediaType: MediaType): String? = when (mediaType) {
        MediaType.ARTIST -> "library_artists"
        MediaType.ALBUM -> "library_albums"
        MediaType.TRACK -> "library_tracks"
        MediaType.PLAYLIST -> "library_playlists"
        MediaType.RADIO -> "library_radios"
        MediaType.AUDIOBOOK -> "library_audiobooks"
        MediaType.PODCAST -> "library_podcasts"
        MediaType.GENRE -> "library_genres"
        else -> null
    }

    private data class CacheKey(val serverId: String, val schemaVersion: Int?, val listing: String)
}

/**
 * Drops the options without a [SortField]: a field that this app version has no label for, and the
 * random orders (see [SortField]). The labels are local strings per field, because the app does not
 * use the server translations of `label_key`.
 */
internal fun List<ServerSortOption>.toSortOffers(): List<SortOffer> = mapNotNull { option ->
    SortField.fromServerKey(option.field)?.let {
        SortOffer(
            field = it,
            defaultDescending = option.defaultDirection == "desc",
            supportsDirection = option.supportsDirection,
        )
    }
}
