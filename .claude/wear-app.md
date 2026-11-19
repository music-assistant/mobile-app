# Wear OS App (`:wearApp`)

A standalone watch remote for Music Assistant: play/pause, next, previous, volume, pick a
player or player group, and work the queue of the selected player.

## Build and run

```bash
./gradlew :wearApp:installDebug      # to a connected watch or Wear OS emulator
./gradlew :wearApp:testDebugUnitTest
./gradlew :wearApp:detekt
```

Configure the server in `local.properties` at the repository root (git-ignored):

```
ma.host=192.168.1.10
ma.port=8095
ma.tls=false
ma.token=<long-lived access token>
```

Gradle reads them into `BuildConfig`, which `WearServerConfig.fromBuildConfig()` wraps. A
missing token leaves the watch in `SessionState.NotConfigured`, which the screen reports
instead of retrying. This is the placeholder for the real sign-in: replacing it means
changing how `WearServerConfig` is built, and nothing else.

`minSdk` is 33 (Wear OS 4). `applicationId` is shared with the phone app, because Play pairs
a watch APK to its phone app by application id; the version code carries a +1000 offset so
the two never collide in one release.

## Why it does not reuse `:composeApp`

`:composeApp` is one Compose Multiplatform module holding the phone's whole UI. Depending on
it would pull Compose Multiplatform Material3, Navigation3, WebRTC, Sendspin, Coil and the
extended icon pack into the watch APK, and its Koin graph assumes a phone (media session,
microphone, foreground services, artwork disk cache). The watch needs eight commands and
four event types, so `:wearApp` speaks the protocol itself and depends on no other module.

**The cost is duplication.** When the server renames a command, an argument or an event, the
watch does not follow the phone automatically. `data/APICommands.kt` names its counterpart in
`io.music_assistant.client.api.APICommands` for exactly this reason. The wire shapes that
matter are: `players/cmd/<play_pause|next|previous>`, `players/cmd/volume_set`,
`players/cmd/group_volume`, `player_queues/items` (`queue_id`, `limit`, `offset`),
`player_queues/play_index` (`index` takes a `queue_item_id`), and
`player_queues/delete_item` (`item_id_or_index`).

## Layers

- **`data/MaSession`** — one WebSocket session: the `server/hello` handshake, the `auth`
  round-trip with the token, request correlation by `message_id`, and the event stream. The
  session lives with the activity's visible lifetime (`MainActivity.onStart`/`onStop`): a
  watch screen is on for seconds, and a socket behind a dark screen costs battery for state
  nobody reads. Reconnects are capped-exponential. An authentication rejection (`RpcError`)
  is terminal; a timeout or a dropped socket is not, because neither says anything about the
  token.
- **`data/MaRepository`** — the single source of truth: players, queues, the selected player
  and its queue rows. Snapshots come from `players/all` and `player_queues/all` on every
  completed handshake and stay current through events. Commands are **optimistic**: the local
  model changes at once and the next server event for that player overwrites it wholesale, so
  a refused command corrects itself with no extra bookkeeping.
- **`data/ArtworkLoader`** — fetches and decodes one cover at a time behind a small LRU. The
  same fetch produces the display bitmap and, from a 64 px thumbnail decode, the ARGB seed for
  the colour scheme, so the quantizer never walks a full-size image on a watch CPU.
- **`ui/WearPlayerViewModel`** — combines the repository flows into one `WearUiState` and
  keeps the current cover loaded.
- **`ui/`** — `NowPlayingScreen` (root), with `VolumeScreen` and `QueueScreen` pushed on top
  of it and `OutputScreen` on top of the volume screen, each dismissed by the system swipe.

There is no Koin: `WearApplication` builds four long-lived objects by hand, and a container
would be more machinery than the app has moving parts.

## Position

`queue_time_updated` arrives once a second for the playing queue and is the whole position
model: every report re-anchors the position, and nothing reads the server's wall clock
(`elapsed_time_last_updated`), which a watch's own clock cannot be trusted against. Between
reports `rememberLiveProgress` (`ui/LiveProgress.kt`) carries the position forward from the
watch's own monotonic clock, measured from the last report, so the ring keeps moving under a
playing track and through a burst of missed events. It ticks twice a second, and only while
the track plays and has a known length. The progress ring around the play button is that
position over the current item's duration.

## The now-playing screen

The app icon then the title, the artist beneath, then previous / play-pause / next, then the
queue button and the output button. `AppBadge` draws the launcher icon the way the system
would — the foreground over `ic_launcher_background`, masked to a circle — and only in front
of a real track title, never in front of "Connecting…" or "Nothing playing". The foreground
drawable keeps its glyph inside the adaptive icon's safe zone, so the badge scales it up
rather than showing a near-empty circle. `PlayPauseButton` draws the position as a scalloped ring around the play button
(`ui/PlayPauseButton.kt`): the one thing that moves stays next to the one thing the user
presses, and the screen edge is left to the system. A track with no known length — a radio
stream — draws the ring without the played arc.

