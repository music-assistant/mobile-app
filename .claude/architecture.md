# Architecture Patterns

## MVVM + Unidirectional Data Flow

```
User Action → ViewModel → Update State → UI Recomposition
                ↓
         Repository/DataSource
                ↓
           Network/Storage
```

- ViewModels expose `StateFlow<T>` for reactive UI state
- UI collects state with `collectAsStateWithLifecycle()`
- User actions invoke ViewModel methods
- Wrap async results in `DataState<T>` (Loading/Data/Error/NoData)

## Dependency Injection (Koin)

```kotlin
// Module definition
val appModule = module {
    singleOf(::Repository)
    viewModelOf(::FeatureViewModel)
}

// Usage in Composable
val viewModel = koinViewModel<FeatureViewModel>()
```

## Navigation (Navigation3)

- Type-safe routes via `@Serializable` data classes/objects
- Sealed interface for destination grouping
- Modal sheets for overlays

```kotlin
@Serializable sealed interface NavScreen {
    @Serializable data object Home : NavScreen
    @Serializable data class Detail(val id: String) : NavScreen
}
```

## Expect/Actual Pattern

Use sparingly. Most code stays in `commonMain`.

```kotlin
// commonMain
expect class PlatformFeature {
    fun doThing()
}

// androidMain
actual class PlatformFeature {
    actual fun doThing() { /* Android impl */ }
}
```

## Data Layer

- **Repository**: Single source of truth, exposes StateFlows
- **DataSource**: Network/local data access
- **Models**: Server DTOs in `model/server/`, domain models in `model/client/`
- **List payloads**: `library_items` and `player_queues/items` answer in one message and the
  server caps a message at about 9 MB. Never send a huge `limit`. Page with `Request.fetchAllPages`
  (`api/Paging.kt`) when a caller needs the whole list. `playlist_tracks` and `podcast_episodes`
  depend on the server version. Server 2.10 streams the whole list in 500-item `partial` batches
  that `RpcEngine` reassembles, and it ignores `limit` and `offset`. Server 2.11 answers one
  500-item page. `MediaItemRepository.fetchAllMediaItems` selects the method from the schema
  (`SERVER_2_11_SCHEMA`), so load these two lists through it. `library_items` already
  returns slim summary items by default.
- **Collections**: an audiobook series is `MediaType.COLLECTION` (`MediaCollection`, books in `items`). The
  server returns series rows only when `library_items` gets `collapse_collections`. The Audiobooks top bar sets
  it with the `SeriesGrouping` toggle, which is a display setting and not a filter. `music/audiobooks/get_collection`
  loads one series. Home rows from Audiobookshelf can also contain series. Play the series `uri` and the
  server starts the first book that is not fully played. Android Auto and CarPlay show the flat book list.
- **Input sources**: a live input from a server plugin is `MediaType.AUDIO_SOURCE` (`AudioSource`). The server
  puts the sources that a client can start at the `music/browse` root, from server 2.9.0. Play the source `uri`
  with `play_media`. A source never ends, so the long-press menu has "Play now" only. A source has no favorite
  state. Native player inputs (`source_list`, `players/cmd/select_source`) are a different feature, and the
  client does not show them.
- **Ambient sound overlay**: the server mixes a looping `sound_effect` item into the queue stream, so all
  players on the stream hear it. `QueueOverlayRepository` (`data/repository/`) sends `player_queues/overlay`
  and loads the sounds with `music/sound_effects`. The command goes to the server for every player and is never
  a `PlayerAction`, so the local player does not intercept it. A null `QueueInfo.overlay` means that the server
  has no overlay support. Show the feature only if a provider has the `sound_effects` feature. To turn the overlay
  off, send `enabled=false` only, because the server keeps the last sound and volume.
- **Play requests**: send every `player_queues/play_media` through `PlayDispatcher` (`data/play/`). Do not send it
  from a screen or from a car surface directly. The dispatcher runs each request in an app scope, because the server
  answers only after playback starts, and that can take tens of seconds. It sends one request for each queue at a
  time. It drops a double tap, and a new "play" or "replace" request replaces a waiting one (`coalesce`).
  `busyQueueIds` gives the queues that have a request in progress or waiting. `PlayerData.playRequestPending`
  joins it with the server flag `extra_attributes.play_action_in_progress` (any client), and `PlayerControls`
  shows a ring around the main button while it is true. Read the server flag from the server queues, because the
  local player's queue info comes from `LocalPlayerAdapter`.
