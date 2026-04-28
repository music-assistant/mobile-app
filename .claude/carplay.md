# CarPlay & Siri Integration

**Last reviewed:** 2026-04-28

The iOS app exposes Music Assistant as both a CarPlay audio app and a Siri media destination. Both surfaces share the same KMP bridge (`KmpHelper`) for MA operations and a single `SiriIntentHandler` class for the three media-domain intents.

---

## CarPlay

### Template hierarchy

```
Root: CPListTemplate ("Library")
├── Section 1: Browse → CPGridTemplate (7 categories)
│   ├── Artists    (mic.fill)                          → push CPListTemplate
│   ├── Albums     (opticaldisc.fill)                  → push CPListTemplate
│   ├── Tracks     (music.note)                        → push CPListTemplate
│   ├── Playlists  (list.bullet.rectangle.fill)        → push CPListTemplate
│   ├── Audiobooks (book.fill)                         → push CPListTemplate
│   ├── Podcasts   (antenna.radiowaves.left.and.right) → push CPListTemplate
│   └── Radio      (radio.fill)                        → push CPListTemplate
└── Section 2: Recommendations (CPListImageRowItem, async loaded)
    └── RecommendationFolders with artwork thumbnails (up to 8 per folder)
```

User taps an item → `CarPlayContentManager.playItem()` → `KmpHelper.playMediaItem()` → `CPNowPlayingTemplate.shared` is pushed → `NowPlayingManager` updates `MPNowPlayingInfoCenter` for artwork/metadata/remote commands.

### Template constraints (iOS 18+)

- `CPTabBarTemplate` only accepts `CPListTemplate` and `CPGridTemplate` — **not** `CPSearchTemplate`.
- `CPSearchTemplate` cannot be pushed onto the navigation stack either.
- Pushable templates: `CPListTemplate`, `CPGridTemplate`, `CPNowPlayingTemplate`, `CPAlertTemplate`, `CPActionSheetTemplate`, `CPVoiceControlTemplate`.
- In-app search is unavailable; search is Siri-driven via `INSearchForMediaIntent`.

### Browse icons

`CPGridTemplate` with SF Symbols, adaptive theming via `UIImageAsset`:
- Dark: background `#404378` (primaryContainer), icon tint `#C0C1FF` (primary)
- Light: background `#E0DEFF`, icon tint `#575992`
- Icons centered, scaled to 80% of available space.

### Artwork loading

`CarPlayImageLoader` singleton: `NSCache`-backed image cache, async URL loading, SF Symbol placeholders while loading, in-place updates on `CPListItem` once images arrive.

---

## Siri integration

The app conforms to three media-domain intents via a single class (`SiriIntentHandler`) routed through `AppDelegate.application(_:handlerFor:)`. The same class implements all three protocols because they share the matching/caching machinery.

| Intent | Phrase | Behavior |
|---|---|---|
| `INPlayMediaIntent` | "Hey Siri, play X in Music Assistant" | Resolve → search MA → best-match → `KmpHelper.playMediaItem` → donate. |
| `INUpdateMediaAffinityIntent` | "I love/like/dislike this song" | Resolve from prior donations or search → `KmpHelper.setFavorite`. Like ⇒ add, dislike ⇒ remove. MA has no negative signal. |
| `INSearchForMediaIntent` | "search for X in Music Assistant" | Resolve only — surface results, don't play. Follow-up "play that" routes through `INPlayMediaIntent`. |

**Voice transport commands bypass `SiriIntentHandler` entirely.** "Hey Siri, skip this," "next track," "pause," and the steering-wheel buttons all flow through `MPRemoteCommand` (`nextTrackCommand`, `pauseCommand`, etc.), dispatched by `mediaremoted` from `com.apple.AssistantServices`. Those land in `NowPlayingManager.setupRemoteCommands()` and route to `NativeAudioController`'s `RemoteCommandHandler`, never hitting any of the three intents above. Confirmed empirically: a "Hey Siri, skip to next track" during in-car testing produced a `mediaremoted` log line `Received command from <com.apple.assistant_service>: NextTrack` with no `INPlayMediaIntent` activity at all.