The output button opens `VolumeScreen`, and the output picker sits one step behind it.

A title too wide for its line scrolls (`Modifier.scrollingTitle`, `basicMarquee` repeating
for as long as the screen is up — three passes and then a truncated rest reads as broken).
Only the title moves: the badge is its sibling in the row, and the marquee measures the text
unbounded and clips it to the width the layout gave. A short title keeps its natural width
(`weight(1f, fill = false)`), so badge and title stay centred as a pair; a long one fills the
line and the badge sits at its left. The always-on screen never scrolls — it is meant to be
still — and falls back to the ellipsis.

`Modifier.roundScreenPadding` is what the line scrolls inside. `SAFE_MARGIN` is a rectangular
margin, and near the top of a circle the glass is much narrower than the rectangle: on a
240 dp round screen the title sits about 49 dp down, where the glass spans 23 dp to 217 dp, so
a flat 24 dp margin runs the text into the bezel. The modifier measures where the line landed
and insets it to the chord at its furthest edge, keeping `RIM_INSET` of air, never less than
`SAFE_MARGIN`. Side padding cannot move a one-line row up or down, so the measurement does not
chase itself; a square screen keeps the flat margin.

`controlSizes()` (`ui/ControlSizes.kt`) picks one of two size sets at 225 dp of screen width,
the same threshold Wear's own components use, and the column reserves a top inset for the
clock. Both were checked on a 240 dp and a 192 dp round screen.

The transport row is as wide as the play button's position ring, not as its button — 164 dp
at the small sizes, 202 dp at the large ones — so the column gives it the whole screen width
and the labels carry the text margin themselves. If a screen is narrower still,
`ControlSizes.fitTo` scales every transport size, the gap included, by one factor. A `Row`
hands its children space in source order, so a row that does not fit otherwise takes the
whole shortfall out of its last child, and the next button draws as a pill beside a round
previous button: an icon button is a 50% rounded corner, which is a circle only on a square.
No screen in circulation reaches the scaling, and `ControlSizesTest` holds that.

## Volume and output

`VolumeScreen` is Wear's full-screen `Stepper`: louder at the top, quieter at the bottom, the
level drawn up the left rim by `StepperLevelIndicator`, in steps of 5 over the server's
0..100. The middle slot is the one filled control on the screen — the selected player's name,
which opens `OutputScreen`. That pairs the two halves of one question, how loud and out of
what, without a second control to find. A player that reports no volume, or a session that is
down, disables the stepper but not the output button: an empty screen where a control used to
be reads as a bug, and picking a different player is exactly what such a user wants next.

The stepper is inset top and bottom by `ScreenScaffoldDefaults.contentPadding`'s top, which is
Wear's own figure for the room the clock needs and tracks the screen size. Without it the
increase button sits under the time. Both ends take the same inset so the output button stays
on the centre line. The level indicator is not inset: it is read off the rim, and the clock is
at the top of the screen rather than at its left.

The bezel or crown moves the volume too (`Modifier.rotaryVolume`), which on a watch that has
one is the fastest way to a volume. It handles `onRotaryScrollEvent` itself rather than going
through `rotaryScrollable`: that modifier animates each event through a `ScrollableState`, and
a `MutatorMutex` cancels the animation still in flight, so a fast turn loses most of its
detents — measured at 2 steps out of 5 on the emulator, against 5 of 5 here. Rotary arrives as
a scroll distance, positive for the clockwise turn that carries a list forward and a volume up,
so the distance is banked and spent one step at a time, keeping the remainder. One step is
`ViewConfiguration.scaledVerticalScrollFactor`, which is exactly what one detent of a rotating
bezel reports, so a detent is a step and a crown's finer stream takes a proportional part of
one. Each step ticks (`CLOCK_TICK`), as the watch's own steppers do. The step is taken from the
last value the screen commanded rather than from the composed one, because a turn can deliver
several events inside a frame and all of them would otherwise start from the same value; that
is dropped as soon as a volume arrives from anywhere else. Focus comes from
`rememberActiveFocusRequester`, so the screen gets rotary only while it is the one on top —
pushing `OutputScreen` hands it to that list.

`OutputScreen` lists the selectable players — reachable and not hidden, the same set the phone
app offers — under "Select audio output". The current one is filled (`filledVariantButtonColors`)
with "Current" as its second line rather than ticked: on a watch, colour is read before a
glyph is, and the second line says in words what the colour means. Every other row is
`filledTonal` and carries what the player is playing, or how many members a group holds.
Picking a player pops back to the volume screen, so the stepper is at once the new player's.

## The always-on screen

`AmbientState` (`ui/AmbientState.kt`) is what the platform told us through
`AmbientLifecycleObserver`, passed through rather than interpreted at the edge: for now, only
whether the panel is low-bit.
`MainActivity` registers the observer — that registration is what opts the app into the
always-on screen at all — and holds the state in a `mutableStateOf` that `setContent` reads.
The server session is held against ambient rather than against `onStop` alone — which
lifecycle callbacks a watch fires on the way into ambient is not worth relying on — and
released on whichever of the two ends last, so the always-on screen keeps telling the truth
about what is playing instead of freezing on whatever was playing when the wrist dropped.