- **Play modes**: a menu entry or a tap gives a `PlayMode` (`data/play/`), and the screen makes the `PlayRequest`
  from it. `ItemAction.toPlayMode` is the only map from a menu entry to a mode. The queue options are `play`,
  `replace`, `next`, `add` and `replace_next` (insert after the current item and drop the rest; every server 2.x
  has it). `shuffle=true` ("Play shuffled") and `start_from_beginning` ("Play from beginning") are optional flags
  of `play_media`. An older server ignores an unknown argument, so `PlayMediaSupport` (`data/model/server/`)
  gates each flag on its first schema, 52 and 40, and `LocalPlayMediaSupport` carries it to the menus and to
  `ClickActionConfig`. Offer "Play shuffled" for the containers in `canPlayShuffled` only. Offer "Play from
  beginning" for a podcast episode with progress only, because the server resets the resume point of an episode
  and not of an audiobook. "Play from here to latest" is `PlayFromHere` in `ClickContext.PODCAST`, the episodes
  tab of a podcast. The car bulk action `PLAY_SHUFFLED` has no schema gate, so a server before the flag plays
  the container in order.
- **Favorites**: from server 2.11 (`sendsFavoriteUpdates`), a favorite belongs to one user. The server
  sends null `favorite` in every `media_item_*` event, and sends the state of the user in `favorite_updated`.
  That event has no item body, so `MediaItemRepository` fetches the item and emits
  `MediaItemChange.FavoriteUpdated` with the state of the event. An `Added` or `Updated` change with
  `carriesFavorite` false must not change the heart of a row. Replace a row with
  `item.withFavorite(change.favoriteAfter(row.favorite))`. `MainDataSource` gets the added and updated
  tracks from `itemChanges` for the same reason.
- **Dislike**: from server 2.11 (`supportsDislike`), a false `favorite` is a dislike and null is no choice.
  On an older server, false is "not a favorite". Get the favorite menu entries from `favoriteActions`.
  Each entry is an `ItemAction.SetFavorite` and holds the state that it sets. The phone, Android Auto and Siri
  send each favorite write through `MainDataSource.setFavorite`. Compose reads `LocalDislikeSupported`,
  which `App` provides.
- **Scopes**: `MainDataSource.grantedScopes` (`GrantedScopes`) holds the scopes of the signed-in user. The
  app reads `auth/scopes` once for each new session and finds the role of the user in the table. Gate a
  write action on a scope. Do not gate it on the role name, because server 2.11 has custom roles.
  The value is `NONE` before the table arrives and after a failed fetch, so the gate fails closed.
  Compose reads `LocalGrantedScopes`, which `App` provides. `canEditLibrary` (`library.write`) gates
  favorites, library membership, playlist edits and the played state. `canConfigurePlayers`
  (`config.players.write`) gates DSP and the car DSP action. Every role has `players.control`, so
  grouping has no gate.
- **Refresh item**: `music/refresh_item` loads an item again from its music services. If no service has the
  item, the server searches for a substitute. The item details menu shows "Refresh item" or "Find on your
  music services" (`RefreshItemAction`). The command needs `library.manage` before server 2.11 and
  `library.write` from 2.11 (`MainDataSource.refreshItemAccess`). Like the web frontend, "Refresh item"
  also needs `library.manage`.

  An item is unavailable if no mapping is available on an available provider instance. The entry stays
  hidden until `ProviderRepository` loads the providers.

  Send the command through `ItemRefresher` (`data/repository/`). For a library item, the server answers
  only after it matches the providers and reloads the metadata. That can take minutes, and the server
  reports no progress. So the refresher runs the command in an app scope and keeps the uri in
  `refreshingUris` until the answer. It ignores a second refresh of that uri. The page shows an
  indeterminate progress indicator while its uri is in the set, also after the user opens the page again.
  The refresher publishes the answer as a local `MediaItemChange.Updated` with `carriesFavorite` false,
  because a refresh does not change the favorite. Then it emits `refreshed`. The page replaces its item
  from `refreshed` by uri, because a substitute has no mapping in common with the unavailable item.
  The command changes the listings of the item, so the sub-items load again.
