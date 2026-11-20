# Player sources plan

Status: built on 2026-11-20 on the branch `player-sources`, as one change. See "Player sources" in `architecture.md`.

Not built:

- The iOS lock screen commands (`NowPlayingCoordinator.swift`).
- The skip actions of the Android media session. Only its seek action follows the source.
- A Robolectric test that selects a source.
Written on 2026-11-20. Written to ASD-STE100 rules.

## Read this first

- The server facts in this plan come from the source of two server versions. They are a start point, not the truth.
- Read the server source again before you write code. The source code of the server dev branch changes each week.
- This plan needs the device audio share (branch `source`, commit `6c4f686e`). That branch adds `Player.liveInputUri`, which this plan replaces.
- If the branch `source` is not in `main`, merge it first or change Part 2.

Versions that this plan used:

| Repository | Version |
|---|---|
| Server stable | tag 2.10.12, commit `6ab5bfa` |
| Server dev | commit `9b0ab9b`, 2026-11-20 |
| Models | 1.1.205 on stable, 1.1.248 on dev |
| Web frontend | 2.17.376, commit `b00f1e5` |

## Goal

A player can play a source that is not its Music Assistant queue.
Examples are a TV input, a line-in, Spotify Connect, AirPlay and the device audio share.
The app reads only `active_source` today, so it has two faults:

- The user cannot select a source of a player.
- While a source plays, the app does not know its name or its controls. It offers Stop only.

The plan has three parts. Each part is one pull request.

1. Model: read `source_list`.
2. Source state: show the active source and enable the correct controls.
3. Picker: let the user select a source.

Part 2 has the most value, because it changes what all users see during Spotify Connect or AirPlay.
Part 3 helps only the owners of players with inputs.

## Server facts

### The source list

Each player has `source_list`, a list of `PlayerSource`. On 2.10.12 the server puts these entries in the list:

- The Music Assistant queue. Its `id` is the player id. Its flags tell what the queue can do now.
- The live source that plays on the player. Its `id` is the source uri.
- The sources that a plugin binds to the player, and the inputs of the device. Each `id` is a uri.
- The inputs that the player provider reports. Each `id` is a provider value, not a uri.

A player of type `protocol` gets only the provider inputs.

Fields of `PlayerSource`:

| Field | Meaning |
|---|---|
| `id`, `name` | The identity and the label. |
| `passive` | The user cannot select the source. |
| `can_play_pause`, `can_seek`, `can_next_previous` | The controls that the source accepts. |
| `can_shuffle`, `can_repeat`, `shuffle_enabled`, `repeat_mode`, `account_id` | Not in scope. |

All fields but `id` and `name` have a default, so give each field a default in the client model.

### Commands

- `players/cmd/select_source` takes `player_id` and `source`. A null `source`, or the player id, selects the queue again.
- The server removes the player from its group or sync before it selects a source that is not a queue.
- `players/cmd/pause`, `play`, `seek`, `next` and `previous` go to the active source. The server rejects a command that the source flags do not permit.
- The app sends these `players/cmd` commands already. Check this in `PlayerRequestFactory` before Part 2.

### Two forms of a live source

A live source shows in one of two forms. The app must handle the two forms.

- The source is the `active_source` of the player, and the player has no active queue.
- The source is an `AudioSource` item in the queue. The item has its own `can_play_pause` flag. `PlayerData.canPauseContent` reads it already.

### Players with provider inputs

On the dev branch these providers report inputs: AmpliPi, Bluesound, Bose SoundTouch, HEOS, MusicCast, Raumfeld, Samsung WAM, Sonos, Sonos S1 and WiiM.

## Web frontend rules

Copy these rules. Do not invent new rules.

- Active source (`activeSource.ts`): the entry whose `id` is `active_source`, or the player id if `active_source` is null.
- External source (`externalSource.ts`): the active source, if the player has no resolved queue and `active_source` is not the player id.
- Play and pause (`usePlayPauseCommand.ts`): an `AudioSource` queue item decides first. Then the active source decides with `can_play_pause`.
- Restart: a stopped source that is not passive and not the queue accepts Play, because the server selects it again.
- Stop button: it replaces Pause for an `AudioSource` item that cannot pause, for a radio stream, and for an active source that cannot pause.
- Next and previous: enabled by `can_next_previous` of the active source.
- Seek: enabled by `can_seek` of the active source, and only if the current media has a duration.
- Picker (`player_menu_items.ts`): the entries that are not passive, and the active entry. The menu shows only if the list has more than one entry and the player is not synced. The active entry is checked and disabled. The list is sorted by name.

