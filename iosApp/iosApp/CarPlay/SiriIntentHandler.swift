import Foundation
import Intents
import ComposeApp
import os.log

private let log = OSLog(
    subsystem: Bundle.main.bundleIdentifier ?? "io.music-assistant.client",
    category: "Siri"
)

/// Single handler conforming to the three media-domain SiriKit intent
/// protocols MA supports:
///
///  - `INPlayMediaIntent` — "Hey Siri, play X in Music Assistant"
///  - `INMediaAffinityIntent` — "I love/like/dislike this song"
///  - `INSearchForMediaIntent` — "Search for X in Music Assistant"
///
/// Donations matter as much as resolution. Siri never lists MA as a media
/// destination unless the app donates `INPlayMediaIntent` from successful
/// plays — that's the signal Siri uses to predict "the user plays music
/// through this app." Donate from in-app/CarPlay-initiated plays *and* from
/// Siri's own successful `handle()` (reinforces the model).
class SiriIntentHandler: NSObject {

    // MARK: - Resolved Item Cache (shared across handler instances)

    /// Siri may recreate the handler between resolve and handle phases.
    /// The cache outlives a single instance so `handle` doesn't need to
    /// re-search the server by name to recover the `AppMediaItem`. NSCache
    /// gives us thread-safe access and bounded memory.
    private static let resolvedCache: NSCache<NSString, AppMediaItem> = {
        let cache = NSCache<NSString, AppMediaItem>()
        cache.countLimit = 100
        return cache
    }()

    private static func cache(_ items: [AppMediaItem]) {
        for item in items {
            resolvedCache.setObject(item, forKey: item.itemId as NSString)
        }
    }

    private static func cached(forIdentifier id: String) -> AppMediaItem? {
        resolvedCache.object(forKey: id as NSString)
    }

    // MARK: - Donation

    /// Donate an `INPlayMediaIntent` for an item the user just chose to play.
    /// This is what tells Siri "Music Assistant is a media destination" — without
    /// donations the app never appears in Siri's list of candidate apps for
    /// "play X" prompts.
    ///
    /// No-ops for AppMediaItem subtypes that don't map to a Siri-playable kind
    /// (e.g. `RecommendationFolder` from the CarPlay browse grid). Donating
    /// those would tell Siri the user "played" a category, polluting the
    /// prediction model.
    static func donatePlayed(_ item: AppMediaItem) {
        guard let mediaItem = mapToINMediaItem(item) else {
            os_log("donatePlayed: skipping non-mappable item kind=%{public}@ name=%{public}@",
                   log: log, type: .info,
                   String(describing: Swift.type(of: item)),
                   item.title)
            return
        }
        // Server id is the namespace for `itemId`, which is server-relative.
        // If we somehow reach this without a connected server, we have no
        // way to scope the identifier — and no real item to donate either,
        // since callers only invoke this after a successful play. Bail
        // loudly rather than write a malformed donation.
        guard let serverId = KmpHelper.shared.getServerId(), !serverId.isEmpty else {
            os_log("donatePlayed: no serverId — refusing to donate (caller invariant violated: donations only after a successful play)",
                   log: log, type: .error)
            return
        }
        let intent = INPlayMediaIntent(
            mediaItems: [mediaItem],
            mediaContainer: nil,
            playShuffled: nil,
            playbackRepeatMode: .none,
            resumePlayback: nil,
            playbackQueueLocation: .now,
            playbackSpeed: nil,
            mediaSearch: nil
        )
        intent.suggestedInvocationPhrase = "Play \(item.title)"

        let interaction = INInteraction(
            intent: intent,
            response: INPlayMediaIntentResponse(code: .success, userActivity: nil)
        )
        // Server id + item id together are stable and unique across MA
        // instances. Re-plays of the same item update the same donation
        // rather than fragmenting Siri's model across many entries.
        interaction.identifier = "play-\(serverId)-\(item.itemId)"
        interaction.donate { error in
            if let error = error {
                os_log("Failed to donate INPlayMediaIntent: %{public}@",
                       log: log, type: .error, String(describing: error))
            }
        }

        // Keep the AppMediaItem alive for any follow-up affinity intent
        // ("I like this song") that resolves against this donation.
        cache([item])
    }

