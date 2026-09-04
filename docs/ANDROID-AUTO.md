# Android Auto & Voice Playback

Music Assistant exposes one `MediaBrowserService` (`AndroidAutoPlaybackService`) and one `MediaSession`. From these, the app supports three integration surfaces with a single code path:

1. **Android Auto** — the in-car experience (DHU or a real head unit).
2. **Phone media notification** — the persistent notification with playback controls.
3. **Voice playback** — in the car, and on the phone with the classic Google Assistant.

This document covers what each surface does, how the voice routing works, and the limits of each.

> **Gemini cannot start playback from voice on the phone.** Gemini replaced the classic Google Assistant and does not use the App Actions mechanism this app declares. The app cannot change that. Read Section 4 before you open a bug report about it.

---

## 1. Enabling Android Auto for sideloaded builds

Production / Play Store builds work out of the box. For debug or self-signed APKs, the head unit refuses to load the app until you allow unknown sources:

1. On the phone, open the **Android Auto** app.
2. Tap **Version and permission info** repeatedly until the developer-mode dialog appears, accept it.
3. From the three-dot overflow menu, choose **Developer settings**.
4. Enable **Unknown sources**.
5. In the Android Auto launcher customization, enable **Music Assistant** so it appears among your media apps.

Changing the phone's *Customize Tabs* setting takes effect on the next browse refresh — no reconnect required.

> **Enable the Local Player before you use Android Auto.** Android Auto is scoped to the local on-device player. `onGetRoot` always returns a valid root — a refusal does not hide the app, because the app is declared as a media app, so the car keeps it listed and shows a loading screen forever. With the local player disabled, `AutoLibrary` serves one explanatory row instead of the library, and `SharedMediaSessionManager` deactivates the session. The car then shows the app but offers no content and no now-playing card, so the app cannot seize the media slot on behalf of a remote player. The phone media notification for *remote* players is unaffected. Enable the local player while the car is connected, and the change takes effect on the next Android Auto reconnect.

---

## 2. Library browsing in the car

The root tabs surfaced in Android Auto are driven by the phone's *Customize Tabs* setting. Supported tabs: **Artists, Albums, Playlists, Podcasts, Radio, Audiobooks**. Tracks and Genres are intentionally omitted (too long to scroll safely while driving).

Android Auto only displays four root tabs at the top level — tabs beyond the first four are grouped into a "More" menu by the AA host. Not a bug.

Each collection tab (except Radio) exposes four sub-lists:

- **Recent** — sorted by last played, descending.
- **Favorites** — marked-favorite items only.
- **New** — recently added (or recently *updated* for podcasts).
- **By name** — alphabetical.

**Radio** is a flat list sorted by last played, with a single "Favorites" header at the top.

Browsing is cached for 5 minutes to keep the head unit responsive; reconnecting to the server invalidates the cache automatically.

---

## 3. Voice playback — symmetric architecture

Both the in-car AA voice path and the phone-side App Actions path terminate at **exactly one callback**: `AndroidAutoPlaybackService.onPlayFromSearch`. There is no duplicate handler in `MainMediaPlaybackService`, no `onStartCommand` voice routing, no service-side foreground-service contract to satisfy.

```
                       In-car AA voice               Phone-side voice
                       (DHU / real head unit)        (classic Google Assistant;
                                                      NOT Gemini — see Section 4)
                              │                              │
                              │             ┌────────────────┴────────────────┐
                              │             │  shortcuts.xml capability:      │
                              │             │  actions.intent.PLAY_MEDIA      │
                              │             │  (slots → MEDIA_PLAY_FROM_SEARCH
                              │             │   extras via dispatch Activity) │
                              │             └────────────────┬────────────────┘
                              │                              │
                              │                  VoicePlayDispatchActivity
                              │                  (Theme.NoDisplay; finishes
                              │                   in onCreate)
                              │                              │
                              │                  VoiceDispatcher (process-scoped)
                              │                              │
                              │                  MediaBrowserCompat.connect()
                              │                              │
                              └──────────────┬───────────────┘
                                             ▼
                            bindService(AndroidAutoPlaybackService)
                                             │
                                             ▼
                            MediaController.transportControls
                                .playFromSearch(query, extras)
                                             │
                                             ▼
                       AndroidAutoPlaybackService.onPlayFromSearch
                            • waits ≤10s for dataSource.localPlayer
                            • dispatches to AutoLibrary.searchAndPlay
                                             │
                                             ▼
                              AutoLibrary.searchAndPlay
                            • EXTRA_MEDIA_FOCUS → typed branch
                            • else → Track→Artist→Album→Playlist
                                     →Podcast→Radio cascade
                            • blank query → Favorites shuffle
                                             │
                                             ▼
                              Request.Library.play (REPLACE)
```