- **Item metadata**: the item details page shows the long text of the item under the header, like the
  web frontend: the biography of an artist (`metadata.description`), the review of an album
  (`metadata.review`, or its description), and the description of a playlist, a podcast or an
  audiobook. `AppMediaItem.itemDescription()` (`model/client/ItemDescription.kt`) selects the text
  and `toPlainItemText` makes plain text from the Markdown and HTML that a provider sends. The page
  clamps it to 3 lines, and "More" opens a sheet. The facts line shows the album type, the track
  count and the total time (`PlayableSummary`, a client sum of the loaded tracks, none for a dynamic
  playlist), the publisher of an audiobook, and the music services of a library item. The genre chips
  come from `music/genres/genres_for_media_item`, which maps genres to library items only, so an
  item of a music service gets none. A chip opens the genre page. A playlist shows its `owner` name.
  From server 2.11 (`supportsPlaylistAccess`) a Music Assistant playlist shows its access record
  instead (`PlaylistAccess`, `ownerLine`): "Personal" or the owner, who sees it, and "Collaborative".
  The name of another owner comes from `config/providers/share_candidates`, which needs
  `library.write`. The car surfaces show none of this.
- **Library sort**: get the sort choices of a library list from `LibrarySortRepository.menu(mediaType)`, on the
  phone and in Android Auto. From server 2.11 (`sortWireFor`), the menu holds the fields of `music/sort_options`
  and the request sends `sort_field` and `sort_direction`. An older server gets the static `SortConfig` table and
  `order_by`. The `SortConfig` default stays the list default while the server offers its field, so podcasts
  stay newest first. If the server does not offer it, the first server option is the default. A 2.11 server
  rejects a field that the list does not offer, so resolve the stored choice with
  `LibrarySortMenu.resolve` before the request. Send a sort only as a `ListSort`. The sub-item lists (album
  tracks, playlist tracks, episodes, artist lists) keep `SortConfig` and sort on the client.
- **Library filters**: `LibraryFilters` (`data/model/client/`) is the filter set of one library list. The
  settings store one instance for each media type, and the list ViewModel reads it from the settings flow.
  Each field is one `library_items` argument that the server applies. Do not add a field for an argument
  that the server drops into `**kwargs`. `audiobookArtistType` makes the Audiobooks list show the library
  authors or narrators through `music/artists/library_items` with `artist_type`, and the ViewModel then
  uses the Artists sort menu and the `ARTIST` row type (`State.listedMediaType`). The sheet offers the
  row only for the types that `music/artists/library_artist_types` returns. An author or narrator row
  opens the artist page. `Artist.isAudiobookArtist` (from `artist_type`) makes `ArtistDetailsViewModel`
  load `music/artists/artist_audiobooks` into the "In library" row (`in_library_only`) and the "All
  audiobooks" row, one option for each provider instance, and it leaves the top tracks row empty. The
  request sends `artist_type`, because the server needs it for a provider artist. `genreContentType` is the
  `content_type` taxonomy of the Genres list. `genreMediaType` refines the music taxonomy only: the sheet
  shows it under the music content type, and `genreMediaTypeFilter` gives null for every other content
  type, so a stored choice does not narrow the podcast or audiobook genres. The episodes tab of a podcast has its own played filter
  (`PlayedFilter`, one setting for all podcasts). The app sends `fully_played=false` and filters the
  answer as well, because a 2.10 server ignores the argument.
- **Providers**: `ProviderRepository` (`data/repository/`) owns the `providers` list. Read
  `providers` from it. Do not send `Request.Provider.all()` from a screen. It loads the list once
  for each session and again on each `providers_updated`. It does not use the event payload,
  because server 2.10 sends the list without the provider filter of the user.
- **User preferences**: `UserPreferences` (`data/`) owns the server preferences of the signed-in user.
  The web frontend uses the same object. `auth/user/update` replaces the whole object, and the server
  sends no change event. So write one key with `UserPreferences.update`. It gets the latest object from
  `auth/me`, changes the key, and sends the whole object. One mutex orders the reads and the writes. If
  the read fails, nothing is written. A change from another client between the read and the write is
  lost. Recent searches (`search.recent`, 5 terms) and search sources (`search.sources`) use this path.
