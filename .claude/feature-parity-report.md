# Feature parity report: mobile app against Music Assistant 2.10 and 2.11 beta

This report is written to ASD-STE100 rules. It compares the listener-facing feature set of the
mobile app with the Music Assistant server and web frontend. It covers the current release
(2.10) and the 2.11 beta (the `dev` branch).

## 1. Summary

The app has parity with the web frontend for the core listener flows:

- home rows, library lists and filters, global search, browse, item details
- play actions, player controls, queue, grouping
- announcements, ambient sound, AI Radio
- authentication, remote access, and the car surfaces

Five changes in 2.11 need work in the app:

1. `playlist_tracks` and `podcast_episodes` return one page of 500 items instead of a stream.
   The app sends no `limit`, so long playlists and podcasts lose items. Priority: high.
2. Favorites are per user and have three states (like, dislike, none). The server strips
   `favorite` from `media_item_updated` events and sends a new `favorite_updated` event. The app
   reconciles the optimistic heart from the old event. Priority: high.
3. The Sendspin server moved to spec 1.0.0-rc1 (aiosendspin 10.x). The app still speaks the old
   wire. The server accepts old clients by default and logs them as noncompliant. An admin can
   turn that off. Priority: high, size large.
4. Sorting moved to the server (`sort_field`, `sort_direction`, `music/sort_options`). The app
   uses the deprecated `order_by`. Priority: low.
5. Permissions use scopes and custom roles. The app gates only AI Radio on scopes. Priority:
   medium.

The app constant `LOCAL_SCHEMA_VERSION` is 65, the schema of server 2.10.6 and 2.10.7. A 2.10
server does not trigger the "server ahead" warning. A 2.11 beta (96 or 97) triggers it on connect.

The count of missing listener features is about 35. Most are Easy or Medium. The visualizer is
not viable on mobile. Party mode, music quiz and smart playlists are Hard.

## 2. Method and inputs

| Input | Version | Notes |
|---|---|---|
| Server 2.10 | tag `2.10.6` | `API_SCHEMA_VERSION` 65, `MIN_SCHEMA_VERSION` 28 |
| Server 2.10 patch | tag `2.10.7` (2026-10-17) | schema and models as in 2.10.6; aiosendspin 10.2.0, see 5.10 |
| Server 2.11 beta | `dev` at 2026-10-16 (`10a3ad5`) | `API_SCHEMA_VERSION` 96, `MIN_SCHEMA_VERSION` 28 |
| Frontend 2.10 | tag `2.17.297` | bundled with server 2.10.6 |
| Frontend 2.11 beta | tag `2.17.343` | pinned by server dev; refuses servers below schema 86 |
| Models 2.10 | `music-assistant-models` 1.1.205 | |
| Models 2.11 beta | `music-assistant-models` 1.1.225 | |
| Mobile app | branch `main`, 2026-10-16 | `LOCAL_SCHEMA_VERSION` 59 |

The method:

1. List every `@api_command` in both server trees and compare signatures.
2. List every command the frontend calls and every listener-facing view and action.
3. List every command the app calls and every screen and action, for phone, Android Auto and
   CarPlay.
4. Match the three lists. "Parity" means the same user outcome, not the same UI.

Scope: listener-facing features only. Admin features are listed once in section 8.

Paths below use these prefixes:

- `app/` = `composeApp/src/commonMain/kotlin/io/music_assistant/client/`
- `aa/` = `androidApp/src/main/kotlin/io/music_assistant/client/`
- `ios/` = `iosApp/iosApp/`
- `sendspin/` = `sendspin-kmp/src/commonMain/kotlin/io/music_assistant/sendspin/`
- `server/` = `music_assistant/` in the server tree. "dev" marks the 2.11 tree.
- `models/` = `music_assistant_models/` in the models package.

## 3. Legend

Classes:

| Class | Meaning |
|---|---|
| Parity | The app gives the same outcome as the web on 2.10 and on 2.11. |
| Adjust-2.11 | The app works on 2.10, but 2.11 changed the API or the behavior. |
| Adjust-partial | The app has the feature, but a part of the web outcome is missing. |
| Missing | The web has the feature and the app has nothing. |
| Decided | The project decided to leave the feature out. The report does not propose it. |
| Out of scope | Admin or web-only feature. |

Surface cells: `yes`, `part`, `no`, `n/a`. `n/a` means the surface cannot host the feature, or
the feature has no meaning there.

Feasibility for missing features:

| Level | Meaning |
|---|---|
| Easy | The API exists on 2.10 and 2.11. One screen or one action. Reuses existing composables. |
| Medium | A new screen or dialog, a platform integration (file picker, share sheet), or a 2.11-only API with a 2.10 fallback. |
| Hard | A new subsystem, a task-progress UI, or a guest or multi-session flow. |
| Not viable | Needs a browser-only runtime, or contradicts a documented decision. |

## 4. Parity matrix

Columns: Web 2.10, Web 2.11, Phone, AA (Android Auto), CP (CarPlay).

### 4.1 Home

| Feature | Web 2.10 | Web 2.11 | Phone | AA | CP | Class | Note |
|---|---|---|---|---|---|---|---|
| Recommendation rows | yes | yes | yes | yes | yes | Parity | `music/recommendations` and `recommendations/items` |
| Row reorder and hide | yes | yes | yes | n/a | n/a | Parity | AA uses the same `homeRowsConfig` |
| Shortcuts row | yes | yes | yes | no | no | Parity | Reads `sidebar.shortcuts`, resolves with `item_by_uri` |
| Add or remove a shortcut | yes | yes | no | no | no | Missing | Write `sidebar.shortcuts` with `auth/user/update` |
| In progress and recently played rows | yes | yes | part | part | part | Adjust-partial | Web adds the rows itself. App shows them only if the server sends them as recommendations |
| Per-row provider filter | yes | yes | no | n/a | n/a | Missing | Preference `discover.hiddenProviders.<row>` |
| Played-state badge on cards | no | yes | no | no | no | Missing | Check for fully played, clock for in progress |
| Players row on home | yes | yes | yes | n/a | n/a | Parity | App uses the player pager |

### 4.2 Library lists