    // MARK: - Mapping

    /// Map an `AppMediaItem` to its SiriKit `INMediaItem` representation.
    /// Returns nil for subtypes that have no sensible Siri mapping (today:
    /// `RecommendationFolder`, which is a browse-grid category, not a
    /// playable target). Callers must skip nil results: donating, returning
    /// in resolution, or playing a non-mappable item all corrupt Siri's
    /// model.
    ///
    /// Coverage of `AppMediaItem` subtypes:
    ///   Track            → .song
    ///   Album            → .album
    ///   Artist           → .artist
    ///   Playlist         → .playlist
    ///   Audiobook        → .audioBook
    ///   RadioStation     → .radioStation
    ///   Podcast          → .podcastShow
    ///   PodcastEpisode   → .podcastEpisode
    ///   Genre            → .musicStation  (no INMediaItemType.genre exists;
    ///                      "play <genre>" semantically maps to a station)
    ///   RecommendationFolder → nil  (filter out)
    fileprivate static func mapToINMediaItem(_ item: AppMediaItem) -> INMediaItem? {
        let type: INMediaItemType
        if item is AppMediaItem.Track {
            type = .song
        } else if item is AppMediaItem.Album {
            type = .album
        } else if item is AppMediaItem.Artist {
            type = .artist
        } else if item is AppMediaItem.Playlist {
            type = .playlist
        } else if item is AppMediaItem.Audiobook {
            type = .audioBook
        } else if item is AppMediaItem.RadioStation {
            type = .radioStation
        } else if item is AppMediaItem.Podcast {
            type = .podcastShow
        } else if item is AppMediaItem.PodcastEpisode {
            type = .podcastEpisode
        } else if item is AppMediaItem.Genre {
            type = .musicStation
        } else {
            // RecommendationFolder, plus any future AppMediaItem subtype we
            // haven't taught Siri about. Returning nil keeps the prediction
            // model clean.
            return nil
        }

        return INMediaItem(
            identifier: item.itemId,
            title: item.title,
            type: type,
            artwork: nil,
            artist: item.subtitle
        )
    }

    /// Cache the `AppMediaItem` set then map them to the SiriKit equivalents.
    /// Used by every `resolve…` entry point that performs a server search.
    /// Items with no Siri mapping (see `mapToINMediaItem`) are dropped from
    /// the returned list but kept in the cache — `handle()` may still want
    /// them by id if Siri hands one back unexpectedly.
    fileprivate static func resolveAndCache(_ items: [AppMediaItem]) -> [INMediaItem] {
        cache(items)
        return items.compactMap(mapToINMediaItem)
    }

    /// Extract the best query string Siri gave us. Three input shapes:
    ///
    ///  1. `intent.mediaSearch?.mediaName` set — first pass, user said "play X".
    ///     Siri's voice transcript drives the search.
    ///  2. `intent.mediaItems` pre-populated — second pass after Siri ran its
    ///     own disambiguation against its global music catalog (Apple Music's
    ///     graph, regardless of which app handles the intent). The identifiers
    ///     in those items belong to Siri, not us, so we re-search MA by title
    ///     (and artist when available) and return *our* items.
    ///  3. Neither — give up; nothing to do.
    ///
    /// Returns nil only when we have nothing to search by.
    fileprivate static func queryString(from intent: INPlayMediaIntent) -> String? {
        // Voice transcript wins when present.
        if let name = intent.mediaSearch?.mediaName, !name.isEmpty {
            // Append a hinted artist when Siri parsed one — improves MA match
            // accuracy for "play <track> by <artist>" phrasing.
            if let artist = intent.mediaSearch?.artistName, !artist.isEmpty {
                return "\(name) \(artist)"
            }
            return name
        }
        // Fall back to a Siri-resolved item.
        if let item = intent.mediaItems?.first {
            let title = item.title ?? ""
            let artist = item.artist ?? ""
            let combined = "\(title) \(artist)".trimmingCharacters(in: .whitespaces)
            return combined.isEmpty ? nil : combined
        }
        return nil
    }