- **Search sources**: from schema 63 (`supportsSearchSources`, server 2.10.0), `music/search` takes
  `providers`, and the filter sheet shows a source picker instead of the "In library only" switch.
  `SearchSources` (`ui/compose/search/`) makes the choices like the web frontend: "library", one
  target for each streaming service (its domain), and one target for each other provider instance.
  An empty choice searches everything. A genre-only search sends `library` only. The choice is saved
  before the search starts, and the search reads the sources when it starts, not from the screen state.
- **Play history**: the server sends `playlog_updated` to all users. Ignore an event with the
  `userid` of a different user. A null `userid` applies to all users. The home screen loads its
  rows again 1.5 s after the last event, like the web app.
- **Episode numbers**: an episode subtitle starts with "S2 E5", or with "E5" if the episode has no
  season. `EpisodeNumberFormat` (`model/client/items/`) holds the templates, and `subtitleWith` makes
  the subtitle. The phone gets the templates in `localizedSubtitle`. Android Auto and CarPlay load
  them once (`AutoLibrary`, `MediaSessionStrings`, `CarPlayStrings`), because their item builders are
  synchronous. Do not format the label in a different place.
- **Episode page**: `ItemDetailsScreen` opens a `PodcastEpisode` (`music/podcasts/podcast_episode`, every server 2.x).
  The header shows the podcast name, which opens the podcast, the played state, and the playing time. The
  "Other episodes" tab (`ItemDetailsTab.OTHER_EPISODES`) lists the podcast episodes without the open one, with
  the sort of the podcast page. The menus reach the page with "Go to episode" (`navigationOptions`). The page
  shows the `description` as "About this episode". From schema 81 (`supportsEpisodeTranscript`, server 2.11),
  `TranscriptRepository` (`data/repository/`) loads `music/podcasts/podcast_episode_transcript` once for each
  episode in an app scope and keeps the last answers, because the page and the player ask for the same
  episode. The answer is a `[text, cues]` tuple, and `transcriptOf` (`model/client/Transcript.kt`) makes a
  `Lyrics` from it, so `LyricsSheet` shows a transcript with the lyrics timing and offset. The page keeps it
  in `State.lyrics` and offers "Transcript" in the menu once it is loaded. The expanded player loads the
  transcript of the current episode and shows it behind the lyrics button. An episode whose provider reports
  `has_transcript` false is not asked. The page has no steppers and no hide-fully-played filter.
- **Queue origin**: `QueueTrack.origin` is the container that the item was played from, from server
  2.11. It is null on an older server, for a single track and for a live stream. The stream details
  dialog shows it. It opens the container only if `opensItemDetails` is true. Add a type to
  `opensItemDetails` when `ItemDetailsViewModel.getItemById` loads it.
- **In-list filter**: `List.clientFiltered(query)` (`model/client/QueryFilter.kt`) filters loaded
  items on the client. Derive the visible list as raw → filter → sort in one place.
- **Announcements**: `AnnouncementRepository` (`data/announcement/`) runs both kinds in an app scope,
  because the server answers only after playback. Text uses `players/cmd/play_announcement` with
  `message`. Voice has no upload endpoint: raw s16le PCM streams live over `/live_announcement`
  (WebSocket) or the `live_announcement` data channel (WebRTC). Closing the link ends the clip, so
  never cancel a session early. The platform microphone is the `MicrophoneCapture` Koin binding.
- **AI Radio**: The client supports server schema 88 and higher only (`supportsAiRadio`). Both gates,
  `MainDataSource.aiRadioAvailable` and `aiRadioQueueDjAvailable`, need it. A station is a finite
  Radio item (`ai_radio://radio/<station_id>`, `ServerAiRadioStation.uri`). Play it through
  `PlayDispatcher` like any radio. There is no start, stop or status command. The server also mirrors
  each show into the library as a Radio row. The Radio lists hide these rows (`isAiRadioShow`),
  because the AI Radio section lists them. The `ai_radio` plugin
  sends `provider_event` refetch hints (`object_id` `ai_radio`). The hints have no state, so re-read
  the endpoint that the hint names. `AiRadioRepository.changes` maps the hints to `AiRadioChange`.
  It also sends `RESYNC` when the command session comes back, because the server does not send
  again the hints that the client lost. The queue DJ menu (`QueueDjViewModel`) and the stations
  screen show their cache and use only these changes. A show on a queue does not lock the queue DJ.