When playback actually starts, `ensureNotificationService()` (running inside the bound AA service) observes `isAnythingPlaying` and starts `MainMediaPlaybackService` as a foreground service for background-audio survival. This is the **same** chain the in-car AA experience has always used.

### Target player

Voice playback always routes to the **local on-device player** (`dataSource.localPlayer`), never to a remote MA player the user might have selected in the UI. The semantic contract of "play X on Music Assistant" is "play on this device" — sending it to a kitchen Sonos because that happens to be the currently-selected output would be wrong UX.

### Supported phrases

The app uses the Assistant's `EXTRA_MEDIA_FOCUS` to disambiguate intent. Speak naturally and end with **"on Music Assistant"**:

| Intent | Phrasing examples | Behavior |
|---|---|---|
| Artist | "play **Radiohead**", "play music by **Daft Punk**" | All tracks by artist, shuffled |
| Album | "play album **OK Computer**", "play **Dark Side of the Moon** by Pink Floyd" | Album in track order |
| Song / Track | "play **Bohemian Rhapsody**", "play **Yesterday by The Beatles**" | The single track (artist filter when provided) |
| Playlist | "play my **Workout** playlist" | The matched playlist |
| Genre | "play **jazz**", "play some **classical**" | Track search with the genre as query |
| Podcast | "play the **Daily** podcast" | Latest episode by release date |
| Radio | "play **BBC Radio 6**", "play **KEXP**" | The matched station |
| Random | "play **some music**", "play **music**" | Favorite tracks shuffled (falls back to Recently Played if no favorites) |

When the Assistant doesn't tag a focus type, the app cascades **Track → Artist → Album → Playlist → Podcast → Radio**. First non-empty bucket wins — biased toward exact song-title matches, which is the most common voice phrasing.

---

## 4. Phone-side voice — App Actions, and the Gemini limit

Phone-side voice routing uses the **App Actions** framework. The capability declaration in `androidApp/src/main/res/xml/shortcuts.xml` registers `actions.intent.PLAY_MEDIA` with seven `<intent>` clauses. There is one clause per schema.org media type, plus an unstructured fallback. The Assistant NLU matches the spoken phrase. It picks the clause that fits the `media.@type` slot, projects the recognised slots into `MEDIA_PLAY_FROM_SEARCH` extras, and sends the intent to `VoicePlayDispatchActivity`.

App Actions is the **classic Google Assistant** mechanism.

### Gemini does not use App Actions

Gemini replaced the classic Google Assistant on Android in September 2026. Gemini does not read App Actions built-in intents. Ask Gemini to "play <something> on Music Assistant" and it answers that the app is not supported by its tools. No `VoiceDispatch` log line appears, because the intent never leaves Google.

**This app cannot fix that.** Gemini routes media to a Google-side partner list of connected apps. At the time of writing that list holds iHeartRadio, Pandora and Spotify. The successor developer API, AppFunctions, is in a private preview for trusted testers. No manifest entry, capability declaration or session flag adds an app to the list. Do not accept a bug report that says the app must declare something to appear there.

Keep the App Actions declaration. It costs nothing, it still works on a device where the classic Assistant survives, and it is the entry point for the `adb` tests in Section 5.

### What still works

| Surface | Command | Status |
|---|---|---|
| Android Auto (car or DHU) | "play Radiohead" | Works. Uses the browser service and the media session, not App Actions. |
| Gemini or Assistant, transport control | "pause", "next", "resume" | Works while the app has a session. The session advertises `ACTION_PLAY_FROM_SEARCH` and the other transport actions in every state, including the idle state. |
| Gemini, phone | "play X on Music Assistant" | Refused by Gemini. Not fixable in the app. |
| Classic Google Assistant, phone | "play X on Music Assistant" | Works where the classic Assistant is still installed. |

### User-side enablement for the classic Assistant

Open *Google app → profile → Settings → Google Assistant → Music*. Select Music Assistant as the default provider, or select *No default provider* so the "on Music Assistant" suffix is honored. If the app is not in the music-provider list, open the app one time after install so the system can register the App Actions metadata. Registration is best-effort on Google's side. It can need a few minutes, or a phone restart, before the NLU index refreshes.

### Architecture notes