Practical consequence when triaging "Hey Siri did the wrong thing" reports: classify by *what* was said. Content phrases ("play X", "search for Y", "I love this") → `SiriIntentHandler`. Transport phrases ("next", "previous", "pause", "skip") → `NowPlayingManager` / `NativeAudioController`. The two paths share no code; diagnosing the wrong one wastes time.

**Diagnostic flow** when Siri claims the app doesn't support media:
```
sudo log collect --device-name <iPhone> --last 2m --output /tmp/x.logarchive
log show /tmp/x.logarchive --predicate 'process == "assistant_service"' --info \
    | grep "InstalledAppProvider#installedApps filtered for intent:INPlayMediaIntent"
```
Our bundle id should appear in the result list. If it doesn't, this key is missing or the `INPlayMediaIntent` capability in Xcode's Signing & Capabilities is misconfigured.

**`NSUserActivityTypes`** (separate from `INIntentsSupported`):
```xml
<array>
    <string>INPlayMediaIntent</string>
    <string>INUpdateMediaAffinityIntent</string>
    <string>INSearchForMediaIntent</string>
</array>
```
`INIntentsSupported` enables **handling** an intent. `NSUserActivityTypes` enables **donating** one. Without it, `INInteraction.donate()` fails with `IntentsErrorDomain Code=1901 — "Donating intent 'INPlayMediaIntent' is not supported by this app"`. Handling works fine even without it, so this is easy to miss.

### Resolution / matching strategy

Both phases of an intent (resolve, handle) flow through `bestMatch(in:for:query:)`. The strategy:

1. **Filter to Siri-mappable items** (drop `RecommendationFolder`).
2. **Type-filter by Siri's expressed `INMediaItemType`** if set; fall back to all mappable items if not (Siri leaves type=`.unknown` for non-Apple-Music apps, so the unfiltered fallback is the common case).
3. **Score by token overlap** against the union of `name` and `subtitle` tokens. `tokens()` lowercases, folds diacritics + width, and strips apostrophes/punctuation. Including `subtitle` (artist for tracks/albums) lets queries like "Pirate's Gospel by Alela Diane" lift the album over an unrelated artist named "Pirates".
4. **Pick highest score; tiebreak by MA's original ordering.** MA's relevance signal is the tiebreaker, not the primary sort.

`AppMediaItem` → `INMediaItemType` mapping (see `mapToINMediaItem`):

| AppMediaItem | INMediaItemType |
|---|---|
| `Track` | `.song` |
| `Album` | `.album` |
| `Artist` | `.artist` |
| `Playlist` | `.playlist` |
| `Audiobook` | `.audioBook` |
| `RadioStation` | `.radioStation` |
| `Podcast` | `.podcastShow` |
| `PodcastEpisode` | `.podcastEpisode` |
| `Genre` | `.musicStation` (no `.genre` exists; "play <genre>" semantically is a station) |
| `RecommendationFolder` | `nil` — filtered out everywhere |

### Disambiguation policy

**`INPlayMediaIntent.resolveMediaItems` never returns `.disambiguation`.** Always `.success(with: bestMatch)` or `.unsupported()`. Returning `.disambiguation` produces an infinite loop: Siri sends each user tap from the disambig list back as a fresh intent with `mediaItems` pre-filled, and any non-`.success` response restarts the cycle. The bestMatch is good enough in practice because of the scoring above.

**`INUpdateMediaAffinityIntent.resolveMediaItems` *does* return `.disambiguation`** (capped at 5 items). It's a one-shot mutation, not a cycle: Siri presents the list, the user taps, the next intent has pre-filled `mediaItems`, the early-return in resolve returns `.success`, done.

### `mediaSearch` extraction (PlayMedia)

`queryString(from:)` builds a search string from the intent's content, in this priority:

1. `intent.mediaSearch?.mediaName` — voice transcript. Concatenated with `artistName` when present (improves match accuracy for "play X by Y" phrasing).
2. `intent.mediaItems[0].title + artist` — falls back when Siri pre-resolved against its own catalog. The identifiers in those items belong to Siri/Apple Music, not MA, so we re-search MA by title/artist and return our items.
3. Returns nil — bail with `.unsupported`.