## HTTP Clients

- Get every `HttpClient` from the Koin `HttpClientFactory`. Do not call `HttpClient(engine)` directly.
- Android (`AndroidHttpClientFactory`): OkHttp with the KeyChain client certificate that the user selected
  (`SettingsRepository.clientCertificateAlias`), for mTLS. Trust stays the platform default, so the network
  security config applies. All clients share one connection pool. A certificate change evicts that pool.
- iOS (`IosHttpClientFactory`): Darwin. `handleChallenge` answers a client-certificate challenge with the identity
  in `KeychainClientIdentity`, only while `clientCertificateAlias` is set. The Darwin engine owns its session pool,
  so `KtorServiceClient` makes a new client when the certificate changes.

## Artwork Loading

- **`ArtworkRepository`**: Single owner of artwork fetching, disk caching, freshness, and concurrent-request deduplication. Shared by all platforms.
- **Compose and Android media**: Use the singleton Coil loader. Its artwork adapter resolves through the repository; Coil owns decoding and decoded-memory caching, not a second disk cache.
- **CarPlay and iOS Now Playing**: Use `KmpHelper.loadArtwork` through the shared `NativeArtworkLoader` adapter. Kotlin owns one cancellable coroutine covering fresh-token lookup and body loading; it accepts a synchronous Swift cache probe after token resolution, so a versioned native decoded-image hit skips the body entirely. Swift owns the bounded shared `NSCache` (including decode and presentation); only reusable repository results are inserted under the token identity-plus-digest key.
- **WebRTC**: Uses the same repository through the existing HTTP proxy. Cache identities include the server ID so synthetic URLs cannot collide across servers.
- **Cache identity**: Decoded-image keys include the content version. Decode-failure eviction targets only that version, never a newer replacement.

**Key rule**: Route new artwork consumers through the shared loader or bridge. Do not add independent downloads, disk caches, or URL-only caches that bypass repository freshness.

## Compose Guidelines

- Material3 components
- Extract reusable composables to `ui/common/composables/`
- Use `remember`/`derivedStateOf` for computed values
- Split large composables into smaller files by meaning
- Previews only when explicitly requested

## State Management

```kotlin
// ViewModel
class FeatureViewModel : ViewModel() {
    private val _state = MutableStateFlow(FeatureState())
    val state: StateFlow<FeatureState> = _state.asStateFlow()
    
    fun onAction(action: Action) {
        // Update state
    }
}

// Composable
@Composable
fun FeatureScreen(viewModel: FeatureViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    // Render UI
}
```

## Error Handling

- Use sealed class/interface for result types
- Display errors via Toast or inline error states
- Log with context: `Logger.withTag("Component").e { "message" }`

## Localization

- Edit only the English file, `composeResources/values/strings.xml`. Lokalise syncs the `values-*` locale files.
- Do not edit a `values-*` file. If you remove a key from the English file, Lokalise removes it from the locale files.
- To change the meaning of a string, add a new key and remove the old key. Translators see a new key, but they do not see a changed text.

## UI Architecture

**IMPORTANT**: The project is transitioning from the old MainScreen/MainViewModel to the new HomeScreen/HomeScreenViewModel architecture.

- **Deprecated (do NOT use)**: `MainScreen.kt`, `MainViewModel.kt`
- **Current (use these)**: `HomeScreen.kt`, `HomeScreenViewModel.kt`

When implementing new features or integrations:
- Use `HomeScreenViewModel` as reference for architecture patterns
- Add dependencies to `HomeScreenViewModel` in `SharedModule.kt`
- Do NOT modify `MainViewModel` - it's legacy code being phased out

### Sendspin Integration

The built-in player is the `:sendspin-kmp` Gradle module (`io.music_assistant.sendspin`), published as `io.github.music-assistant:sendspin-kmp` (see `sendspin-kmp/README.md`). The module is pure Kotlin Multiplatform. It has no Compose and no Koin dependency, and it is fully unit-tested with fakes.