- `VoicePlayDispatchActivity` is `Theme.NoDisplay` and calls `finish()` synchronously inside `onCreate`, satisfying the OS contract that forbids async work before `onResume()` completes.
- The async `MediaBrowserCompat.connect()` runs on a process-scoped singleton (`VoiceDispatcher` in the same file), so it survives the Activity's immediate tear-down.
- A new voice command supersedes any in-flight bind — the previous attempt is disconnected before the new one starts.
- The dispatch path is functionally identical to what the in-car AA framework does when it binds our `MediaBrowserService` — same callback, same handler, same routing.

---

## 5. Testing voice intents from the shell

You can fire `MEDIA_PLAY_FROM_SEARCH` intents directly without involving any Assistant, which is much faster for iteration. Target `VoicePlayDispatchActivity` explicitly so the OS doesn't show an app chooser when multiple apps register the same action:

```bash
# Artist intent
adb shell am start \
  -n io.music_assistant.client/.VoicePlayDispatchActivity \
  -a android.media.action.MEDIA_PLAY_FROM_SEARCH \
  --es query "Radiohead" \
  --es android.intent.extra.focus "vnd.android.cursor.item/artist" \
  --es android.intent.extra.artist "Radiohead"

# Album intent (with artist refinement)
adb shell am start \
  -n io.music_assistant.client/.VoicePlayDispatchActivity \
  -a android.media.action.MEDIA_PLAY_FROM_SEARCH \
  --es query "OK Computer" \
  --es android.intent.extra.focus "vnd.android.cursor.item/album" \
  --es android.intent.extra.album "OK Computer" \
  --es android.intent.extra.artist "Radiohead"

# Track intent (with artist disambiguation)
adb shell am start \
  -n io.music_assistant.client/.VoicePlayDispatchActivity \
  -a android.media.action.MEDIA_PLAY_FROM_SEARCH \
  --es query "Yesterday" \
  --es android.intent.extra.focus "vnd.android.cursor.item/audio" \
  --es android.intent.extra.title "Yesterday" \
  --es android.intent.extra.artist "The Beatles"

# Playlist intent
adb shell am start \
  -n io.music_assistant.client/.VoicePlayDispatchActivity \
  -a android.media.action.MEDIA_PLAY_FROM_SEARCH \
  --es query "Workout" \
  --es android.intent.extra.focus "vnd.android.cursor.item/playlist" \
  --es android.intent.extra.playlist "Workout"

# "Play some music" — no query, no extras
adb shell am start \
  -n io.music_assistant.client/.VoicePlayDispatchActivity \
  -a android.media.action.MEDIA_PLAY_FROM_SEARCH

# Unstructured query (no focus)
adb shell am start \
  -n io.music_assistant.client/.VoicePlayDispatchActivity \
  -a android.media.action.MEDIA_PLAY_FROM_SEARCH \
  --es query "Bohemian Rhapsody"
```

For Assistant-side validation without speaking, install Google's [App Actions Test Tool](https://developer.android.com/guide/app-actions/test-tool) (Android Studio plugin) and trigger `actions.intent.PLAY_MEDIA` with synthetic slot values — that's the canonical dev loop for verifying capability fulfilment.

> Do **not** target `AndroidAutoPlaybackService` directly with `startForegroundService` from the shell. It is a `MediaBrowserServiceCompat` without `foregroundServiceType` declared, and the OS will kill the process with `ForegroundServiceDidNotStartInTimeException` after 5 seconds. Use `VoicePlayDispatchActivity` as shown — it binds the service the way the OS expects.

### Logcat trace

```bash
adb logcat -v time VoiceDispatch:V AndroidAuto:V SharedSession:V '*:S'
```

The app emits three tags: `VoiceDispatch`, `AndroidAuto` (the browse library and the playback service share it) and `SharedSession`. Earlier versions of this document named `AAService`, `AAPlayFromSearch` and `AAVoice`. Those tags do not exist. Do not use them, or you capture an empty log.

A successful voice command produces this sequence:

1. `VoiceDispatch: Received voice intent …` *(phone-side only)*
2. `VoiceDispatch: Bound session token acquired — sending playFromSearch …` *(phone-side only)*
3. `AndroidAuto: onGetRoot from package=…`
4. `AndroidAuto: Real media host '…' — promoting to AA owner (projection=…)`
5. `SharedSession: acquire — refCount=…`
6. `AndroidAuto: onPlayFromSearch query="…" extras={…}`
7. `AndroidAuto: Dispatching to AutoLibrary (local player ready)`
8. `AndroidAuto: searchAndPlay focus=… query="…"`
9. `AndroidAuto: Library.play REPLACE queueId=… items=… first=…`

Each missing step points the finger:

| Missing | Cause | Fix |
|---|---|---|
| No `VoiceDispatch` | The assistant did not route to the app. With Gemini this is expected and permanent — see Section 4. | Check that you use the classic Assistant, and see the enablement steps in Section 4. |
| `VoiceDispatch` present, but no `onPlayFromSearch` | The `MediaBrowser` bind failed. Rare. | Check that the playback service does not crash. |
| `onPlayFromSearch` present, but no `searchAndPlay` | The local player is disabled, or it did not start in 10 seconds. | Enable the Local Player. Open the app one time to start authentication and the local player. |
| `searchAndPlay` present, but a zero-hit warning | The query matched nothing in the library. | Check that the query exists in Music Assistant. |

---

## 6. Limitations & known quirks

- **Local player required**: the whole Android Auto surface (browsing, voice, now-playing) is gated on the local on-device player being enabled. With it disabled, MA is deliberately invisible to AA (see Section 1) so it can never take over the car's media slot on behalf of a remote player. There is intentionally no "not enabled" placeholder in the car — MA simply doesn't appear.
- **Player target**: voice playback always goes to the **local on-device player** (the phone speakers or whatever the phone's audio output is routed to). Voice control of remote MA players (a Sonos in the kitchen, a Squeezebox in the office) is not supported — voice integration is scoped to the local player only.
- **Android Auto voice routing when another app is playing**: the AA Assistant frequently ignores the "on Music Assistant" suffix when another media app currently owns the active `MediaSession`. Workaround: focus Music Assistant in the AA media-app picker first, or pause the other app. This is documented Google behavior, not something we can override from the app side.
- **Gemini does not play from voice on the phone**: Gemini ignores App Actions and routes media only to apps on a Google-side partner list. The app cannot join that list. See Section 4. Android Auto voice and Gemini transport control ("pause", "next") are unaffected.
- **Phone Assistant routing to default provider**: with Spotify (or another partner-certified app) set as the default music provider, the classic Google Assistant can decide the routing before the App Actions capability fires. Set the default provider to Music Assistant, or to "none", to avoid this.
- **Podcast freshness**: "latest episode" is picked by `release_date` (lexicographic max). Providers that emit non-ISO-8601 release-date strings may yield a wrong pick.
- **Genre voice intent**: there is no curated "genre radio" — the genre name is just passed as a query string to the track search. Quality depends entirely on how providers tag their genres.
- **App label**: Assistant matches the app by its launcher label ("Music Assistant"). If your launcher renames or hides the app, voice routing can fail — restore the default label.
- **No `onPrepareFromSearch`**: prepare-then-play is not wired. Voice commands play immediately; there is no "queued for later" path. Assistant rarely uses prepare with media-browser services, so this is acceptable.
- **No heuristic keyword sniffing**: the app does not try to detect words like "radio" or "podcast" inside an unstructured query. Either Assistant's NLU sets the focus extras correctly, or the query falls through the unstructured cascade. Adding language-specific keyword sniffing is deliberately out of scope.

---

## 7. Reporting issues

When voice routing misbehaves, capture:

1. The exact phrase you spoke.
2. The `VoiceDispatch` and `AndroidAuto` log lines. Use the filter in Section 5.
3. The surface: classic Assistant on the phone, Gemini, the DHU microphone, or "Hey Google" in a real car.
4. What you expected, and what played.

Read Section 4 first. If the surface is Gemini on the phone, and the log holds no `VoiceDispatch` line, then Gemini refused the request before it reached the app. That is a Google limit, not an app defect.

Open an issue at [music-assistant/mobile-app](https://github.com/music-assistant/mobile-app/issues) with that information. The `focus` + `query` pair is the single most useful debugging signal.

---

## Source map

| Component | File |
|---|---|
| Bound-service callback + dispatch | `androidApp/src/main/kotlin/io/music_assistant/client/services/AndroidAutoPlaybackService.kt` |
| Focus-aware search and play | `androidApp/src/main/kotlin/io/music_assistant/client/auto/AutoLibrary.kt` |
| Phone-side App Actions dispatch | `androidApp/src/main/kotlin/io/music_assistant/client/VoicePlayDispatchActivity.kt` |
| App Actions capability map | `androidApp/src/main/res/xml/shortcuts.xml` |
| MediaSession ownership (AA vs notification) | `androidApp/src/main/kotlin/io/music_assistant/client/services/SharedMediaSessionManager.kt` |
| Foreground audio host (post-playback) | `androidApp/src/main/kotlin/io/music_assistant/client/services/MainMediaPlaybackService.kt` |
| Manifest registrations | `androidApp/src/main/AndroidManifest.xml` |
