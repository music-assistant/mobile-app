# watchOS App (`watchApp` + `:watchShared`)

A standalone Apple Watch remote for Music Assistant: play/pause, next, previous, volume, pick
a player or player group, hand playback to another one, and work the queue of the selected
player. It is the watchOS counterpart of the Wear OS client (`.claude/wear-app.md`) and offers
the same set of actions.

## The two halves

| Part | What it is |
|------|------------|
| `:watchShared` | A Kotlin Multiplatform library: the session, the repository, the models. Builds the `MaWatchKit` framework for `watchosArm64` (arm64_32), `watchosDeviceArm64` (arm64) and `watchosSimulatorArm64`. |
| `watchApp/watchApp.xcodeproj` | The SwiftUI watch app. It renders `MaWatchKit` and owns nothing else. |

This is the same split as `:composeApp` / `iosApp`, and the reason is the same as the Wear OS
module's: the watch speaks the protocol itself rather than depending on `:composeApp`, whose
Koin graph assumes a phone. The cost is the same too — **the protocol is duplicated a third
time**. `watchShared/.../data/ApiCommands.kt` names its counterparts for exactly that reason;
when the server renames a command, an argument or an event, the phone, the Wear app and the
watch all have to follow.

## Build and run

```bash
./gradlew :watchShared:watchosSimulatorArm64Test   # the shared half's tests
./gradlew :watchShared:detekt

open watchApp/watchApp.xcodeproj                   # then run the "watchApp" scheme
```

From the command line:

```bash
xcodebuild -project watchApp/watchApp.xcodeproj -scheme watchApp \
  -destination 'generic/platform=watchOS Simulator' build
```

The Xcode target's first build phase runs `:watchShared:embedAndSignAppleFrameworkForXcode`,
guarded by a source hash so a no-op rebuild does not pay for a cold Gradle daemon. It needs
`JAVA_HOME`, like the iOS project's equivalent phase.

`EXCLUDED_ARCHS[sdk=watchsimulator*] = x86_64` in `watchApp/Configuration/Config.xcconfig`:
`:watchShared` builds no Intel simulator slice, as `:composeApp` builds none for iOS, and
without the exclusion Xcode asks for one and the framework step stops.

Signing follows the iOS project: `MA_TEAM_ID` and `MA_WATCH_BUNDLE_ID` override the defaults,
as environment variables or in a git-ignored `watchApp/Configuration/Local.xcconfig`.

## Server configuration

The same `local.properties` placeholder as the Wear app, at the repository root:

```
ma.host=192.168.1.10
ma.port=8095
ma.tls=false
ma.token=<long-lived access token>
```

Kotlin Multiplatform has no `BuildConfig`, so `:watchShared:generateWatchBuildConfig` writes
the four values into a generated `WatchBuildConfig` object that `WatchServerConfig.fromBuildConfig()`
wraps. A missing token leaves the watch in `WatchSessionStatus.NOT_CONFIGURED`, which the
screen reports instead of retrying. This is the placeholder for the real sign-in: replacing it
means changing how `WatchServerConfig` is built, and nothing else.

**Plain `http` reaches an IP address, an unqualified name and a `.local` name, and nothing
else.** App Transport Security exempts exactly those three, which is why neither this app nor
the iOS app carries an ATS exception. A fully-qualified domain name needs `ma.tls=true`.

## Layers

The Kotlin half is a port of the Wear module's, and the comments there apply here:

- **`data/MaSession`** — one WebSocket session: the `server/hello` handshake, the `auth`
  round-trip, request correlation by `message_id`, and the event stream. Reconnects are
  capped-exponential; an authentication rejection is terminal, a timeout or a dropped socket
  is not. Unlike the Wear module it frames its own JSON rather than installing a WebSocket
  content converter — one `Frame.Text` is one JSON object, and owning the parse keeps a
  malformed frame from tearing down the session. `NSURLSession` carries it, through Ktor's
  Darwin engine, which pings: a half-open link over a Bluetooth-proxied connection can sit
  there for minutes before the OS notices.
- **`data/MaRepository`** — the single source of truth: players, queues, the selected player
  and its queue rows. Snapshots come from `players/all` and `player_queues/all` on every
  completed handshake and stay current through events. Commands are **optimistic**: the local
  model changes at once and the next server event for that player overwrites it wholesale.
- **`WatchPlayerController`** — the one object Swift holds. SwiftUI cannot collect a Kotlin
  `Flow`, so `observe` takes a callback, delivers on the main thread and hands back a
  `WatchSubscription` to cancel. `WatchSessionStatus` is an enum rather than the sealed
  hierarchy the Wear app uses, because a Kotlin sealed interface crosses into Swift as a set
  of classes that only an `is` cascade can read.
- **`PlayerStore`** (Swift) — an `@Observable` box around the controller, plus the position
  anchor (below). Everything else in `watchApp/` is a view.

There is no Koin and no DI container on either side: the graph is four long-lived objects.

## Position

`queue_time_updated` arrives once a second for the playing queue and is the whole position
model: every report re-anchors the bar, and nothing reads the server's wall clock, which a
watch's own clock cannot be trusted against. Between reports `PlayerStore.position(at:)`
carries the position forward from `Date` measured against the anchor it stamps when a report
lands, and a `TimelineView(.periodic(by: 0.5))` redraws the bar. A track with no known length
— a radio stream — gets no fraction at all, and the bar keeps its space without drawing a
played arc.

## The screens

A `NavigationStack` with the now-playing screen at its root, the queue and the volume one push
away, and the output picker one step past the volume — the same shape as the Wear client, and
each screen dismissed by the system swipe.