## Part 1: model

- `data/model/server/ServerPlayer.kt`: add `ServerPlayerSource` and `sourceList: List<ServerPlayerSource> = emptyList()`. Replace the comment block that has the old field list.
- `data/model/client/`: add `PlayerSource(id, name, passive, canPlayPause, canSeek, canNextPrevious)`.
- `Player`: add `sources: List<PlayerSource>` and keep the raw `activeSource` id.
- `Player.externalSource: PlayerSource?`: the entry whose `id` is `activeSource`, if that id is not the player id.
- `PlayerFactory`: map the list.

Tests: a mapping test for each entry type, for a missing list, and for a missing flag.

## Part 2: source state

Replace `Player.liveInputUri` with `Player.externalSource`.

- `MainDataSource.mergePlayerDataList`: drop the old queue while `externalSource` is not null.
- `PlayerData.canPauseContent`: for an external source, use `canPlayPause`.
- `PlayerData.transportState` (`PlayerControls.kt`): for an external source, enable previous and next from `canNextPrevious`. Do not use the queue index.
- Timeline: for an external source, enable seek from `canSeek` and the duration of `currentMedia`.
- Player page: show the source `name` where the queue title shows. The artwork and the titles come from `currentMedia`, as they do now.
- `DeviceAudioShareUi`: compare `externalSource?.id` with the share uri.
- Android media session (`MediaSessionHelper`): give the same flags to the session actions.
- iOS (`NowPlayingCoordinator.swift`, lines 546 to 549): enable the commands from the same flags, through `KmpHelper`.
- Android Auto and CarPlay: use the same `transportState`. Do not add a second rule.

Put the rule in one place. `transportState` and `mainTransport` are that place, and each surface reads them.

Fallback for an old server: if `activeSource` is a uri and the list has no entry for it, make an entry with no controls.
That keeps the behavior of today, which is Stop only. Remove the fallback if all supported servers send the entry.

Tests: extend `PlayerDataCanPauseContentTest` and `MergePlayerDataListTest`. Add a `transportState` test for each flag. Keep `DeviceAudioShareUiTest` green.

## Part 3: picker

- Player menu of the expanded player: add "Select source". Use the picker rule of the web frontend.
- Send `Request.Player.selectSource(playerId, source.id)`. The request exists on the branch `source`.
- Do not send it through `PlayDispatcher`, because it is not a `play_media` request.
- No scope gate, because each role has `players.control`.
- Hide the menu entry for the local player, and for the target of a device audio share.
- Strings go in the English `strings.xml` only.
- Phone only. The car surfaces get no picker.

Tests: the list rule (passive, active, one entry, synced), and one Robolectric test that selects a source.

## Not in scope

- Shuffle and repeat of a source (`can_shuffle`, `can_repeat`).
- `account_id`.
- The protocol label of the web frontend ("Spotify • AirPlay").
- A source picker in Android Auto or CarPlay.

## Open points

Check each point before the part that needs it.

1. Which server version first sends the live source entry and the queue entry. This plan checked 2.10.12 only.
2. If `source_list` of a sync child or of a group member has the entry of its leader. The `externalSource` rule assumes that a leader id is not in the list of the child.
3. If the app sends all transport commands as `players/cmd`. `APICommands.kt` has no queue command for pause, next or seek, but nobody traced the call.
4. What the app shows today during a Spotify Connect session. Nobody tested it on a device.
5. If a select of a source on a grouped player needs a warning, because the server removes the player from its group.
6. If `current_media` of a provider input has a title. A TV input can have none, and then the page needs the source name as the title.
7. If the dev branch changed the fields or the command when this work starts.

## Verification

1. `./gradlew :composeApp:testAndroidHostTest`
2. `./gradlew :androidApp:testDebugUnitTest`. Run it alone, not in one Gradle call with step 1.
3. `CI=true ./gradlew detektAll`
4. Device, with a player that has inputs, or with the demo player provider of the server:
   - Select an input, and make sure that the page shows its name.
   - Select the queue again, and make sure that the queue comes back.
   - Start Spotify Connect or AirPlay on a player, and make sure that the controls agree with the web frontend.
   - Start a device audio share, and make sure that the target still shows the share artwork and Stop.
   - Do the same checks from the notification, from Android Auto and from the iOS lock screen.

Docs: add a "Player sources" entry to `.claude/architecture.md`, and change the "Input sources" entry, which says that the client does not show player inputs.