    /// What kind of media did the user ask for? Sources, in priority order:
    /// the explicit type on a Siri-resolved `INMediaItem`, then the
    /// `mediaSearch.mediaType` voice-parsed type, else `.unknown`.
    fileprivate static func preferredType(from intent: INPlayMediaIntent) -> INMediaItemType {
        if let resolved = intent.mediaItems?.first {
            // INMediaItem.type is non-optional INMediaItemType.
            return resolved.type
        }
        return intent.mediaSearch?.mediaType ?? .unknown
    }

    /// Pick the single MA item that best matches the user's intent.
    /// Returns nil when no candidate is mappable to a Siri media type.
    ///
    /// Strategy:
    ///  1. Drop non-Siri-mappable items (e.g. RecommendationFolder).
    ///  2. Filter to type-matching candidates if Siri specified a type.
    ///  3. Score each remaining candidate by token overlap with the query
    ///     against the union of `name` and `subtitle` tokens. Including
    ///     subtitle (which is the artist for tracks/albums, "Audiobook"
    ///     for audiobooks, etc.) lets artist tokens in the query score
    ///     when present without creating dilution noise when absent.
    ///  4. Pick the highest-scored item; tiebreak by MA's original ordering
    ///     (which already reflects MA's relevance score, so first wins).
    ///
    /// Why score instead of trusting MA's order: MA's server-side relevance
    /// can put low-similarity items (e.g. an unrelated artist) above the
    /// obvious match (e.g. a same-named album). When Siri sends us a phrase
    /// like "Pirate's Gospel" with `mediaType=.unknown`, MA's first result
    /// ("Elsa") wins blindly with type-only filtering — and Elsa silently
    /// fails to play because the user wanted the album. Token-overlap
    /// scoring picks the actual *Pirate's Gospel* album instead.
    fileprivate static func bestMatch(
        in items: [AppMediaItem],
        for type: INMediaItemType,
        query: String
    ) -> AppMediaItem? {
        // 1. Drop non-mappable items so bestMatch never returns something the
        //    callers will have to discard anyway.
        let mappable = items.filter { mapToINMediaItem($0) != nil }
        guard !mappable.isEmpty else { return nil }

        // 2. Type filtering. Empty filtered set → fall back to all mappable
        //    items (Siri very often leaves type=.unknown for non-Apple-Music
        //    apps, so the unfiltered fallback is the common case).
        let typeFiltered: [AppMediaItem] = {
            switch type {
            case .artist:
                return mappable.filter { $0 is AppMediaItem.Artist }
            case .album:
                return mappable.filter { $0 is AppMediaItem.Album }
            case .song:
                return mappable.filter { $0 is AppMediaItem.Track }
            case .playlist:
                return mappable.filter { $0 is AppMediaItem.Playlist }
            case .audioBook:
                return mappable.filter { $0 is AppMediaItem.Audiobook }
            case .radioStation, .algorithmicRadioStation:
                return mappable.filter { $0 is AppMediaItem.RadioStation }
            case .musicStation:
                // We map Genre → .musicStation; RadioStation is the closer
                // analogue when a real station was requested. Accept either.
                return mappable.filter { $0 is AppMediaItem.RadioStation || $0 is AppMediaItem.Genre }
            case .podcastShow, .podcastStation, .podcastPlaylist:
                return mappable.filter { $0 is AppMediaItem.Podcast }
            case .podcastEpisode:
                return mappable.filter { $0 is AppMediaItem.PodcastEpisode }
            default:
                return []
            }
        }()
        let candidates = typeFiltered.isEmpty ? mappable : typeFiltered

        // 3. Score by token overlap against name ∪ subtitle. Highest score
        //    wins; ties broken by original order (MA's relevance is the
        //    tiebreaker, not the primary sort).
        let queryTokens = Self.tokens(query)
        guard !queryTokens.isEmpty else {
            return candidates.first
        }
        let scored = candidates.enumerated().map { idx, item -> (item: AppMediaItem, score: Double, idx: Int) in
            // Score against `name` plus `subtitle` (artist names for
            // tracks/albums, descriptive labels otherwise). This lets an
            // artist token in the query lift the album that names that
            // artist, without dragging unrelated items down.
            let itemTokens = Self.tokens(item.title)
                .union(Self.tokens(item.subtitle ?? ""))
            let overlap = queryTokens.intersection(itemTokens).count
            let score = Double(overlap) / Double(queryTokens.count)
            return (item, score, idx)
        }
        // `max(by:)` returns the element NOT less than any other under the
        // given predicate. The score branch is plain `<`. The tiebreak
        // `lhs.idx > rhs.idx` makes higher-idx items "less than" lower-idx
        // items, so the lowest idx wins on ties (MA's ordering preserved).
        let best = scored.max(by: { lhs, rhs in
            if lhs.score != rhs.score { return lhs.score < rhs.score }
            return lhs.idx > rhs.idx  // lower idx = better tiebreak
        })

        if let best = best {
            os_log("bestMatch: picked name=%{public}@ kind=%{public}@ score=%{public}.2f from %d candidates (type-filtered=%d, mappable=%d)",
                   log: log, type: .info,
                   best.item.title,
                   String(describing: Swift.type(of: best.item)),
                   best.score,
                   candidates.count,
                   typeFiltered.count,
                   mappable.count)
        }
        return best?.item
    }