| Feature | Web 2.10 | Web 2.11 | Phone | AA | CP | Class | Note |
|---|---|---|---|---|---|---|---|
| Lists for 8 media types | yes | yes | yes | part | part | Parity | Cars have no Tracks tab and no Genres tab |
| Server paging | yes | yes | yes | yes | yes | Parity | |
| Sort options | client table | server `sort_options` | client table | fixed | fixed | Adjust-2.11 | See 5.4 |
| Favorites, provider, genre, album type, album artist filters | yes | yes | yes | n/a | n/a | Parity | AA has a Favorites sub-list |
| Series grouping for audiobooks | yes | yes | yes | flat | flat | Parity | `collapse_collections` |
| Author and narrator tabs | yes | yes | yes | n/a | n/a | Parity | Issue #1201. A "Show" row in the Audiobooks filter sheet, offered when `library_artist_types` has authors or narrators |
| Genre content-type tabs | yes | yes | yes | n/a | n/a | Parity | Issue #1201. A "Content type" row in the Genres filter sheet sends `content_type`. The "Media type" row refines the music content type only |
| Hide fully played episodes | yes | yes | yes | no | no | Parity | Issue #1201. A toggle on the episodes tab. The app sends `fully_played=false` and also filters the answer, because 2.10 ignores the argument |
| List and grid view | yes | yes | yes | n/a | n/a | Parity | |
| Grid size slider | no | yes | no | n/a | n/a | Missing | |
| In-list search | yes | yes | yes | n/a | n/a | Parity | App sends the `search` argument |
| Multi-select and bulk actions | yes | yes | no | n/a | n/a | Missing | |
| Empty state with "show results on provider" | part | yes | no | n/a | n/a | Missing | |
| Retry after a load failure | no | yes | part | n/a | n/a | Adjust-partial | App shows an error state. Check that it offers retry |
| Add a radio or a track by URL | yes | yes | no | n/a | n/a | Missing | `builtin/add_radio`, `builtin/add_track` |
| Create playlist | yes | yes | yes | n/a | n/a | Parity | |
| Import and export playlist | yes | yes | no | n/a | n/a | Missing | M3U file; 2.11 adds `match_policy` |
| Smart playlist create | yes | yes | no | n/a | n/a | Missing | `smart_playlists/*` |
| Track table with BPM and key columns | no | yes | no | n/a | n/a | Missing | Low value on a phone |
| Recent and New sub-lists in the car | n/a | n/a | n/a | yes | no | Parity | App feature for AA |

### 4.3 Search

| Feature | Web 2.10 | Web 2.11 | Phone | AA | CP | Class | Note |
|---|---|---|---|---|---|---|---|
| Global search for 7 media types | yes | yes | yes | yes | Siri | Parity | AA: `onSearch` and voice. CP: Siri only |
| Genre search | yes | yes | yes | no | no | Parity | Issue #1197. From server 2.10.0, `music/search` searches the library genres. The app has a Genre chip, and a genre-only search asks the library only, like the web |
| Library-only toggle | yes | yes | yes | n/a | n/a | Parity | From server 2.10.0, the Library source of the source picker replaces it |
| Source picker per provider | no | yes | yes | n/a | n/a | Parity | Issue #1197. Preference `search.sources`, shared with the web. Server 2.10.0 (schema 63) and later |
| Recent searches | yes | yes | yes | n/a | n/a | Parity | Issue #1197. Preference `search.recent`, shared with the web. Opening, playing or playing from the menu of a result saves the query |
| Search limits | 15 per type, 50 for one type | same | 10 per type, 30 for one type | 10 | 10 | Parity | Limits must stay small, see memory on the 8 s soft timeout |
| In-car search screen | n/a | n/a | n/a | yes | no | Decided | CarPlay audio apps have no search template |

### 4.4 Browse

| Feature | Web 2.10 | Web 2.11 | Phone | AA | CP | Class | Note |
|---|---|---|---|---|---|---|---|
| Provider folder tree | yes | yes | yes | no | no | Parity | |
| Audio sources at the root | yes | yes | yes | no | no | Parity | Play now only |
| Sort and paging inside a folder | no | yes | no | n/a | n/a | Adjust-2.11 | `music/browse` gained `search`, `sort_field`, `limit`, `offset` |
| Play and Shuffle buttons on a playable folder | no | yes | no | n/a | n/a | Missing | `play_media` with the folder URI |

### 4.5 Item details

| Feature | Web 2.10 | Web 2.11 | Phone | AA | CP | Class | Note |
|---|---|---|---|---|---|---|---|
| Artist page | yes | yes | yes | drill | drill | Parity | App: top tracks with source picker, library albums, all albums, similar artists |
| Artist appears on, singles, discography | no | yes | no | no | no | Missing | `artist_appears_on`, `discography`. Discography items are provider `musicbrainz`, addable but not playable |
| Artist audiobooks row | yes | yes | yes | no | no | Parity | Issue #1201. An author or narrator page shows "In library" and "All audiobooks" rows instead of albums and top tracks |
| Album page | yes | yes | yes | drill | drill | Parity | Year shown in the header |
| Album versions, review, more from artist | yes | yes | no | no | no | Missing | `album_versions` |
| Track page | yes | yes | yes | n/a | n/a | Parity | Albums, versions and similar tracks tabs. Similar shows only if a provider has `similar_tracks`. Lyrics in the overflow menu. Open with "Go to track" or the player title |
| Playlist page | yes | yes | yes | drill | drill | Parity | Force refresh in the header |
| Playlist description and featured artists | part | yes | description | no | no | Missing | Featured artists are missing. The description, the owner and the access record are on the page |
| Podcast page | yes | yes | yes | drill | drill | Parity | Episode sort by name, date or duration |
| Podcast episode page with transcript | no | yes | no | no | no | Missing | `podcast_episode`, `podcast_episode_transcript` |
| Audiobook page with chapters | yes | yes | yes | n/a | buttons | Parity | Tap a chapter to play from it |
| Collection (series) page | yes | yes | yes | flat | flat | Parity | |
| Radio page | yes | yes | no | n/a | n/a | Missing | `radio_versions`; 2.11 adds `radio_tracks` for finite stations |
| Genre page | yes | yes | yes | no | no | Parity | Artists and Albums tabs from `genres/overview` |
| Descriptions, biography, publisher, narrators | yes | yes | no | no | no | Adjust-partial | The DTO parses the fields. No screen renders them |
| User-customizable detail rows | no | yes | no | n/a | n/a | Missing | Low value on a phone |
| Find in list | no | no | yes | n/a | n/a | Parity | App extra |

### 4.6 Item actions