**Public surface** (package `api`):
- `SendspinPlayer(config: StateFlow<LocalPlayerConfig?>, deps, scope)` creates the player. A `null` config disables it. There is no start or stop call.
- `state: StateFlow<PlayerState>` is `Disabled`, `Connecting`, `Connected`, `Reconnecting`, or `Failed`. `Connected` carries the player id, the server name, the clock quality, and the audio status.
- `events: Flow<PlayerEvent>` carries `PlaybackStarted`, `PlaybackStopped(cause)`, `ServerRefreshNeeded`, `FocusRegained`, and `Warning(code)`.
- `hold(held)` is the local pause. While held, the player is silent and discards the audio that it gets, with no server round trip. `LocalPlayerAdapter` holds on every local Pause. It releases on every play intent: a local Play, Next, Previous or seek, a `PlayDispatcher` request or a queue-item tap for the local player. It also releases when the server reports that the player is not playing. A `stream/start` while held gives `StreamStartedWhileHeld`, and the adapter sends the pause again. A Play that releases a hold sends a pause before the play, so the server starts a new stream. `LocalCommandQueue.sendOrDrop` sends Play, Next, Previous and seeks. It waits up to 10 s for the session and for the queued commands, then it sends the command or drops it. It never queues them. A `Starved` stop (the buffer ran dry while the audio link was down) is the same local pause, and the player never resumes on its own. The adapter reports `wantsSession` while the player plays or a hold waits for the server, so `MainDataSource` keeps the MA session alive: it stays `Reconnecting` instead of `Disconnected.Backgrounded` until the server confirms the pause.
- Ports the app implements: `AudioSink`, `DecoderFactory`, `SendspinKeyStore`, and `Endpoint.WebRtc.openChannel`. On Android the module has the sink, the decoders, the audio dispatcher (`sendspinAudioDispatcher`), the clock (`ElapsedRealtimeClock`) and the online flow (`connectivityOnline`), in package `io.music_assistant.sendspin.android`. That package uses `api` only. The app keeps `SettingsSendspinKeyStore`, because a different store would lose the existing player identity.
- The app depends on `sendspin.api` and the root factory only. Every other declaration in the module is `internal`, so the compiler rejects a new leak. The module compiles in explicit API mode. `sendspin-kmp/api/sendspin-kmp.klib.api` is the committed public API, checked by `checkKotlinAbi`; after an intended API change run `./gradlew :sendspin-kmp:updateKotlinAbi` (macOS). The Noise primitives are a private default of the composition root, not an app port.