    /// Normalize a string into a set of comparable tokens.
    ///
    /// Steps: fold diacritics + width (so "Beyoncé" matches "Beyonce" and
    /// fullwidth characters match their narrow forms), lowercase, strip
    /// apostrophes/punctuation, split on non-alphanumerics, drop empties.
    ///
    /// Without diacritic folding, MA-indexed names that include accents
    /// (e.g. "Café Tacvba", "Mötley Crüe") silently fail to score against
    /// Siri voice transcripts that arrive ASCII-only.
    private static func tokens(_ s: String) -> Set<String> {
        let normalized = s
            .folding(options: [.diacriticInsensitive, .widthInsensitive], locale: nil)
            .lowercased()
            .replacingOccurrences(of: "'", with: "")
            .replacingOccurrences(of: "\u{2019}", with: "") // curly apostrophe
            .components(separatedBy: CharacterSet.alphanumerics.inverted)
            .filter { !$0.isEmpty }
        return Set(normalized)
    }
}

// MARK: - INPlayMediaIntentHandling

extension SiriIntentHandler: INPlayMediaIntentHandling {

    func resolveMediaItems(
        for intent: INPlayMediaIntent,
        with completion: @escaping ([INPlayMediaMediaItemResolutionResult]) -> Void
    ) {
        os_log("PlayMedia.resolveMediaItems: entry. intent=%{public}@",
               log: log, type: .info, String(describing: intent))
        // Explicit dump of mediaSearch fields — `description()` on
        // INPlayMediaIntent suppresses nil properties from the printed form,
        // so the parent dump alone can hide whether Siri passed an album /
        // artist hint alongside (or instead of) a resolved `mediaItem`.
        let ms = intent.mediaSearch
        os_log("PlayMedia.resolveMediaItems: mediaSearch.mediaName=%{public}@ albumName=%{public}@ artistName=%{public}@ mediaType=%{public}d genres=%{public}@",
               log: log, type: .info,
               ms?.mediaName ?? "<nil>",
               ms?.albumName ?? "<nil>",
               ms?.artistName ?? "<nil>",
               (ms?.mediaType ?? .unknown).rawValue,
               String(describing: ms?.genreNames))
        os_log("PlayMedia.resolveMediaItems: mediaContainer=%{public}@",
               log: log, type: .info, String(describing: intent.mediaContainer))

        guard let query = Self.queryString(from: intent) else {
            os_log("PlayMedia.resolveMediaItems: BAIL — no usable query (no mediaSearch.mediaName, no mediaItems). Returning .unsupported.",
                   log: log, type: .error)
            completion([.unsupported()])
            return
        }
        guard KmpState.isReady else {
            os_log("PlayMedia.resolveMediaItems: BAIL — KmpState.isReady=false. Returning .unsupported.",
                   log: log, type: .error)
            completion([.unsupported()])
            return
        }

        let preferredType = Self.preferredType(from: intent)
        os_log("PlayMedia.resolveMediaItems: searching MA for query=%{public}@ preferredType=%{public}d",
               log: log, type: .info, query, preferredType.rawValue)
        KmpHelper.shared.search(query: query) { items in
            os_log("PlayMedia.resolveMediaItems: search returned %d items",
                   log: log, type: .info, items.count)
            // Cache everything so handle() can find any MA item by id, but
            // resolve to a single best match. We never return .disambiguation
            // here: Siri sends each user tap back as a fresh intent, and any
            // non-.success result restarts the cycle, producing an infinite
            // disambig loop.
            Self.cache(items)
            guard let best = Self.bestMatch(in: items, for: preferredType, query: query) else {
                os_log("PlayMedia.resolveMediaItems: no Siri-mappable items — .unsupported",
                       log: log, type: .info)
                completion([.unsupported()])
                return
            }
            guard let resolved = Self.mapToINMediaItem(best) else {
                completion([.unsupported()])
                return
            }
            os_log("PlayMedia.resolveMediaItems: best match name=%{public}@ kind=%{public}@ — .success",
                   log: log, type: .info, best.title, String(describing: type(of: best)))
            completion([.success(with: resolved)])
        }
    }