`LocalAmbientState` carries it to the screens, and that is not a style choice: the screens
sit under a `SwipeDismissableNavHost`, whose destinations hold the content lambda they were
built with. A value passed as an argument into a `composable { }` block does not reach the
screen when it changes — measured on a Wear emulator, the ambient state reached `MaWearApp`
and stopped there, and the always-on layout never drew. A composition local is read by the
screen itself, so the screen is what gets invalidated. Do not pass ambient state into a
destination as an argument.

The ambient screen is `NowPlayingScreen` with everything an always-on screen must not do
taken out of it: no cover art (a full-screen image is the worst thing an OLED can hold up for
an hour, and its crossfade would animate a screen meant to be still), no position ring, no
`rememberLiveProgress` ticker, and outlined buttons instead of filled ones — the
rim buttons included, which stay put even though touch does not reach the app while dimmed:
a gap where a control was reads as the app having lost something, and an outline is a ring of
lit pixels rather than a disc of them. Every size stays as it was and the ring
keeps its space as a `Spacer`, so dimming and waking never reflow what the user was looking
at. A low-bit panel also drops the artwork-seeded colour scheme, which it has too few colours
to render as anything but dithering.

**Burn-in is the system's to handle, not ours.** On a panel that reports
`burnInProtectionRequired` the platform shifts the display itself, and
[the always-on guide](https://developer.android.com/training/wearables/apps/always-on) says in
as many words not to shift UI in the app: two shifts move the content twice as far, which is
both visible and liable to push something past the clip. So the flag is not carried on
`AmbientState` at all. What it asks of an app is only to avoid solid bright areas and to keep
anything that matters at least 10 px clear of the rim so the system's own shift cannot clip
it — and the always-on layout does both whatever the panel reports: outlines rather than
fills, no artwork, `RIM_INSET` (6 dp) on everything that touches the rim and `SAFE_MARGIN`
(24 dp) on the text. An earlier version walked a 4 dp square per ambient update; it was wrong
and is gone, along with the ambient update count that drove it. `onUpdateAmbient` is not taken
either: nothing in the always-on layout goes stale between updates.

**Staying on screen past the first minute.** Wear's `AmbientTaskStackManager` starts a
60 000 ms timer on an ambient task whose log line reads `no-ongoing`, then moves it to the
back of the stack and brings the watch face forward. The platform's answer is an Ongoing
Activity, so `OngoingPlayback` (`data/OngoingPlayback.kt`) publishes one — the playing chip —
and the task's log line becomes `ongoing`, no timer is created, and the always-on screen
stays. Measured on a Wear emulator: evicted at exactly 60 s without it, still on screen at
95 s with it.

The chip follows playback, not the screen: a notification posted purely to hold the display,
with nothing happening behind it, is what the chip exists to stop. So the always-on screen
lasts past a minute **only while something is playing**; paused, Wear takes the screen back
after its minute, which is the right trade for a remote. It goes when the app leaves the
watch's screen, together with the session — without the session the track behind the chip
stops updating, and a chip naming a track that changed ten minutes ago is worse than none.

Posting it needs `POST_NOTIFICATIONS`, which is a runtime permission on the Android 13 and
newer that every Wear OS 4+ watch runs. It is asked for once on first start, and a refusal
costs the ambient screen past its first minute and nothing else — `OngoingPlayback` checks the
permission and simply does not post, rather than letting the post fail silently.

The other screens — volume, output, queue — are left as they are in ambient. They are static,
unanimated lists with no artwork behind them, and the player is the screen that is actually
worth looking at with the wrist down.

## Colour from the cover

`ui/WearColorScheme.artworkColorScheme` seeds a `SchemeTonalSpot` from the cover's scored
colour and reads each role at the tone the Wear Material 3 tokens prescribe (`ColorTokens` in
`androidx.wear.compose.material3`): primary at 90, its dim variant at 80, containers at 30,
surfaces off the neutral palette. That is what makes the buttons belong to the album rather
than merely be tinted by it. Error roles keep the Wear defaults — a red that follows the
album art stops reading as an error. The scheme is always dark; a watch media screen over
cover art has no light mode. With no cover (a silent group, say) the default Wear scheme
applies.

## Queue window

The watch loads a 100-item window starting five rows before the current index, not the whole
queue: a 500-item page is a large message for a device on a Bluetooth-proxied link. A tap
sends `play_index`; a full swipe removes the row, optimistically, and the server's
`queue_items_updated` reloads the window and settles any disagreement.

## Strings

`wearApp/src/main/res/values/strings.xml` is an ordinary Android resource file and is **not**
synced by Lokalise — that pipeline covers `composeApp/src/commonMain/composeResources` only.
The watch ships English until someone wires it up.

## Not implemented

Browsing the library, search, favourites, shuffle and repeat, queue reordering, complications
and tiles, and the phone-paired sign-in. The watch is a remote for what is already playing.