**Internal layout**: `wire` (one parse per message), `transport` (one connection, no reconnect), `session` (Noise session on the caller's coroutine), `connection` (the single reconnect policy and the liveness watchdog), `clock` (the spec time filter over probe bursts), `audio` (byte-capped jitter buffer, scheduler, drift corrector), `player` (composition root). The packages `noise`, `identity`, `pairing`, and `management` are unchanged from the previous implementation.

**App side** (`composeApp`):
- `data/LocalPlayerAdapter` owns the player and everything about the MA player model: optimistic UI, the offline command queue, and server event reconciliation. `LocalPlayerFactory` builds the player from the platform ports (sink, decoders, key store), so a test gives the adapter a fake player (`LocalPlayerAdapterTest`).
- `data/LocalPlayerEndpoints` derives the endpoint from the MA session. A WebRTC session gets the data channel. A direct session gets the proxied WebSocket at `<wsUrl>/sendspin`, or the custom Sendspin server from settings. A reconnecting MA session keeps the last endpoint. Each Sendspin connection attempt needs a new data channel. From server schema 49 (`LABEL_ROUTED_CHANNELS_SCHEMA`), the app opens it in-band on the live peer, so the MA session continues. On an older server, or if the new channel does not open, the app renegotiates the peer connection.
- Sinks and decoders: Android uses `AudioTrackSink` and `AndroidDecoderFactory` from the module. On iOS, `player/local/` holds `AudioQueueSink` and pass-through `IosDecoderFactory` (the native player decodes).
- The control plane stays on the MA REST API. Sendspin carries audio only (role `player@v1`).
- Only the Noise-encrypted protocol is supported. `supportsEncryptedSendspin(schemaVersion)` requires MA schema 45 or newer. A known lower schema produces `Endpoint.Unsupported`, and the player fails without opening a transport. A missing schema is inconclusive: endpoint selection permits a handshake rather than rejecting the server. An explicit unsupported wire version, and a `server/error` for an unsupported version or suite, fail terminally; network failures and handshake timeouts still retry.
- The module speaks two wire dialects, `WireVersion` (`api`). `LEGACY_9` is aiosendspin 9.x (server 2.10.6 and older). `SPEC_1_0` is Sendspin spec 1.0.0-rc1, aiosendspin 10.x (server 2.10.7 and 2.11). `sendspinWireFor(schemaVersion, serverVersion)` selects `SPEC_1_0` from `SERVER_2_11_SCHEMA`, or from a 2.10 `server_version` with patch 7 or higher, because 2.10.7 keeps the 2.10 schema. All other servers get `LEGACY_9`, which includes an unknown server and a source build (`0.0.0`). The handshake does not give the server version: `server/init` has `version` 1 on both wires. `LocalPlayerEndpoints` puts the wire on the `Endpoint`, so a wire change restarts the connection. A 10.x server accepts the 9.x wire only while its hidden setting `allow_legacy_clients` is on, and it logs a "non-compliant client" warning for it. The differences on `SPEC_1_0`: Noise message 1 carries `psk_category`, and the client matches the PSK by id and category. On a miss in the initial handshake, the client completes with the Sentinel PSK, so the server can offer re-pairing. `client/hello` has no `trust_level`, no `supported_commands`, and `supported_pair_methods` is an object keyed by method. `client/state` carries `output_delay_ms`, `required_lead_time_ms`, `min_buffer_ms` and `supported_commands`. The player sends it after the clock converges and again when the delay changes, never periodically. An audio chunk header has 4 more bytes, `send_ahead`, which the client skips. Fragments use type 1 with a flags byte. A re-handshake has no hello exchange, and an activate without `active_roles` keeps the roles. The Pairing PSK flow sends `client/pair-init` with the pairing index before `client/pair-finalize`. `SendspinJson` omits null fields on both wires. `SendspinDeps.deviceInfo` (`DeviceInfo`) gives the product name, the manufacturer and the app version that the server names in its logs.
- Spec 1.0.0-rc1 rules that the module obeys on both wires: the `client/hello` name and each `device_info` string are cut to 64 bytes of UTF-8 (`truncateUtf8`). `advertisedFormats` (`api/AudioFormatSpec.kt`) makes the supported formats, all stereo at 48 kHz: the preferred codec at `LocalPlayerConfig.bitDepth` (24-bit, then 16-bit, for FLAC and PCM), then 16-bit PCM, because every server must produce 16-bit PCM. Opus is always 16-bit.
- Buffer: `LocalPlayerConfig.bufferSeconds` sets the buffer in seconds. `buffer_capacity` and the local byte cap are that many seconds of the heaviest advertised format (`estimatedBytesPerSecond`), so a fallback format gets the same time. The estimate counts FLAC as raw PCM, its worst case, and Opus as 16 kB/s. The settings screen shows the same estimate for the preferred codec. The server reads `buffer_capacity` only from `client/hello`, so a buffer change restarts the connection, and the local cap changes when a connection starts. A local cap below the advertised capacity evicts the future chunks that the server sends, and each evicted chunk is a gap in the audio. The `Stream stats` line counts these evictions.
- 24-bit audio: the user sets it in the settings, and it applies to FLAC and PCM only (`SendspinPlayerSettings.effectiveBitDepth`). `supportsHiResAudio` gates it: Android 12 (API 31) or newer, and all iOS versions. The default is 16-bit. The client advertises 16-bit and 24-bit only, and `AudioFormatSpec` accepts no other depth. 24-bit audio already exceeds real DACs, and the Android mixer drops more bits. `DriftCorrector` resamples 16-bit and 24-bit packed PCM. On Android, `FlacDecoder` asks MediaCodec for float output on a 24-bit stream and converts it to 24-bit packed (`floatToPcm24`). The stock decoder makes only 16-bit or float, and the framework pads 16-bit output for a 24-bit request.
- Format change: the server builds the audio chain and the quality badge when a stream starts, and a reconnect starts no stream. So after a codec or bit-depth change, a playing local player pauses and plays when the new connection is active (`LocalPlayerAdapter.restartStreamOnFormatChange`). A seek is not used, because a seek fails on radio.
- Codec default: a new install gets FLAC. `SettingsRepository` stores the default on the first read. An install that has a server or used the local player gets Opus, because Opus was the default before.
- Spec 1.0.0-rc1 rules that apply on `SPEC_1_0` only: a JSON message that is not UTF-8, or that has no `type` string or no `payload` object, closes the connection (`ServerMessage.BadEnvelope`). An audio message shorter than its 13-byte header also closes it. A payload that does not decode into the client model (`ServerMessage.Malformed`) is not a protocol error, so the client ignores it. The 9.x wire ignores all of these.
- Clock: `ClockFilter` is the spec time filter with the spec parameter values. A test compares it with a literal copy of the spec steps. The one addition is a re-seed: 3 samples in a row that each exclude the prediction from before the streak by more than their `max_error` plus 20 ms, on the same side, mean that the clock moved. `ClockProbe` sends each probe after the reply to the previous probe, 8 probes for each burst. Bursts start 9 s apart (2 s for the first 30 s), under the 10 s limit of the spec. A probe without a reply in 1.5 s ends its burst. The player sends `available` after two samples, as aiosendspin does.

### Android Services Integration

Android foreground services integrate with Sendspin through MainDataSource:

**MainMediaPlaybackService**:
- Handles notifications and lock screen controls
- Shows all active players (excluding deprecated builtin players)
- Accesses player state via `MainDataSource.playersData`
- Uses `MediaSessionHelper` for MediaSession management and volume control (see `.claude/volume-control.md`)

**AndroidAutoPlaybackService**:
- Provides Android Auto support via `MediaBrowserServiceCompat`
- Shows first player with active playback (`queueInfo?.currentItem != null`)
- Uses `playerData.queue` for queue access (not deprecated `builtinPlayerQueue`)
- When Sendspin is playing locally, it appears in Android Auto
- Supports library browsing via `AutoLibrary`
- Shows a Home tab that copies the app home page. The tab has one browsable item for each
  recommendation row. `visibleHomeFolders` (`ui/compose/home/HomeRowsConfig.kt`) filters and
  orders the rows. It uses the same rules and the same `homeRowsConfig` as the app. The Shortcuts
  row is not in the tab. The user turns the tab on or off in Settings → Car → Tabs.
- All actions go through `MainDataSource.playerAction()` and `queueAction()`
- Publishes browse-row and queue-row artwork as opaque, read-only `content://` URIs through
  `AndroidAutoArtworkProvider`. A media host fetches icon URIs in its own process and its own UID,
  so a raw server URL fails whenever the app has routing the host lacks (split-tunnel VPN) and
  always fails for `mawebrtc://` URLs. The provider decodes an authenticated token, fetches under
  the Music Assistant UID, and streams a bounded JPEG. `onGetRoot` hands a prefix read grant to a
  caller whose package really owns its UID and that `MediaSessionManager` trusts for media control.
  Now-playing artwork is unaffected: it is already an in-process bitmap in the session metadata.

**Key Pattern**: Services do NOT create or manage Sendspin directly. They access player data through MainDataSource's playersData StateFlow, maintaining a single source of truth.

See `.claude/sendspin-integration-design.md` and `.claude/sendspin-android-services-integration.md` for detailed technical documentation.

## Misc rules

- Don't ever use non-null assertions in live code (!!). Always handle nulls safely.
- If code reads the `value` of a derived `StateFlow` after it writes the source, derive it with `mapState`
  (`utils/StateFlowView.kt`). Do not use `map { }.stateIn(scope)` for it, because that value updates on
  another coroutine and the read can get the old value.
- Use Kotlin-like idioms (e.g., prefer `let`, `also`, `apply` for scoping).
- Instead of `if-else` chains, prefer `when` expressions for better readability.
- Instead of `if-else` for nullable variable, use safe calls and the Elvis operator, or `?.let{} ?: run {}` expression.