    func handle(
        intent: INPlayMediaIntent,
        completion: @escaping (INPlayMediaIntentResponse) -> Void
    ) {
        os_log("PlayMedia.handle: entry. mediaItems.count=%d",
               log: log, type: .info, intent.mediaItems?.count ?? -1)

        guard KmpState.isReady else {
            os_log("PlayMedia.handle: KMP not ready — failureRequiringAppLaunch", log: log, type: .error)
            completion(INPlayMediaIntentResponse(code: .failureRequiringAppLaunch, userActivity: nil))
            return
        }
        guard let selected = intent.mediaItems?.first else {
            os_log("PlayMedia.handle: no selected mediaItem — .failure", log: log, type: .error)
            completion(INPlayMediaIntentResponse(code: .failure, userActivity: nil))
            return
        }

        // Cache hit: skip the second server search entirely. This is the
        // common path when `resolveMediaItems` ran moments ago and returned
        // an item with our identifier. Won't fire when the resolved item
        // came from Siri's catalog (different identifier namespace).
        if let identifier = selected.identifier,
           let cached = Self.cached(forIdentifier: identifier) {
            os_log("PlayMedia.handle: cache hit for id=%{public}@ — playing", log: log, type: .info, identifier)
            KmpHelper.shared.playMediaItem(item: cached)
            Self.donatePlayed(cached)
            completion(INPlayMediaIntentResponse(code: .success, userActivity: nil))
            return
        }

        // Cache miss — re-search MA. This is the common path when Siri
        // pre-resolved against its own catalog (e.g., user picked an item
        // from Siri's disambig list). The identifier in the intent is
        // Siri's, not ours; we have to find the equivalent MA item by
        // title/artist.
        guard let query = Self.queryString(from: intent) else {
            os_log("PlayMedia.handle: cache miss + no query — .failure", log: log, type: .error)
            completion(INPlayMediaIntentResponse(code: .failure, userActivity: nil))
            return
        }
        let preferredType = Self.preferredType(from: intent)
        os_log("PlayMedia.handle: cache miss; re-searching for query=%{public}@ preferredType=%{public}d",
               log: log, type: .info, query, preferredType.rawValue)
        KmpHelper.shared.search(query: query) { items in
            // Prefer an exact identifier match (defensive — Siri *could*
            // round-trip our id from a prior `.success(with:)`); otherwise
            // type-aware best match.
            let match: AppMediaItem? = {
                if let id = selected.identifier,
                   let exact = items.first(where: { $0.itemId == id }) {
                    return exact
                }
                return Self.bestMatch(in: items, for: preferredType, query: query)
            }()
            guard let match = match else {
                os_log("PlayMedia.handle: re-search returned no items for query=%{public}@ — .failure",
                       log: log, type: .error, query)
                completion(INPlayMediaIntentResponse(code: .failure, userActivity: nil))
                return
            }
            os_log("PlayMedia.handle: playing match name=%{public}@ kind=%{public}@",
                   log: log, type: .info, match.title, String(describing: type(of: match)))
            KmpHelper.shared.playMediaItem(item: match)
            Self.donatePlayed(match)
            completion(INPlayMediaIntentResponse(code: .success, userActivity: nil))
        }
    }
}