| Feature | Web 2.10 | Web 2.11 | Phone | AA | CP | Class | Note |
|---|---|---|---|---|---|---|---|
| Play now, next, add, replace | yes | yes | yes | tap action | tap action | Parity | |
| Replace next (`REPLACE_NEXT`) | yes | yes | yes | no | no | Parity | Menu entry; not a car action |
| Play from here | yes | yes | yes | yes | yes | Parity | |
| Play shuffled | yes | yes | yes | bulk action | bulk action | Parity | `play_media` with `shuffle=true`, schema 52 and newer |
| Radio from item (endless mix) | yes | yes | yes | no | no | Parity | |
| Add to and remove from library | yes | yes | yes | no | no | Parity | |
| Favorite | yes | yes | yes | yes | yes | Adjust-2.11 | See 5.2 and 5.3 |
| Dislike | no | yes | no | no | no | Missing | `favorites/set_item(false)` |
| Add to playlist, remove from playlist | yes | yes | yes | no | no | Parity | |
| Mark played and unplayed | yes | yes | yes | no | no | Parity | |
| Play from beginning | yes | yes | yes | no | no | Parity | Podcast episodes with progress, schema 40 and newer |
| Refresh item, find on music services | yes | yes | details menu | no | no | Parity | `music/refresh_item`. 2.11 lowers the scope to `library.write`. The app offers it on the item details screen only |
| Update metadata, edit builtin items | yes | yes | no | no | no | Out of scope | Needs `library.manage` |
| Export, migrate, share playlist | export | all three | no | no | no | Missing | See 4.10 |
| Go to artist and album | yes | yes | yes | no | no | Parity | |
| Default click and enqueue customization | server config | same | local matrix | yes | yes | Parity | Storage differs. App stores it in settings |
| Show info | yes | yes | part | no | no | Adjust-partial | App has no info sheet for tracks and radios |

### 4.7 Player and now playing

| Feature | Web 2.10 | Web 2.11 | Phone | AA | CP | Class | Note |
|---|---|---|---|---|---|---|---|
| Play, pause, next, previous, seek | yes | yes | yes | yes | yes | Parity | |
| Stop | yes | yes | yes | no | no | Parity | Stop replaces Pause for a radio stream and for an input source without pause support |
| Skip back and forward for long content | yes | yes | yes | yes | chapters | Parity | 2.11 web uses `player_queues/skip`. App seeks by delta |
| Shuffle, repeat, crossfade, autoplay | yes | yes | yes | part | part | Parity | Cars show shuffle and repeat |
| Autoplay locked by repeat indicator | no | yes | no | n/a | n/a | Missing | |
| Playback speed | yes | yes | yes | no | no | Parity | |
| Volume, mute, group volume, member volume | yes | yes | yes | keys | n/a | Parity | |
| Power | yes | yes | yes | no | no | Parity | |
| Sleep timer | yes | yes | yes | no | no | Parity | Schema 35 and newer |
| Lyrics with offset | yes | yes | yes | no | no | Parity | |
| Podcast transcript in the lyrics panel | no | yes | no | no | no | Missing | Timed cues |
| Waveform on the seek bar | yes | yes | no | no | no | Missing | `audio_analysis/wave_form` |
| Visualizer | yes | yes | no | n/a | n/a | Not viable | WebGL and Web Audio in the browser |
| Stream and quality details | yes | yes | yes | no | no | Parity | |
| "Played from" container | no | yes | yes | no | no | Parity | `QueueItem.origin` |
| Ambient sound overlay | yes | yes | yes | no | no | Parity | |
| Announcements, text and voice | yes | yes | yes | no | no | Parity | Schema 46 and 48 |
| Transfer queue, clear queue | yes | yes | yes | no | no | Parity | |
| Save queue as playlist | yes | yes | no | no | no | Missing | `player_queues/save_as_playlist` |
| Select source, select sound mode | yes | yes | no | no | no | Decided | `architecture.md` excludes native inputs |
| AI DJ host | yes | yes | yes | no | no | Parity | App needs schema 88 |
| DSP enable and preset | settings | settings | yes | presets | presets | Parity | |
| Player select and order | yes | yes | yes | switch action | n/a | Parity | |
| Cast to dashboard | yes | yes | no | n/a | n/a | Missing | `dashboard/*`, scope `users.invite` |
| Dynamic colors | no | no | yes | n/a | n/a | Parity | App extra |
| Pending play indicator | no | no | yes | no | no | Parity | App extra |
| Players that need setup | yes | yes | part | no | no | Adjust-partial | App lists them. It has no setup action, which is admin work |

### 4.8 Queue

