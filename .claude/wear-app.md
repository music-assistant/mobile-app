# Wear OS App (`:wearApp`)

A standalone watch remote for Music Assistant: play/pause, next, previous, volume, pick a
player or player group, and work the queue of the selected player.

## Build and run

```bash
./gradlew :wearApp:installDebug      # to a connected watch or Wear OS emulator
./gradlew :wearApp:testDebugUnitTest
./gradlew :wearApp:detekt
```

## Server configuration

The phone app sends the server and its own token through the Wearable Data Layer. The watch
has no sign-in. `WatchConfigPublisher` (composeApp, `watchlink/`) sends on each sign-in and on
a sign-out or a rejected token. A reconnect or a background teardown says nothing about the
token, so it sends nothing. `WearDataLayerLink` (androidMain) puts the data item
`/ma/server-config` with the keys of `WatchLinkContract`, which this module imports from
`:composeApp`, so the two sides cannot drift. `PhoneLinkStore` reads the stored item when the
app starts and follows its changes; `MaSession` reconnects when the server changes.

The watch shares the phone token: a sign-out on the phone signs the watch out
(`SessionState.NotConfigured`), and the server lists one session for both. The Data Layer
keeps the item on the watch, so the watch starts without the phone, and the item is the only
copy of the token on the watch. A phone that reaches the server through WebRTC only sends
`remote_only` (`SessionState.RemoteOnly`), because the watch cannot speak WebRTC. A client
certificate (mTLS) is not sent.

The Data Layer delivers the item only to an app with the same application id **and the same
signing key** as the phone app. Debug builds share `~/.android/debug.keystore`. The release
`selfSigned` config of this module also uses the debug key, so a release phone app signed with
a different key sends nothing to it.

To test on an emulator, pair a phone emulator and a Wear emulator (Android Studio, Device
Manager → Pair Wearable), install both debug builds, and sign in on the phone.

`minSdk` is 33 (Wear OS 4). `applicationId` is shared with the phone app, because Play pairs
a watch APK to its phone app by application id; the version code carries a +1000 offset so
the two never collide in one release.

## Why it does not reuse `:composeApp`

`:composeApp` is one Compose Multiplatform module holding the phone's whole UI. Depending on
it would pull Compose Multiplatform Material3, Navigation3, WebRTC, Sendspin, Coil and the
extended icon pack into the watch APK, and its Koin graph assumes a phone (media session,
microphone, foreground services, artwork disk cache). The watch needs eight commands and
four event types, so `:wearApp` speaks the protocol itself. From `:composeApp` it takes only the vector icons
and `WatchLinkContract`, the wire format of the phone link.

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
- **`data/ArtworkLoader`** — fetches and decodes covers behind two LRUs. `load` is the cover
  behind the screen: one fetch produces the display bitmap and, from a 64 px thumbnail decode,
  the ARGB seed for the colour scheme, so the quantizer never walks a full-size image on a watch
  CPU. `loadThumbnail` is the cover on a queue row: decoded small, never scored for a colour,
  and cached by the screenful rather than one at a time, with a miss cached as a null so a track
  the server has no cover for is not re-fetched every time its row scrolls back.

  **Not Coil.** `:composeApp` ships no Coil network artifact — its Coil is handed bytes by an
  `ArtworkRepository` this module does not have — so a URL given straight to `AsyncImage` here
  resolves to nothing at all, quietly. The server's imageproxy needs no token, so a plain Ktor
  `get` is the whole of what a cover takes.
- **`ui/WearPlayerViewModel`** — combines the repository flows into one `WearUiState` and
  keeps the current cover loaded.
- **`ui/`** — `NowPlayingPager` (root: the controls and the cover art, as two pages), with
  `VolumeScreen` pushed on top of it and `OutputScreen` on top of the volume screen, each
  dismissed by the system swipe. The queue is not a route and not a page: `QueueSheet` is a
  sheet over the root (see **Queue sheet**).

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
output button, and the queue sheet's handle along the bottom edge. `AppBadge` draws the launcher icon the way the system
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