// MARK: - INUpdateMediaAffinityIntentHandling
//
// MA stores favorites as a single boolean (no negative signal), so we map:
//
//   .like    → addFavorite (URI-based call into the server)
//   .dislike → removeFavorite (id+mediaType-based call); a no-op if the item
//              wasn't already favorited
//   .unknown → reject with needsValue so Siri prompts the user to clarify
//
// Siri delivers the affinity intent with the most recently donated/played
// media context, so the cache populated by `donatePlayed` typically resolves
// "this song" without any new server round-trip.

extension SiriIntentHandler: INUpdateMediaAffinityIntentHandling {

    func resolveMediaItems(
        for intent: INUpdateMediaAffinityIntent,
        with completion: @escaping ([INUpdateMediaAffinityMediaItemResolutionResult]) -> Void
    ) {
        // Siri may pre-fill `mediaItems` based on prior donations (the
        // currently-playing item, last-played item, etc.). Trust those.
        if let preset = intent.mediaItems, !preset.isEmpty {
            completion(preset.map { .success(with: $0) })
            return
        }

        guard let query = intent.mediaSearch?.mediaName, !query.isEmpty else {
            completion([.unsupported()])
            return
        }
        guard KmpState.isReady else {
            completion([.unsupported()])
            return
        }

        // Why we *do* return .disambiguation here, while PlayMedia's
        // resolve does not (see `PlayMedia.resolveMediaItems`):
        //
        //   - Affinity is a one-shot mutation. Siri presents the disambig
        //     list, the user taps one item, Siri sends a fresh intent with
        //     `mediaItems` pre-filled, and the early return above returns
        //     `.success` for it. No loop — the resolution converges.
        //
        //   - PlayMedia is the cycle that loops. Each tap re-enters resolve
        //     against MA's catalog (Siri's identifiers don't match ours)
        //     and would re-disambig forever, hence its no-disambig policy.
        KmpHelper.shared.search(query: query) { items in
            let mediaItems = Self.resolveAndCache(items)
            if mediaItems.isEmpty {
                completion([.unsupported()])
            } else if mediaItems.count == 1 {
                completion([.success(with: mediaItems[0])])
            } else {
                // Cap to keep the Siri disambig list scannable on CarPlay
                // and small phone screens. MA's search returns up to 10
                // results across types — too many to scroll on a head unit.
                let capped = Array(mediaItems.prefix(5))
                completion([.disambiguation(with: capped)])
            }
        }
    }

    func resolveAffinityType(
        for intent: INUpdateMediaAffinityIntent,
        with completion: @escaping (INMediaAffinityTypeResolutionResult) -> Void
    ) {
        switch intent.affinityType {
        case .like, .dislike:
            completion(.success(with: intent.affinityType))
        case .unknown:
            completion(.needsValue())
        @unknown default:
            completion(.needsValue())
        }
    }