| Feature | Web 2.10 | Web 2.11 | Phone | AA | CP | Class | Note |
|---|---|---|---|---|---|---|---|
| View, play index, reorder, remove | yes | yes | yes | yes | no | Parity | AA exposes the session queue |
| Move to next, move to end | yes | yes | no | no | no | Decided | Drag reorders the queue (issue #1193) |
| Radio from a queue item | yes | yes | yes | no | no | Parity | "Start endless mix" in the long-press menu replaces the queue; the web opens the radio playlist page |
| Add a queue item to a playlist | no | no | yes | no | no | Parity | App extra |
| Chapter jump from the queue | yes | yes | yes | n/a | buttons | Parity | |
| Queue settings page | yes | yes | no | no | no | Out of scope | Scope `config.players.write` |

### 4.9 Grouping

| Feature | Web 2.10 | Web 2.11 | Phone | AA | CP | Class | Note |
|---|---|---|---|---|---|---|---|
| Join and leave members, group volume | yes | yes | yes | no | no | Parity | `set_members`, `ungroup` |
| Local player playback delay | n/a | n/a | yes | no | no | Parity | App extra |
| Create a group player | settings | settings | no | no | no | Out of scope | `players/create_group_player` |
| Group the local player | yes | yes | no | no | no | Decided | See memory on local-player grouping |

### 4.10 Playlists

| Feature | Web 2.10 | Web 2.11 | Phone | AA | CP | Class | Note |
|---|---|---|---|---|---|---|---|
| Create, add tracks, remove tracks | yes | yes | yes | n/a | n/a | Parity | |
| Rename, change image, delete | yes | yes | no | n/a | n/a | Missing | `playlists/update`, `library/remove_item` |
| Reorder tracks | no | no | no | n/a | n/a | n/a | No server command exists |
| Import and export | yes | yes | no | n/a | n/a | Missing | File picker and share sheet |
| Share and collaborative playlists | no | yes | no | n/a | n/a | Missing | `playlists/set_access`, `share_candidates` |
| Migrate to another provider | no | yes | no | n/a | n/a | Missing | `migrate_playlist` returns a background task |
| Smart playlists | yes | yes | no | n/a | n/a | Missing | Rule editor |
| Force refresh | yes | yes | yes | n/a | n/a | Parity | |

### 4.11 Podcasts and audiobooks

| Feature | Web 2.10 | Web 2.11 | Phone | AA | CP | Class | Note |
|---|---|---|---|---|---|---|---|
| Resume position, fully played state | yes | yes | yes | yes | yes | Parity | |
| Chapters and chapter progress | yes | yes | yes | n/a | buttons | Parity | Preference `audiobook_chapter_progress` |
| Mark played and unplayed | yes | yes | yes | no | no | Parity | |
| Play episodes from here to the latest | yes | yes | yes | no | no | Parity | Episodes tab of the podcast |
| Episode and season numbers | no | yes | yes | yes | yes | Parity | `PodcastEpisode.episode_number`, `season` |
| Transcript | no | yes | yes | no | no | Parity | Episode page and player sheet; schema 81 (`supportsEpisodeTranscript`) |
| Next unplayed book in a series | yes | yes | yes | yes | yes | Parity | Server starts the first unfinished book |

### 4.12 Radio and AI Radio

| Feature | Web 2.10 | Web 2.11 | Phone | AA | CP | Class | Note |
|---|---|---|---|---|---|---|---|
| Library radio list | yes | yes | yes | yes | yes | Parity | App hides AI Radio show rows |
| Favorite the song on air | no | no | yes | yes | no | Parity | App extra, schema 27 |
| Finite station tracklist | no | yes | no | no | no | Missing | `Radio.is_endless`, `radio_tracks` |
| AI Radio shows: list and play | session API | Radio items | Radio items | yes | yes | Parity | App needs schema 88; 2.10 sessions are not supported |
| AI Radio show and host editing | yes | yes | no | no | no | Out of scope | Scope `config.providers.write` |

### 4.13 Authentication and user

| Feature | Web 2.10 | Web 2.11 | Phone | AA | CP | Class | Note |
|---|---|---|---|---|---|---|---|
| Username and password login | yes | yes | yes | n/a | n/a | Parity | |
| Home Assistant OAuth | yes | yes | yes | n/a | n/a | Parity | iOS uses `ASWebAuthenticationSession` |
| PKCE return to the app | no | yes | yes | n/a | n/a | Parity | `auth/exchange` (issue #1184). Remote Access only |
| Saved token auto-login | yes | yes | yes | n/a | n/a | Parity | |
| Long-lived token login | yes | yes | yes | n/a | n/a | Parity | "Use a token instead" in the Music Assistant login form (issue #1202). The app sends the token with `auth` |
| Join code login | yes | yes | no | n/a | n/a | Missing | `auth/join_code/exchange` |
| Logout | yes | yes | yes | n/a | n/a | Parity | |
| Profile: name, avatar, password, tokens | yes | yes | no | n/a | n/a | Missing | `auth/me`, `auth/user/update`, `auth/tokens` |
| Scope-gated UI | admin flag | scopes and roles | AI Radio only | n/a | n/a | Adjust-2.11 | See 5.5 |
| Guest and dashboard sessions | yes | yes | no | n/a | n/a | Out of scope | |
| Localized login errors | no | yes | part | n/a | n/a | Adjust-partial | App shows the `error` text. 2.11 localizes it to the connection locale |

### 4.14 Remote access

| Feature | Web 2.10 | Web 2.11 | Phone | AA | CP | Class | Note |
|---|---|---|---|---|---|---|---|
| WebRTC connection with remote ID | yes | yes | yes | via phone | via phone | Parity | QR scan and history are app extras |
| Images, Sendspin, voice over WebRTC | yes | yes | yes | yes | yes | Parity | Channel labels unchanged in 2.11 |
| Configure remote access | yes | yes | no | n/a | n/a | Out of scope | Scope `system.manage` |

### 4.15 Settings

| Feature | Web 2.10 | Web 2.11 | Phone | AA | CP | Class | Note |
|---|---|---|---|---|---|---|---|
| Theme | yes | yes | yes | n/a | n/a | Parity | |
| Language | explicit | explicit | follows the device | n/a | n/a | Parity | App sends the locale on `auth` |
| Menu customization | yes | yes | yes | yes | yes | Parity | App: library tiles and car tabs |
| Audiobook chapter progress preference | yes | yes | yes | n/a | n/a | Parity | |
| Volume slider mode and haptics | yes | yes | no | n/a | n/a | Missing | Low value |
| Expert mode, onboarding, guided tour | no | yes | no | n/a | n/a | Out of scope | Web-only UX |
| Local player, car, mTLS, saved connections | n/a | n/a | yes | yes | yes | Parity | App extras |
| Player and DSP settings pages | yes | yes | no | n/a | n/a | Out of scope | Dead web deep links exist in `app/ui/compose/home/HomeScreenViewModel.kt` |

### 4.16 Live updates

| Feature | Web 2.10 | Web 2.11 | Phone | AA | CP | Class | Note |
|---|---|---|---|---|---|---|---|
| Player, queue and media item events | yes | yes | yes | yes | yes | Parity | |
| `favorite_updated` | no | yes | no | no | no | Adjust-2.11 | See 5.3 |
| `providers_updated` | yes | yes | yes | yes | yes | Parity | `ProviderRepository` loads the list again (#1186) |
| `playlog_updated` | yes | yes | yes | no | no | Adjust-partial | The phone home rows load again (#1186). The car home tabs do not |
| Sleep timer and queue settings events | yes | yes | n/a | n/a | n/a | Parity | No server sends `queue_settings_updated`. `player_updated` has the sleep timer |
| Unsupported install banner | no | yes | no | n/a | n/a | Missing | `server_info.unsupported_install`, schema 87 |
| Reconnect grace | no | yes | yes | yes | yes | Parity | App transports reconnect with backoff |

## 5. Adjustments for 2.11

Decisions taken on 2026-10-16:

- The app keeps support for server 2.10.
- The app gates every 2.11 feature on one constant: the schema version of the 2.11 release.
  The dev branch reports 96 today. Set the constant to the final number when 2.11.0 ships.
  The app does not support the earlier 2.11 betas.
- Tracker: issues #1179 to #1213 on `music-assistant/mobile-app` cover sections 5 to 7.

### 5.1 Playlist tracks and podcast episodes return one page

What changed: `music/playlists/playlist_tracks` and `music/podcasts/podcast_episodes` return a
list with `limit` default 500 and `offset` default 0 (dev `server/controllers/music/media/playlists.py:157`,
`podcasts.py:70`). In 2.10 both commands streamed the full list in `partial` batches. Both
commands also gained `search`, `sort_field`, `sort_direction` and `favorite`. Episodes gained
`fully_played` and `exclude_item_id`. `limit=0` returns all items.

What the app does: `Request.Playlist.tracks` sends no `limit` (`app/api/Request.kt:424`).
`Request.Podcast.episodes` uses `Library.subItems` and sends no `limit` (`app/api/Request.kt:483`).
`RpcEngine` reassembles `partial` batches, which 2.11 no longer sends.

Impact: on 2.10, no change. On 2.11, a playlist or podcast with more than 500 items shows the
first 500 only. No error appears.

Required change: page both calls with `Request.fetchAllPages` (`app/api/Paging.kt`), or send
`limit=0`. Paging is safer because of the 9 MB message cap. The app also sends `order_by` to
`playlist_tracks`. Neither server version has that parameter. The server ignores unknown
arguments (dev `server/helpers/api.py:219-229`), so remove it as dead code.

### 5.2 Favorites have three states per user

What changed: `MediaItem.favorite` is `bool | None` (`models/media_items/media_item.py:232`).
True is like, False is dislike, None is no choice. The new command
`music/favorites/set_item(item, favorite)` sets the state (dev `server/controllers/music/controller.py:1569`).
`favorites/add_item` sets True. `favorites/remove_item` sets None, not False. The list filter
`favorite=True` returns likes and `favorite=False` returns dislikes.

What the app does: `ServerMediaItem.favorite` is already `Boolean?` (`app/data/model/server/ServerMediaItem.kt:24`).
The app treats every value except true as "not a favorite". `toggleFavorite` in
`app/data/MainDataSource.kt` calls add or remove.

Impact: parsing works on both versions. The app cannot show or set a dislike.

Required change: keep the two-call path for 2.10. On 2.11, call `favorites/set_item` and add a
dislike option to the favorite menu. Gate on the schema or on a feature probe.

Status: done (issue #1187). `supportsDislike` uses `SERVER_2_11_SCHEMA`. On 2.11, each favorite
menu has two pairs of entries, like the web frontend: "Favorite" or "Unfavorite", and "Dislike" or
"Remove dislike". The heart of the full player opens the same menu, and a dislike shows a dislike
icon in the place of the heart. A dislike sends `favorites/set_item` with false. A like and a clear
keep `add_item` and `remove_item`, like the web frontend. On 2.10, the heart stays a toggle, and
the app reads false as "not a favorite". Android Auto and CarPlay keep the like toggle. A Siri
dislike sets a dislike on 2.11 and removes the favorite on 2.10. The app does not offer the
`favorite=False` list filter.

### 5.3 The `favorite_updated` event replaces `favorite` in item events

What changed: the server strips `favorite` from every `media_item_added`, `media_item_updated`
and `media_item_deleted` payload before it sends them (dev `server/controllers/webserver/websocket_client.py`,
near line 640). A new event `favorite_updated` carries
`FavoriteUpdate{uri, media_type, item_id, favorite, user_id}` for the calling user only
(dev `server/controllers/music/media/base.py:1248`; `models/favorite_update.py`).

What the app does: the optimistic now-playing heart reconciles from `MediaItemUpdatedEvent`
(`app/data/MainDataSource.kt:868`). List rows sync through `MediaItemRepository.itemChanges`.

Impact: on 2.11 the reconcile source always carries null. A list row can flip back to "not a
favorite" after a successful like.

Required change: add `favorite_updated` to `app/data/model/server/Enums.kt` and `app/api/Event.kt`.
Reconcile from that event. Never read `favorite` from `media_item_updated` on 2.11.

### 5.4 Sorting moved to the server

What changed: `order_by` is deprecated on every `*/library_items` command (dev
`server/controllers/music/media/base.py:275`). The replacement is `sort_field` and
`sort_direction`. The new command `music/sort_options(listing: ListingType)` returns
`SortOptionInfo{field, supports_direction, default_direction, label_key}` (`controller.py:1115`,
`models/api.py:18`). `SortField` gained `TRACK_NUMBER`, `ALBUM_NAME`, `PROVIDER`,
`FAVORITE_TIMESTAMP` and `ORIGINAL`. Sub-item commands (`album_tracks`, `artist_albums`,
`artist_tracks`, `top_tracks`, `similar_tracks`, `genres/tracks` and others) also gained paging
and sort.

What the app does: `app/data/model/client/SortOption.kt` holds a static table per media type and
sends `order_by`.

Impact: none now, because the server still accepts `order_by`. The app cannot offer the new sort
fields, and it breaks if the server removes `order_by` later.

Required change: on 2.11, read `music/sort_options` once per listing and send `sort_field` and
`sort_direction`. Keep the static table as the 2.10 fallback. The `label_key` needs a local
string, because the app does not vendor server translations.

Status: done for the library lists (issue #1185). `sortWireFor` selects `sort_field` from
`SERVER_2_11_SCHEMA` and `order_by` below it or for an unknown schema. `LibrarySortRepository`
loads `music/sort_options` once per library list and server, for the phone and for Android Auto.
A failed load gives the static table, because every 2.11 library list offers its fields. The app
default stays the list default while the server offers its field, so podcasts stay newest first.
The server default is `sort_name` for every library list. The app
drops `random` and `random_play_count`, because a paged list in a random order repeats items. The
web frontend drops them for the same reason. The app labels an option by its field, and every
`label_key` on dev is equal to its field. The sub-item lists keep the static table and sort on
the client, like the web frontend.

### 5.5 Scopes and custom roles

What changed: the web frontend replaced the admin flag with `authManager.hasScope()`. Custom
roles exist (`auth/roles`, `auth/role/*`). `Scope.CONFIG_PROVIDERS_OWN` is new. `User.role` can
be a custom role id. Scope changes on 2.11: `music/refresh_item` and `music/playlists/update`
need `library.write` instead of `library.manage`.

What the app does: it reads `auth/scopes` for AI Radio only (`app/data/MainDataSource.kt:1629`).
Every other write action is visible for every user.

Impact: a user without `library.write` sees actions that fail with an error toast.

Required change: read `auth/scopes` after login and hide the actions that the user cannot run.
Gate on scopes, never on the role string.

Status: done (issue #1182). `MainDataSource.grantedScopes` holds the scopes of the user, and the
gate fails closed. `library.write` gates the library actions in all menus, the player heart, the
stream favorite, add to playlist, create playlist, the media session and Siri. `config.players.write`
gates the DSP entry and the car DSP action. Grouping has no gate, because
`players/cmd/set_members` needs only `players.control`. Every builtin role and every custom role has
that scope. The app sends no `library.manage` command.

### 5.6 Sendspin spec 1.0.0-rc1

What changed: the Sendspin provider pins `aiosendspin` 10.1.0 instead of 9.1.1
(dev `server/providers/sendspin/manifest.json`). Version 10.0.0 follows spec 1.0.0-rc1. Wire
changes that affect a player client:

- `trust_level` is replaced by `psk_category`.
- `supported_pair_methods` is a map keyed by method. `PairMethodDescriptor` lost `method` and
  `min_pin_length`.
- `supported_commands` moved from `client/hello` to `client/state`.
- `static_delay_ms` is `output_delay_ms`.
- Artwork `media_width` and `media_height` are `width` and `height`. BMP is gone.
- The audio chunk header carries `send_ahead`.
- Noise messages fragment with binary ID 1 and a flags byte.
- `server/state` always carries the full role state.
- `stream/end` lost `server_transmitted`. `server/time` keeps it.
- Pairing uses "pairing code" instead of PIN. A pairing code attempt on a paired client always
  re-pairs it.

Correction: `trust_level` is a `client/hello` field, not a handshake field. Noise message 1
carries `psk_id` on 9.x and `psk_id` plus `psk_category` on 10.x. The client ignores the extra
key, so the 9.x wire connects to a 10.x server as a non-compliant client.

Impact: the server accepts the old wire by default. `allow_legacy_clients` defaults to true and
maps to `allow_noncompliant_clients` and `allow_unencrypted` (dev
`server/providers/sendspin/provider.py`). The server logs the device name and software version
in a noncompliance warning. If an admin turns the toggle off, a 9.x client cannot connect.

Server 2.10.7 also pins `aiosendspin` 10.2.0, the same version as dev on 2026-10-17. Its schema
stays at 65, the same as 2.10.6. So the schema cannot tell the two servers apart. The 2.11 betas
are not the same: 2.11.0b1 to b3 pin 9.1.1, and b4 and b5 pin 10.0.x.

The handshake does not give the server version. Both 9.1.1 and 10.2.0 send `server/init` with
`version` 1. `server/hello` has no version, and it comes after `client/hello`, which is different
on each wire. Only the `psk_category` key in Noise message 1 is different. The app does not select
the wire from the presence of a key.

Status: done (issue #1181). The module speaks both wires (`WireVersion`). `sendspinWireFor` selects
`SPEC_1_0` for a schema of `SERVER_2_11_SCHEMA` or higher. It also selects `SPEC_1_0` for a
`server_version` of 2.10.7 or a later 2.10 release. All other servers get `LEGACY_9`. This
includes the 2.11 betas below `SERVER_2_11_SCHEMA` and a source build, which reports `0.0.0`. The
player sends `required_lead_time_ms` 250 and `min_buffer_ms` 1000. The client parses
`send_ahead` and `pairing.format` but does not use them. The pairing code methods, artwork,
`server/state` and `stream/end` changes do not apply to a player-only client. See
`.claude/architecture.md`, Sendspin Integration, for the full list of differences. The 10.x
details come from the spec and the aiosendspin source.

### 5.7 AI Radio

What changed: `ai_radio/start`, `ai_radio/stop` and `ai_radio/status` are gone. Shows are finite
Radio items with `is_endless=False`. `ai_radio/queue_dj/status` and `set` return
`{queue_id: {host_id, station_id}}`. The station and section list commands need `library.read`
instead of `config.providers.read`.

What the app does: it already uses the new shape (`app/data/model/server/ServerAiRadioStation.kt:37`)
and plays shows through `PlayDispatcher`. The gate is schema 88 and newer.

Impact: parity on 2.11. On 2.10 the feature stays hidden. No change needed.

### 5.8 Authentication responses and PKCE

What changed: `auth/login` and `auth/join_code/exchange` return `AuthFailure{success: false,
error, translation_key}` on failure (dev `server/controllers/webserver/helpers/auth_providers.py:384`).
The `error` text is localized to the connection locale. `auth/authorization_url` accepts
`code_challenge`, `code_challenge_method` and `redirect_target` (dev `server/controllers/webserver/auth.py:1095`).
With `redirect_target="app"` the browser returns to the app, and `auth/exchange(state, code,
code_verifier, device_name)` completes the login (`auth.py:1259`). This needs Remote Access on
the server.

What the app does: `app/data/model/server/AuthClasses.kt:8-11` reads `success` and the optional
`error`. The response is a superset, so parsing works. OAuth on iOS still depends on a buffered
deep-link token.

Impact: none for login. PKCE is an opportunity, not a break.

Required change: none for login. Use PKCE on 2.11 to fix the iOS OAuth return. Keep the current
flow for 2.10.

Status: done (issue #1184). iOS already used `ASWebAuthenticationSession` before this change. Over
Remote Access on 2.11, with a provider that sets `supports_remote_app`, the app sends a PKCE
challenge and `redirect_target` "app". The `app.music-assistant.io` callback page opens
`musicassistant://auth/callback` with `code` and `state`, and the app completes the sign-in with
`auth/exchange`. All other sessions keep the server callback flow. The server does not need an External
URL on the PKCE path. See `.claude/settings-screen.md`.

### 5.9 Other 2.11 changes

- `ServerInfoMessage.unsupported_install` (schema 87). The web shows a banner on its settings
  pages. Status: done (issue #1183). `ServerInfo.unsupportedInstall` reads the flag.
  `UnsupportedInstallCard` shows on the home screen with a Settings button, because the user can
  connect to another server there. The user can dismiss the home card for each server. The
  settings server section shows the card at all times. The flag is true when the server has no official build
  information (dev `music_assistant/mass.py`). It shows the installation method, not the server
  version. Server 2.10 does not send the field, so the app reads false.
- `providers_updated` payloads are filtered per user. Status: done (issue #1186).
  `ProviderRepository` owns the provider list and loads it again on the event. The app does not
  use the payload, because server 2.10 sends the list without the provider filter of the user.
- Playlist visibility changes send a synthesized `media_item_deleted`. The app handles the event,
  so shared playlists disappear correctly.
- `player_queues/play_media` gained `sort_field` and `sort_direction`. The app sends neither
  `sort_by` nor the new fields. No change needed.
- New fields the app can show: `QueueItem.origin`, `Radio.is_endless`,
  `PodcastEpisode.episode_number` and `season`, `Playlist.access`, `PlayLogEntry.seek_position`,
  `AudioMetadata.camelot_key`.
- `PodcastEpisode.episode_number` and `season` (schema 85) and `QueueItem.origin` (schema 90).
  Status: done (issue #1188). An episode subtitle starts with "S2 E5", or with "E5" if the
  episode has no season. The phone, Android Auto and CarPlay show it. The stream details dialog
  shows "Playing from" with the container of `origin`. The text opens the details screen of the
  container if the app has one. A folder shows as text only. An older server does not send the
  fields, so the app shows nothing. These fields need no schema gate.
- `Radio.is_endless` (schema 88) and the finite station tracklist moved to issue #1190. Only
  AI Radio shows are finite. `radio_tracks` returns a new shuffled snapshot on each call, so the
  list does not match the order that plays.
- The frontend 2.11 refuses servers below schema 86. The app has no minimum of its own. The app
  constant `LOCAL_SCHEMA_VERSION` is 65 (`app/data/model/server/ServerInfo.kt:7`), because the app
  was checked against server 2.10.6 and 2.10.7. Both report 65, so they show no dialog. The nightly
  `2.11.0.dev2026101703` reports 96. The `dev` branch reports 97 after server PR #7050, which adds
  counts to the artist lists. Dev now increments the schema for each new command or field that a
  client can check. Set the constant to the schema of the 2.11.0 release, not to a dev value.
- A higher `LOCAL_SCHEMA_VERSION` does not make a 2.10 server unsupported. The app shows a
  warning only if the server schema is above the app constant, or if the app constant is below
  the server `MIN_SCHEMA_VERSION`. Every server version has a `MIN_SCHEMA_VERSION` of 28.

### 5.10 Server 2.10.7

Server 2.10.7 was released on 2026-10-17. It has 78 commits after 2.10.6. These items apply to
the app:

- The schema stays at 65 and the models stay at 1.1.205. The paging, favorite and sort
  behavior is the same as in 2.10.6, so the 2.11 gates stay off. This is correct.
- The Sendspin provider pins `aiosendspin` 10.2.0 (server PR #6716 and the later bumps). See 5.6
  for the wire selection.
- `auth/authorization_url` now checks `return_url` and refuses a URL that is not permitted. The
  pattern list has `musicassistant://`, so the app OAuth flow continues to work.
- Server PR #6849 repairs the Home Assistant sign-in from the mobile app through the External URL
  or Remote Access.

## 6. Adjustments for partial implementations

| Item | What the web does | What the app lacks | Where | Feasibility |
|---|---|---|---|---|
| Genre search | Queries `genres/library_items` with the search text | The genre chip is absent | `app/ui/compose/search/SearchViewModel.kt:76` | Easy |
| Descriptions and biographies | Shows bio, review, description, publisher, narrators | Done. The item page shows them under the header, with the genre chips, the track count and the duration, and the playlist owner | `app/ui/compose/item/ItemMetadata.kt` | Done |
| In progress and recently played rows | Builds the rows from `in_progress_items` and `recently_played_items` | Rows appear only if the server sends them as recommendations | `app/ui/compose/home/HomeScreenViewModel.kt` | Easy |
| Long-lived token login | Paste a token in the login form | Done (issue #1202). "Use a token instead" in the Music Assistant form sends the token with `auth` | `app/ui/compose/auth/AuthenticationPanel.kt` | Done |
| Show info | Info sheet for every item type | Tracks and radios have no screen | `app/ui/compose/item/ItemDetailsScreen.kt` | Easy |
| Players that need setup | Setup warning and setup-only menu | Listed only. Setup is admin work | `app/ui/compose/home/players/` | Decided |
| Retry after a load failure | Retry button in the list | Check each list error state for a retry action | `app/ui/compose/library/` | Easy |
| Event coverage | Reacts to `providers_updated`, `playlog_updated`, sleep timer and queue settings events | Done (#1186). No server sends `queue_settings_updated`, and `player_updated` has the sleep timer | `app/api/Event.kt`, `app/data/repository/ProviderRepository.kt` | Done |
| Localized login errors | Shows the server text | Shows the server text. On 2.11 the text is already localized | `app/auth/AuthenticationManager.kt` | None |
| Android Auto tabs | n/a | No Tracks tab and no Genres tab | `aa/auto/AutoLibrary.kt` | Easy |
| CarPlay docs | n/a | `carplay.md` lists 7 categories. The code offers 7 without Tracks and with AI Radio | `.claude/carplay.md` | Easy |
| Dead deep links | Opens player and DSP settings in the web | `openPlayerSettings` and `openPlayerDspSettings` have no callers | `app/ui/compose/home/HomeScreenViewModel.kt:447-451` | Remove |

## 7. Missing features and feasibility

| Feature | API | Feasibility | Mobile constraint and sketch |
|---|---|---|---|
| Track detail screen | `tracks/get_track`, `track_albums`, `track_versions`, `similar_tracks`, `metadata/get_track_lyrics` | Easy | Done (issue #1189). A `Track` case in `ItemDetailsScreen` with tabs for albums, versions and similar tracks |
| Radio detail screen | `radios/get_radio`, `radio_versions`, `radio_tracks` (2.11) | Easy | Same pattern. Show the tracklist for finite stations on 2.11 |
| Podcast episode screen with transcript | `podcasts/podcast_episode`, `podcast_episode_transcript` (2.11) | Medium | Done (issue 1204). A `PodcastEpisode` case in `ItemDetailsScreen`; the cues feed `LyricsSheet` as `Lyrics.Synced`. No steppers and no hide-fully-played filter |
| Artist appears on, singles, discography | `artist_appears_on`, `discography` (2.11) | Medium | Two more artist sections. Discography items carry provider `musicbrainz`. Show them dimmed and offer "Add to library" only |
| Artist audiobooks row | `artist_audiobooks` | Easy | Done (issue #1201). The author page rows with the existing provider filter |
| Album versions, review, more from artist | `album_versions`, `artist_albums` | Easy | Two sections on the album screen |
| Server-driven sort options | `music/sort_options` (2.11) | Easy | Replace the static table on 2.11. Map `label_key` to local strings |
| Dislike | `favorites/set_item` (2.11) | Easy | Three-state heart menu. Hide on 2.10 |
| Save queue as playlist | `player_queues/save_as_playlist` | Easy | Menu item in the player overflow |
| Playlist rename, image, delete | `playlists/update`, `library/remove_item` | Easy | Needs `library.write`. Hide if the scope is absent |
| Playlist sharing and collaboration | `playlists/set_access`, `config/providers/share_candidates` (2.11) | Medium | A dialog with a user picker. Needs the `Playlist.access` DTO |
| Playlist migration | `migrate_playlist` (2.11) | Medium | The command returns a background task. The app has no task progress UI. Show a toast and let the server finish |
| Playlist import and export | `import_playlist`, `export_playlist` | Medium | Needs a platform file picker and a share sheet. M3U text only |
| Smart playlists | `smart_playlists/*` | Hard | A rule editor is heavy on a phone. Low value. Defer |
| Refresh item, find on music services | `music/refresh_item` | Easy | Long-press action. Scope `library.write` on 2.11, `library.manage` on 2.10 |
| Add radio or track by URL | `builtin/add_radio`, `builtin/add_track` | Easy | A text dialog in the Radio and Tracks lists |
| Multi-select and bulk actions | existing commands | Medium | Compose selection mode on the list screens. Needed for bulk mark played and bulk add to playlist |
| Search source picker and recent searches | `music/search` with `providers`, preferences `search.sources` and `search.recent` | Easy | Chips above the result list. Keep limits at 40 or lower |
| Per-row provider filter on home | preference `discover.hiddenProviders.<row>` | Easy | Edit mode already exists |
| Shortcut add and remove | `auth/user/update` with `sidebar.shortcuts` | Easy | Long-press action. The app already reads the list |
| Played-state badge on home cards | item `fully_played`, `resume_position_ms` | Easy | Overlay icon on the card |
| Author and narrator tabs | `artists/library_artist_types` | Easy | Done (issue #1201). A row in the Audiobooks filter sheet |
| Genre content-type tabs | `genres/library_items` filters | Easy | Done (issue #1201). A row in the Genres filter sheet |
| Hide fully played episodes | `podcast_episodes(fully_played=False)` on 2.11, client filter on 2.10 | Easy | Done (issue #1201). Toggle on the episodes tab |
| Play and Shuffle on a browse folder | `play_media` | Easy | Header buttons in `BrowseScreen` |
| User profile and tokens | `auth/me`, `auth/user/update`, `auth/tokens`, `auth/token/create`, `auth/token/revoke` | Medium | A profile screen in Settings. This also closes the long-lived token gap |
| Join code login | `auth/join_code/exchange` | Easy | One more login tab |
| PKCE OAuth return to the app | `auth/authorization_url`, `auth/exchange` (2.11) | Medium | Needs Remote Access on the server. Fixes the iOS OAuth return. Keep the current flow for 2.10 |
| Scope-gated UI | `auth/scopes` | Medium | Cross-cutting. One `Scopes` value object in `MainDataSource` and a check in each action resolver |
| Waveform on the seek bar | `audio_analysis/wave_form` | Medium | Draw with Compose `Canvas`. Check the payload size first |
| Cast to dashboard | `dashboard/dashboards`, `sessions`, `show`, `hide`, `get_url` | Easy | A dialog in the player overflow. Scope `users.invite` |
| Unsupported install banner | `server_info.unsupported_install` (2.11) | Easy | Dismissible card on the home screen |
| "Played from" container | `QueueItem.origin` (2.11) | Easy | Link in the stream details dialog |
| Episode and season numbers | `PodcastEpisode.episode_number`, `season` (2.11) | Easy | Subtitle on episode rows |
| Grid size slider | none | Easy | Setting per media type |
| Volume slider mode and haptics | none | Easy | Low value |
| Track table with BPM and key | `AudioMetadata.bpm`, `camelot_key` | Easy | Low value on a phone |
| User-customizable detail rows | preferences `artist.rows` and others | Easy | Low value. The app has fixed tabs |
| Autoplay locked by repeat indicator | queue state | Easy | Disable the autoplay badge while repeat is on |
| Visualizer | none | Not viable | Butterchurn needs WebGL and Web Audio. The local player exposes no PCM tap to the UI |
| Party mode, music quiz, listen-in | `party/*`, `music_quiz/*` | Hard | Guest web flows with their own sessions. Defer |
| Member self-service music sources | `config/providers/*` with `config.providers.own` (2.11) | Hard | Needs the config-entry and setup-flow UI. Treat as admin work |
| In-car search on CarPlay | n/a | Not viable | CarPlay audio apps have no search template. Siri covers it |

## 8. Out of scope

Admin features that the web has and the app does not need:

- provider setup and configuration, member self-service sources
- player configuration, the DSP editor, group player creation, queue settings pages
- core settings, storage (network shares and local folders)
- user and role administration
- metadata tools, genre management
- task list, diagnostics, audio analysis administration
- remote access configuration
- AI Radio show and host editing

Web-only features with no meaning on mobile: keyboard shortcuts, PWA install, page title,
Home Assistant panel history, onboarding, expert mode, and the guided tour.

## 9. Unverified items

- The aiosendspin 10.x wire is known from the spec and the package source. The 9.1.1 and 10.2.0
  wheels were compared for the handshake only. No device test was done on the 1.0 wire.
- The semantics of a provider-side `favorite=False` sync are not verified.
- The schema bumps 66 to 96 have no per-version changelog in the server tree. Only
  `unsupported_install` is annotated (schema 87).
- The web limits for search were read from the dev code. The 2.10 values were not compared.
- Each list error state in the app was not checked for a retry action.

## 10. Suggested order of work

1. Page `playlist_tracks` and `podcast_episodes` with `fetchAllPages`. Remove the dead
   `order_by` argument. Safe on 2.10.
2. Handle `favorite_updated`. Stop reading `favorite` from `media_item_updated` on 2.11.
3. Read `auth/scopes` once and gate the write actions.
4. Bump `LOCAL_SCHEMA_VERSION` to the 2.11.0 release schema. Add the unsupported install banner.
5. Decide the Sendspin wire strategy for 2.11 and schedule the module update.
6. Quick wins from section 7: track and radio screens, save queue as playlist, queue
   actions, dislike, playlist rename and delete, refresh item, genre search.
7. Server-driven sort options on 2.11.
8. Medium items by user demand: episode page with transcript, artist discography, profile and
   tokens, PKCE OAuth, playlist sharing, multi-select, waveform.