## The album page

`NowPlayingPager` (`ui/NowPlayingPager.kt`) makes the root two pages of a `VerticalPager`: the
controls at `NOW_PLAYING_PAGE`, where the watch starts, and the cover art on its own at
`ALBUM_PAGE` above it — so a swipe down takes the controls off the bottom and leaves the cover.
Two dots ride the right rim (`VerticalPagerScaffold`'s own `VerticalPageIndicator`), the
cover's above the controls', which is the direction the swipe goes.

**The cover is drawn behind the pager, not inside a page.** The pages slide and it does not, so
the swipe uncovers it rather than carrying a second copy of the same image in from the top.
`ArtworkBackground` takes a `veil` — 1 behind the controls, 0 on the album page — which is the
blur and the scrim it only ever had to keep text readable. The scrim's alpha is read in the
draw phase so the swipe costs no recomposition; the blur has to be a modifier value, so it is
quantised to tenths of a dp.

`albumFraction()` is that 0-to-1 position, and the queue sheet reads it too: its handle belongs
to the controls, so `swipeAwayPx` carries the whole sheet down with them. The sheet is not a
page — it covers the pager and its dots when it is up — so it has to be carried by hand.

`AlbumArtPage` draws nothing but the track title and artist, at the bottom and out of the way
of the art, fading in with `albumFraction` as the page is pulled down — read in the draw phase
through `graphicsLayer`, so it costs no recomposition either. **It names the track, not the
album**: `NowPlaying` carries no album title.

The cover is the play button. Nothing is drawn on that page and nowhere on it could a tap mean
anything else, so the whole of it is `clickable` — with `indication = null`, because a
full-screen ripple over a photograph reads as a flash rather than as a press, and with the same
`performHapticFeedback` tick the rotary volume uses in its place. The tap is a click and not a
drag, so the pager still gets the swipe.

Ambient keeps the cover **only** on the album page, which is the one page that is nothing else;
the controls page still goes dark behind them, because a full-screen image is the one thing an
OLED watch should not hold up for an hour. Entering ambient does not touch the pager, so a
wrist that drops on the cover keeps it.

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

Wear Compose's own `rememberAmbientModeManager` does the same job and would retire this
dependency, and it was tried: on an API 37 emulator it held the screen through the dim, past
the 60 s eviction, and back on the wake. It is not used, because `AmbientModeManagerImpl`
calls `com.google.wear.Sdk.getWearManager` with no version guard and no catch. That needs a
watch new enough to carry the Wear SDK, and this module's `minSdk` is 33, so an older watch
would not lose the always-on screen gracefully — it would crash inside `setContent`. Revisit
when `minSdk` moves.
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

**Ambient always shows the player.** The wrist drops wherever the user left it, and what then
stays lit for hours should be the track, not a volume stepper or a list nobody can scroll. So
entering ambient pops the back stack to the now-playing root (`MaWearApp`), and leaving
ambient leaves the user there — the other screens are a step away and re-opening one is
cheaper than guessing when to restore it. The `LaunchedEffect` that does it sits in
`MaWearApp`'s own body, the scope the activity recomposes; inside a destination it would
never see the state change, for the reason above.

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

## Queue sheet

The queue is `QueueSheet` (`ui/QueueSheet.kt`), a sheet over the now-playing screen rather
than a route on the nav host, and there is no button for it: its handle is the line along the
bottom of the now-playing screen, and pulling that up is how the queue is opened. Wear Compose
Material3 has no sheet component, so the sheet is built on `AnchoredDraggableState` with two
anchors — `Expanded` over the whole screen and `Hidden` all but `QUEUE_HANDLE_REACH` plus the
clock band below it — and placed with `Modifier.offset`, so what is behind it stays visible
above it while it moves.

**Nothing the list can be touched through reaches the rim button of the screen behind it**,
and that is a hit-testing requirement rather than a look. `TransformingLazyColumn` installs a
`pointerInput` over the whole of its bounds whether or not `userScrollEnabled` is set
(`isTransformingLazyColumnClickableThresholdEnabled`, on by default), so any of its bounds
lying over that button swallows the taps meant for it. Two things keep them apart:

- The band the clock needs is `Modifier.padding(top = …)` **on** the column rather than content
  padding **inside** it, so it falls outside the column's bounds. While the sheet is down that
  band is what lies over the button, and it is now dead space in the sheet rather than in the
  list — and the sheet itself (`offset`, `drawBehind`, `nestedScroll`) and `ScreenScaffold`
  (plain boxes and `Modifier.overscroll`) are not pointer targets, so taps fall through.
- `ActionRow` passes `RIM_INSET + QUEUE_HANDLE_REACH` as its `rimInset`, which leaves about
  9 dp between the button and the top of the list at every screen size the app sizes itself
  for.

Three constants, each with one job. `QUEUE_HANDLE_HEIGHT` is the handle itself — a drag target
before it is a line, hence taller than the four pixels it draws. `QUEUE_HANDLE_MARGIN` is how
far it keeps off the bottom of the glass while the sheet is down, and it lives in where the
sheet parks (`QUEUE_HANDLE_REACH`, which is the two of them and is what the sheet shows at rest
and what `ActionRow` clears). `QUEUE_HANDLE_GAP` is how far the queue's first row keeps below
the handle while the sheet is up, and it lives in the list's `contentPadding.top`
(`clockBand + QUEUE_HANDLE_HEIGHT + QUEUE_HANDLE_GAP`) and in how far the handle rides above
that row. One is measured against the bottom of the glass and the other against a row of text,
so they are not the same number; folded into one, tightening the gap pushed the handle into the
bezel.

That content padding is **inside** the column rather than on it: the
column's bounds reach the top of the sheet, so a row that scrolls out of them has reached the
top of the screen instead of ending in a band of sheet above it. Bounds that reach the top of
the sheet would lie over the rim button while the sheet is down, which is why the list is
composed only while the sheet is off the bottom.

Three things keep the first frame still, and every one of them was a visible jump at startup
before it was there:

- **The sheet's anchors** are set from the measured height inside a `Modifier.layout`, in the
  measure pass, so the first placement already has them. A guess from `Configuration`'s rounded
  `screenHeightDp` is a pixel or two out, and the sheet jumped to its anchor on the frame after
  it first drew.
- **The list's first row** is pinned with `firstLayoutItemProvider`: item 0, `ItemEdge.Start`,
  offset `contentTop`. `TransformingLazyColumn` lays out from the item nearest the middle of its
  viewport, and on its first pass it has no layout to read that from and starts the anchor at
  `maxHeight / 2` — so the queue appeared halfway down the sheet and snapped up on the pass
  after. The list is composed only while the sheet is off the bottom, so that first pass happens
  on every open, not just at startup. The pin is on whenever the sheet is aimed anywhere but the
  top (`targetValue != Expanded`), which is deliberately the **first** frame of a close and not
  its last: re-laying the list out once the sheet has arrived moves it under a sheet that has
  already stopped. Aimed at the top, the provider hands back `centerItem` and the column resumes
  its own anchoring. This is also what returns the queue to its top when the sheet is put away —
  there is no explicit `scrollToItem`, and one at startup was a second source of the jump.
- **The clock** moves with the queue, but only once the queue has arrived. `ScreenScaffold`
  registers its list with the `AppScaffold`, which is what scrolls `TimeText` away by it and
  what shows and hides the scroll indicator — and that registration is gated on
  `LocalScreenIsActive`, which the sheet provides as **settled at the expanded anchor**, not
  merely lifted. Left ungated, the clock is driven by a queue parked off the bottom of the
  display, which is what shifted it on the first layout before any of it had ever been seen.
  Gated on the lift instead, the clock is handed a list that is mid-gesture: `ScrollAway`
  hides it outright — instantly, no animation — for an anchor offset it cannot read or that is
  past `maxScrollOut`, and gives it back only once the screen stage leaves `Scrolling`, so the
  clock was sometimes already gone by the time the sheet arrived.

The sheet is never taken out of the composition, because its handle is always on screen.
`QueueSheetState`, held by `MaWearApp`, is only the anchor it is aimed at; whatever a gesture
settles it at is written back through `settledValue`, so `open()` and `close()` always have
something true to change. A sheet that has settled back down is scrolled home as well: the
handle is the first row, and a queue left scrolled would peek one of its tracks out of the
bottom of the screen instead of the handle.

The background is drawn at the fraction the sheet is up (`liftedFraction`, read in the draw
phase so following a finger costs no recomposition), so at rest the cover art shows through
the strip and nothing but the handle is drawn over the now-playing screen. While the sheet is
down the list takes no input at all (`userScrollEnabled`, and the crown's `ScrollableState`
consumes nothing), so the strip cannot steal the touches that belong to the screen behind it.

The rows are drawn as one chain: `chainShape` gives a row the Wear button's own corner where
the chain ends and `CHAIN_JOIN` where it meets its neighbour, so the first row is round on top
and square underneath, the last the other way about, and everything between is square at both
ends. A one-row queue is round all round, since it is both ends at once. `CHAIN_GAP` is the
column's `verticalArrangement`, and is what keeps the links reading as links rather than as one
surface.

Three gestures move it:

- **The handle** sits over the list rather than in it, carrying `Modifier.anchoredDraggable`.
  It still goes up with the queue — `Modifier.offset` puts it `QUEUE_HANDLE_REACH` above the
  first row, so the row's own scroll offset carries it off the top — but where it lands is that
  offset and nothing else, with no lazy column's idea of which item to lay out from involved on
  any pass. It was a list row first, and every startup and open/close jump in this sheet came
  from that.
- **A drag down** past the top of the list reaches the sheet through a `NestedScrollConnection`:
  downwards it takes what the list has already refused (`onPostScroll`), upwards it takes the
  gesture first (`onPreScroll`), so a sheet hanging part way down is pulled back before the
  queue scrolls under it.
- **The crown** scrolls the list and then keeps going into the sheet. Rotary never goes
  through nested scroll, so the hand-off is a composite `ScrollableState` that feeds
  `TransformingLazyColumnState` first and spends the rest of a backwards turn on the sheet's
  offset; the column's own `rotaryScrollableBehavior` is turned off for it. A turn is one
  scroll for as long as the crown moves, so the end of it is where a sheet left between
  anchors is settled.

Settling after a gesture the list could not use goes through `settleAt`, not through
`AnchoredDraggableState.settle(velocity)`: that overload belongs to the constructor that takes
its own positional and velocity thresholds, and **throws** on the single-argument one used
here. `settleAt` reads the velocity itself — past 125 dp/s the sheet goes the way it was
thrown, below it `settle(animationSpec)` takes it to the anchor it is nearest.

Entering ambient closes the sheet, for the same reason it pops back to the root. The system
back gesture closes it too, but only while it is open (`BackHandler(enabled = isOpen)`): the
nav host lets back through at the start destination, where it is how the user leaves the app.

`RimRow` on the now-playing screen takes a `rimInset`, and `ActionRow` passes
`RIM_INSET + QUEUE_HANDLE_HEIGHT`, so the one rim button there rides a circle that clears the
handle and the two never share a tap target.

## Strings

`wearApp/src/main/res/values/strings.xml` is an ordinary Android resource file and is **not**
synced by Lokalise — that pipeline covers `composeApp/src/commonMain/composeResources` only.
The watch ships English until someone wires it up.

## Not implemented

Browsing the library, search, favourites, shuffle and repeat, queue reordering, complications
and tiles, and the phone-paired sign-in. The watch is a remote for what is already playing.