    func handle(
        intent: INUpdateMediaAffinityIntent,
        completion: @escaping (INUpdateMediaAffinityIntentResponse) -> Void
    ) {
        os_log("Affinity.handle: entry. affinityType=%{public}d mediaItems.count=%d mediaSearch.mediaName=%{public}@ mediaSearch.mediaType=%{public}d mediaSearch.artistName=%{public}@",
               log: log, type: .info,
               intent.affinityType.rawValue,
               intent.mediaItems?.count ?? -1,
               intent.mediaSearch?.mediaName ?? "<nil>",
               (intent.mediaSearch?.mediaType ?? .unknown).rawValue,
               intent.mediaSearch?.artistName ?? "<nil>")

        guard KmpState.isReady else {
            os_log("Affinity.handle: KMP not ready — failureRequiringAppLaunch", log: log, type: .error)
            completion(INUpdateMediaAffinityIntentResponse(code: .failureRequiringAppLaunch, userActivity: nil))
            return
        }

        let favorite: Bool
        switch intent.affinityType {
        case .like:
            favorite = true
        case .dislike:
            favorite = false
        default:
            os_log("Affinity.handle: affinityType=%{public}d not like/dislike — .failure",
                   log: log, type: .error, intent.affinityType.rawValue)
            completion(INUpdateMediaAffinityIntentResponse(code: .failure, userActivity: nil))
            return
        }

        // When `mediaSearch.mediaName` is present, it represents the user's
        // expressed media name (often the currently-playing track title for
        // "I love this song" phrasings). Siri's disambig may have led the
        // user to tap an unrelated catalog candidate (e.g., the artist of
        // the song), so we prefer mediaName-driven re-search over the
        // cache-hit shortcut. This keeps the *server-side* favorite applied
        // to the right MA item even when Siri's UI flow ends in
        // "something went wrong" due to its own song/artist type mismatch.
        let mediaSearchName = intent.mediaSearch?.mediaName?.trimmingCharacters(in: .whitespaces)
        let preferMediaSearch = (mediaSearchName?.isEmpty == false)

        let target: INMediaItem? = intent.mediaItems?.first

        if !preferMediaSearch,
           let target = target,
           let identifier = target.identifier,
           let cached = Self.cached(forIdentifier: identifier) {
            let dispatched = KmpHelper.shared.setFavorite(
                item: cached,
                favorite: favorite
            )
            os_log("Affinity.handle: cache hit for id=%{public}@ favorite=%{public}@ dispatched=%{public}@",
                   log: log, type: .info,
                   identifier,
                   favorite ? "true" : "false",
                   dispatched ? "true" : "false")
            // setFavorite returns false synchronously when the request can't
            // be formed (e.g. addFavorite for an item without any URI).
            // Surface that as `.failure` so the user hears Siri's failure
            // tone instead of a false "Done."
            let code: INUpdateMediaAffinityIntentResponseCode = dispatched ? .success : .failure
            completion(INUpdateMediaAffinityIntentResponse(code: code, userActivity: nil))
            return
        }

        // Re-search MA. Build a query from whatever Siri gave us — prefer
        // mediaSearch fields (voice-derived) over the resolved-item title
        // (disambig-tap-derived, less reliable).
        let queryParts = [
            intent.mediaSearch?.mediaName,
            intent.mediaSearch?.artistName,
            intent.mediaSearch?.albumName,
            target?.title,
            target?.artist
        ].compactMap { $0?.isEmpty == false ? $0 : nil }
        let query = queryParts.joined(separator: " ")
        guard !query.isEmpty else {
            os_log("Affinity.handle: no query string can be assembled — .failure", log: log, type: .error)
            completion(INUpdateMediaAffinityIntentResponse(code: .failure, userActivity: nil))
            return
        }

        // Type preference: prefer Siri's expressed mediaType when set; fall
        // back to the resolved item's type. For "I love this song" Siri sets
        // mediaSearch.mediaType=.song even though the resolved item from
        // disambig may be `.artist`.
        let preferredType: INMediaItemType = {
            if let t = intent.mediaSearch?.mediaType, t != .unknown { return t }
            return target?.type ?? .unknown
        }()

        os_log("Affinity.handle: searching MA for query=%{public}@ preferredType=%{public}d",
               log: log, type: .info, query, preferredType.rawValue)
        KmpHelper.shared.search(query: query) { items in
            let match: AppMediaItem? = {
                if let id = target?.identifier,
                   let exact = items.first(where: { $0.itemId == id }) {
                    return exact
                }
                return Self.bestMatch(in: items, for: preferredType, query: query)
            }()
            guard let match = match else {
                os_log("Affinity.handle: re-search returned no items for query=%{public}@ — .failure",
                       log: log, type: .error, query)
                completion(INUpdateMediaAffinityIntentResponse(code: .failure, userActivity: nil))
                return
            }
            let dispatched = KmpHelper.shared.setFavorite(
                item: match,
                favorite: favorite
            )
            os_log("Affinity.handle: applying favorite=%{public}@ to name=%{public}@ kind=%{public}@ dispatched=%{public}@",
                   log: log, type: .info,
                   favorite ? "true" : "false",
                   match.title,
                   String(describing: Swift.type(of: match)),
                   dispatched ? "true" : "false")
            // setFavorite returns false synchronously when the request can't
            // be formed (e.g. addFavorite for an item without any URI).
            // Surface that as `.failure` instead of a false "Done."
            let code: INUpdateMediaAffinityIntentResponseCode = dispatched ? .success : .failure
            completion(INUpdateMediaAffinityIntentResponse(code: code, userActivity: nil))
        }
    }
}