Empirical observations: for "Death Cab for Cutie", Siri puts the entire phrase in `mediaName` with `albumName`/`artistName` both nil and `mediaType=.unknown`. For "by <artist>" phrasing, `artistName` may or may not populate — verify per-phrase via the explicit `mediaSearch.*` log lines in `resolveMediaItems`.

The Affinity handler builds a richer query (mediaName + artistName + albumName + target.title + target.artist) and prefers `mediaSearch.mediaName` over the cache-hit shortcut, because Siri's disambig may have led the user to tap a wrong-type catalog candidate (e.g. tapping the artist when "I love this song" intended a track).

### Donations

Donations tell Siri "MA is a media destination." Without donations, Siri never lists the app as a candidate for "play X" prompts. They fire from:

- `CarPlayContentManager.playItem()` — every play started from the CarPlay browse grid.
- `SiriIntentHandler.handle(INPlayMediaIntent)` on success — reinforces the model after Siri-driven plays.

Each donation:
- Wraps an `INPlayMediaIntent` describing the played item.
- Uses identifier `play-<serverId>-<itemId>`. Server id (from MA's handshake, exposed as `KmpHelper.getServerId()`) namespaces the server-relative `itemId`. Same server + same item ⇒ same identifier ⇒ donations consolidate. Different servers don't collide.
- Bails with an error log if `getServerId()` is nil (this shouldn't happen — donations only fire after a successful play, which requires a connection).
- Caches the `AppMediaItem` in a class-level `NSCache` (countLimit 100) keyed by `itemId`. A follow-up Siri intent (affinity, "play that") finds it without re-searching.

In-app iOS playback (Compose UI) does **not** donate today — only CarPlay and Siri-initiated plays do. Adding in-app donation would require distinguishing user-initiated plays from queue auto-advance (auto-advance must not donate, or Siri's prediction model gets noise).

### Favorites

`KmpHelper.setFavorite(item, favorite)`:
- `favorite=true` → `addFavorite(uri)`. Falls back to `mediaUri` if `uri` is nil (e.g. Genre, which synthesizes a URI).
- `favorite=false` → `removeFavorite(itemId, mediaType)`. Server-side no-op if not currently favorited.

Returns `Boolean` synchronously: `true` if the request was dispatched, `false` if it couldn't be formed (only known case: add for an item with neither `uri` nor `mediaUri`). Network outcome is fire-and-forget. Swift's affinity handler propagates `false` as `INUpdateMediaAffinityIntentResponseCode.failure` so the user hears Siri's failure tone instead of a false "Done."

---

## Files

| File | Purpose |
|---|---|
| `iosApp/iosApp/CarPlay/CarPlaySceneDelegate.swift` | Scene delegate, template creation, navigation. |
| `iosApp/iosApp/CarPlay/CarPlayContentManager.swift` | Data fetching bridge, maps `AppMediaItem` → `CPListItem`, image loading, donates plays. |
| `iosApp/iosApp/CarPlay/SiriIntentHandler.swift` | All three media-domain intents (`INPlayMediaIntentHandling`, `INUpdateMediaAffinityIntentHandling`, `INSearchForMediaIntentHandling`). Static `NSCache<NSString, AppMediaItem>` (100 items). Exposes `static donatePlayed(_:)`. |
| `iosApp/iosApp/iOSApp.swift` | `AppDelegate` for CarPlay scene routing + Siri intent routing (`application(_:handlerFor:)`). Calls `bootstrapKmp()` from `iOSApp.init()` before any scene connects. Owns `KmpState.isReady`. |
| `iosApp/iosApp/NowPlayingManager.swift` | Control Center / lock screen / Now Playing artwork via `MPNowPlayingInfoCenter`. |
| `iosApp/iosApp/Info.plist` | Scene config + the SiriKit `INIntentsSupported` / `INSupportedMediaCategories` / `NSUserActivityTypes` keys. |
| `iosApp/iosApp/CarPlay.entitlements` | `com.apple.developer.carplay-audio`. |
| `composeApp/src/iosMain/.../KmpHelper.kt` | iOS-only KMP bridge: fetch, search, play, setFavorite, getServerId, getServerUrl. |
| `composeApp/src/iosMain/.../MainViewController.kt` | Hosts `bootstrapKmp()` (idempotent KMP/Koin init) and `MainViewController()` for the SwiftUI Compose bridge. |

## KmpHelper bridge methods

iOS-only Swift-callable API in `KmpHelper.kt`:

| Method | MA Request / behavior |
|---|---|
| `fetchArtists / Albums / Tracks / Playlists / Audiobooks / Podcasts / RadioStations` | `Request.<Type>.listLibrary()` |
| `fetchRecommendations` | `Request.Library.recommendations()` |
| `fetchRecommendationFolders` | Same, filtered to `AppMediaItem.RecommendationFolder` |
| `search(query)` | 6 media types (artist/album/track/playlist/audiobook/radio), `limit=10`, `libraryOnly=false` |
| `playMediaItem(item)` | Selected player + `QueueOption.PLAY` |
| `setFavorite(item, favorite) -> Boolean` | `addFavorite(uri\|mediaUri)` or `removeFavorite(id, mediaType)`. False if no URI for add. |
| `getServerUrl() -> String?` | The connected server's base URL |
| `getServerId() -> String?` | The connected server's stable UUID (from `ServerInfo.server_id` in the handshake) |

All completion handlers fire on `Dispatchers.Main` (= iOS main thread on Kotlin/Native).

---

## Adding things

**A new browse category:**
1. Add `fetch<Foo>()` to `KmpHelper.kt` (iosMain).
2. Add the corresponding method to `CarPlayContentManager.swift`.
3. Add a button + handler in `CarPlaySceneDelegate.swift`'s grid.

**A new Siri intent:**
1. Add the protocol conformance to `SiriIntentHandler` (extension).
2. Implement at minimum `handle`; add `resolveMediaItems` and any `resolve<Param>` if the intent has parameters worth resolving.
3. Add the intent class name to `INIntentsSupported` and (if you'll donate) `NSUserActivityTypes` in `Info.plist`.
4. Update `application(_:handlerFor:)` in `iOSApp.swift` to return the handler for the new intent class.
5. If matching MA items, route through `bestMatch(in:for:query:)` for consistency with the existing handlers.

**A new `AppMediaItem` subtype** (added in commonMain): update `mapToINMediaItem` and `bestMatch`'s type-filter switch in `SiriIntentHandler.swift`. Both fail closed (return nil / fall through to all candidates) on unmapped types, but the prediction model gets cleaner if the new type is taught explicitly.

---

## Debugging

Log subsystem is the bundle id (`io.music-assistant.client`), category is `Siri`. To stream Siri logs from Console.app or `log show`:
```
log stream --predicate 'subsystem == "io.music-assistant.client" AND category == "Siri"'
```

Verbose entry-point logs in `PlayMedia.resolveMediaItems` and `Affinity.handle` dump every field of `mediaSearch` plus the raw intent — essential when "why did Siri pick that?" comes up. They're at `.info` level today; see the TODO about demoting once stable.

Common failure modes:
- "Sorry, Music Assistant hasn't added support for that with Siri" → `INSupportedMediaCategories` missing; run the `assistant_service` diagnostic flow above.
- `IntentsErrorDomain Code=1901` on donate → `NSUserActivityTypes` missing.
- Intent never reaches `application(_:handlerFor:)` → the app isn't in `assistant_service`'s `installedApps filtered for intent:...` list. Re-check Info.plist and the SiriKit capability in Signing & Capabilities (capability declares which media types we handle).
- `bestMatch` picks the wrong item → check the `bestMatch:` log line for the score; the query → token decomposition is in `tokens()`. Likely candidates: missing diacritic in MA's index, or the score is being diluted by extra query tokens.

---

## Status (2026-04-28)

| Path | State |
|---|---|
| CarPlay browse + play (simulator) | ✅ |
| CarPlay browse + play (in-car) | Untested |
| `INPlayMediaIntent` from "Hey Siri" on iPhone | ✅ verified for tracks, albums, artists |
| `INUpdateMediaAffinityIntent` ("I love this song") | ⚠️ Server-side favorite applies correctly. Siri's own NLG fails with `SiriAudioAffinityScorer Got unexpected parse` because Siri can't ground the spoken confirmation against an unknown item — renders "something went wrong" UI. **Blocked on Spotlight indexing.** |
| `INSearchForMediaIntent` | Untested |
| Siri-initiated play in CarPlay (steering wheel) | Untested |

Verified phrases: *"play Death Cab for Cutie in Music Assistant"*, *"play Pirate's Gospel by Alela Diane in Music Assistant"* (album resolved via similarity scoring over MA's first result, which was an unrelated artist).

---

## TODO

### Verification follow-ups
- [ ] Verify on a physical device with cold "Hey Siri" trigger. Run `codesign -d --entitlements - <path-to-.app>` after a device build and confirm `<key>com.apple.developer.siri</key><true/>` survived signing. The App ID's SiriKit capability is on (verified 2026-04-28); this just guards against profile drift.
- [ ] Test `INSearchForMediaIntent` end-to-end: "Hey Siri, search for X in Music Assistant". Should display matched items without auto-playing.
- [ ] CarPlay in-car testing: tap-to-play (donation path), steering-wheel Siri ("play X"), affinity if reachable from the wheel.
- [ ] Demote diagnostic logs once stable — the `String(describing: intent)` dump and per-field `mediaSearch.*` lines are essential for debugging but noisy in production. `.debug` level or remove once the integration has been used in the wild for a release.

### Known improvements ([Apple's "Improving Siri Media Interactions and App Selection"](https://developer.apple.com/documentation/sirikit/improving-siri-media-interactions-and-app-selection))

- [ ] **Tier 1: `INMediaUserContext.becomeCurrent()` at app launch.** Per Apple: *"When your app launches, create an INMediaUserContext, add the user's information, and make the context current. The system uses this information to optimize the user experience for App Selection."* Set `numberOfLibraryItems` from MA totals and `subscriptionStatus = .subscribed`. Hook into `iOSApp.init()` after `bootstrapKmp()`. ~5 lines. Plausibly relevant to the now-playing-client identification quirk we saw with `assistantd`. Quick win.

- [ ] **Tier 1: `AppIntentVocabulary.plist`.** Declares vocabulary unique to MA but relevant for all users (app name, "MA" abbreviation). Static plist; no code. Helps Siri ASR recognize app-specific terms.

- [ ] **Tier 2: Core Spotlight indexing of MA library items.** This is the **dispositive fix** for two issues:
  1. Siri's affinity disambig list never contains the actual MA tracks the user wants — Siri builds it from its own catalog plus our Spotlight contributions (currently empty).
  2. `SiriAudioAffinityScorer Got unexpected parse` — Siri's response NLG can't ground the spoken confirmation against unknown items.

  Implementation: a `SpotlightIndexer.swift` that subscribes to MA library updates and writes `CSSearchableItem` entries with `completeUntilFirstUserAuthentication` data protection level so Siri can read them when the app isn't running. `CSSearchableItemAttributeSet(contentType: .audio)` with `title`, `artist`, `album`, `genre`, `playCount`, `lastUsedDate`. Probably its own PR; medium-large given the sync surface.

- [ ] **Tier 3: `INVocabulary` for user-specific names.** Programmatically register playlist titles, frequently-played artists, etc., so Siri's ASR/NLU recognize names that are unique or have unusual spellings. Direct fix for misrecognitions like "Mortaja" we hit during testing. Should be done alongside Tier 2 since both feed Siri's catalog.

- [ ] **Tier 3: `.intentDefinition` file with `mediaContainer`-only PlayMedia.** Required to opt into personalized audio suggestions on Lock Screen, Control Center, and Home app. Declarative; no code. Lower priority.

### Other
- [ ] (Optional) Donate from the in-app iOS playback path. Today, donations only fire from CarPlay and Siri-initiated plays — a user picking a track in the iPhone Compose UI doesn't donate, so Siri only learns from CarPlay/Siri use. Adding an in-app donation requires a Swift-callable hook from the KMP playback layer to flag user-initiated plays vs. queue auto-advance (auto-advance must **not** donate, or the prediction model gets noise). Defer until we see whether predictions are sharp enough from CarPlay/Siri donations alone.