**Now playing.** The cover art is the background rather than a tile, blurred under a gradient,
which is how watchOS puts a single subject on a screen this small: the labels and the controls
then have the whole glass. It is laid over a plain `Color.black` rather than stacked with one,
and that is load-bearing: a `ZStack` takes its size from its children, and an image under
`scaledToFill` reports back whatever it was proposed, which left the background an inset
rectangle with bare screen below it. A colour is flexible and claims the whole container, the
cover fills that, and `ignoresSafeArea` on the background — not on the column — runs it to the
bezel while the controls stay inside the glass. That is also the whole of the artwork theming — unlike the Wear
client, nothing is quantized into a colour scheme, because the album is read straight off the
background and watchOS expects controls in the app's accent colour. The player name is the navigation title, so what the watch is
pointed at is readable without opening anything. Previous, play/pause and next sit in one row,
the play button larger because it is the one the user reaches for without looking; they are
dimmed rather than hidden when the player cannot skip.

The queue and the output picker sit in a last row of the column, under the transport. **Not in
a `.toolbar`**, though that is where watchOS would normally want them: a bottom toolbar is drawn
in the navigation container rather than in the screen, so it stands still while everything
around it slides away on a push. Laid out in the column the two buttons belong to this screen
and travel with it. The output button carries the AirPlay glyph, as on iOS — picking where the
sound comes out is the same question, and it is the symbol a user already reads as "output".

The screen does not scroll, and the way it does not is deliberate: it is a `ScrollView` with
`.scrollDisabled(true)`. Only a scrolling container gets its content inset below watchOS's
large navigation title, and only a disabled one cannot then ride up over that title — which is
exactly what this screen did while it could scroll.

Everything therefore has to fit, which is what `ControlMetrics` is for. It picks one of two
size sets at 180 pt of screen width, between the 41 mm (176 pt) and 45 mm (198 pt) families,
the way `controlSizes()` does on Wear. The compact set also drops the elapsed/remaining line
under the bar: of the things on that screen, a pair of timestamps is the one the bar itself
already says, and Apple's own Now Playing drops them on the same watches. Measured content
heights are 84.5 pt on a 40 mm and 109 pt on a 46 mm, before the button row. Checked on both
simulators.

**The track title is one line on every watch.** Not only because a second line is 18 pt a
40 mm column does not have, but because allowing one anywhere would make the height of the
screen depend on the track: the transport row would shift under the user's thumb as one song
followed another. A long title scales down first and only then truncates.

**Volume.** The Digital Crown is the control — on a watch it is the fastest way to a volume —
with buttons either side for the same step of 5 over the server's 0..100, and the player name
at the bottom opening the output picker. That pairs the two halves of one question, how loud
and out of what. A player that reports no volume, or a session that is down, disables the
slider but not the output button: picking a different player is exactly what such a user wants
next. The crown binding also moves when a volume arrives from anywhere else, and `command(_:)`
compares against the last level this screen sent so that a server report does not echo back as
a new command.

**Output.** The selectable players — reachable and not hidden, the same set the phone app
offers. The current one is tinted and says "Current" in words rather than carrying a tick
alone: on a watch, colour is read before a glyph is. Picking a player pops back to the volume
screen, so the slider there is at once the new player's. A swipe moves what is playing to that
player instead of just pointing the watch at it.

**Queue.** A window around the current track rather than the whole queue — a full page is a
large message for a device on a Bluetooth-proxied link. A tap plays a row; a swipe removes it,
optimistically, and the server's `queue_items_updated` reloads the window and settles any
disagreement. No row artwork, as on Wear: a hundred thumbnails over that link buy nothing.

A player whose `active_source` is an external source — Spotify Connect, say — has no Music
Assistant queue, and the screen correctly says so. That is the server's model, not a gap here.

## Always On

`@Environment(\.isLuminanceReduced)` is the signal, and the now-playing screen takes out of
itself everything a dimmed screen must not do: no cover art (a full-screen image is the worst
thing an OLED can hold up for an hour), no ticking progress, and outlined buttons instead of
filled ones — a ring of lit pixels rather than a disc of them. Every size stays as it was, so
dimming and waking never reflow what the user was looking at.

**The screen is not navigated away from on the way into Always On**, which is where this
diverges from the Wear client: Wear pops back to the now-playing root, because its ambient
screen can outlive the interaction by hours. watchOS keeps the user's own screen visible and
hands it straight back on the wake, and moving them somewhere else would be the surprise.

The session is held through Always On and torn down only on `.background`: an always-on screen
that froze on whatever was playing when the wrist dropped would be telling the user a lie.
Which of `.active` and `.inactive` watchOS reports while dimmed is not worth depending on, so
both keep the session.

Burn-in is the system's to handle. watchOS shifts the display itself; what it asks of an app
is to avoid solid bright areas, and the dimmed layout does that whatever the panel reports.

## Not implemented

Browsing the library, search, favourites, shuffle and repeat, queue reordering, complications,
Smart Stack widgets, the Now Playing media controls, and the phone-paired sign-in. The watch
is a remote for what is already playing. There is no companion relationship with the iOS app:
`WKWatchOnly` is set, and the watch talks to Music Assistant itself.

## Strings

English only, inline in the SwiftUI sources as `LocalizedStringKey`s. Lokalise covers
`composeApp/src/commonMain/composeResources` only, so neither watch client is translated;
`LOCALIZATION_PREFERS_STRING_CATALOGS` is on, so a String Catalog can pick them up the day
someone wires it up.