// MARK: - INSearchForMediaIntentHandling
//
// "Search for X in Music Assistant" — surfaces results without auto-playing.
// Resolution returns the matched items; Siri presents them, and a follow-up
// "play that" routes back through `INPlayMediaIntent`.

extension SiriIntentHandler: INSearchForMediaIntentHandling {

    func resolveMediaItems(
        for intent: INSearchForMediaIntent,
        with completion: @escaping ([INSearchForMediaMediaItemResolutionResult]) -> Void
    ) {
        guard let query = intent.mediaSearch?.mediaName, !query.isEmpty else {
            completion([INSearchForMediaMediaItemResolutionResult(
                mediaItemResolutionResult: .unsupported()
            )])
            return
        }
        guard KmpState.isReady else {
            completion([INSearchForMediaMediaItemResolutionResult(
                mediaItemResolutionResult: .unsupported()
            )])
            return
        }

        KmpHelper.shared.search(query: query) { items in
            let mediaItems = Self.resolveAndCache(items)
            if mediaItems.isEmpty {
                completion([INSearchForMediaMediaItemResolutionResult(
                    mediaItemResolutionResult: .unsupported()
                )])
            } else {
                completion(INSearchForMediaMediaItemResolutionResult.successes(with: mediaItems))
            }
        }
    }

    func handle(
        intent: INSearchForMediaIntent,
        completion: @escaping (INSearchForMediaIntentResponse) -> Void
    ) {
        // Resolution already produced the matched items and put them in the
        // cache. Re-derive the response from the cache (looking up each
        // pre-resolved item by id) rather than passing `intent.mediaItems`
        // through verbatim — that ensures we never echo back something Siri
        // substituted from its own catalog. If the cache no longer has an
        // item (LRU eviction, cleared between resolve and handle), drop it
        // rather than surface a stale or foreign INMediaItem.
        let resolved: [INMediaItem] = (intent.mediaItems ?? []).compactMap { item in
            guard let id = item.identifier,
                  let cached = Self.cached(forIdentifier: id),
                  let mapped = Self.mapToINMediaItem(cached) else {
                return nil
            }
            return mapped
        }
        os_log("Search.handle: returning %d cached items (intent supplied %d)",
               log: log, type: .info,
               resolved.count, intent.mediaItems?.count ?? 0)
        let response = INSearchForMediaIntentResponse(code: .success, userActivity: nil)
        response.mediaItems = resolved
        completion(response)
    }
}
