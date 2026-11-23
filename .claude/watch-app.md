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

Signing follows the iOS project: `MA_TEAM_ID`, `MA_BUNDLE_ID` (the iOS app, the companion) and
`MA_WATCH_BUNDLE_ID` override the defaults, as environment variables or in a git-ignored
`watchApp/Configuration/Local.xcconfig`. The watch identifier must start with the iOS identifier,
and both apps need the same team, or WatchConnectivity does not pair them.

## Server configuration

The iPhone app sends the server and its own token through WatchConnectivity. The watch has no
sign-in. `WatchConfigPublisher` (composeApp, `watchlink/`) sends on each sign-in and on a
sign-out or a rejected token; `WatchConnectivityLink` (iosMain) writes it as the application
context, with the keys of `WatchLinkContract`. `PhoneConfigReceiver.swift` reads the context
on activation and on each change, and `PlayerStore` hands it to
`WatchPlayerController.configure` / `setRemoteOnly` / `clearServer`. A change reconnects a
running session to the new server.

The watch shares the phone token: a sign-out on the phone signs the watch out
(`NOT_CONFIGURED`), and the server lists one session for both. The system keeps the last
application context on the watch, so the watch starts without the phone. That context is the
only copy of the token on the watch. A phone that reaches the server through WebRTC only sends
`remote_only`, and the watch says so (`REMOTE_ONLY`), because it cannot speak WebRTC. A client
certificate (mTLS) is not sent.

The watch app has the iOS app as its companion (`WKCompanionAppBundleIdentifier`,
`WKRunsIndependentlyOfCompanionApp`). The iOS project does not embed the watch target yet, so
install both from Xcode. For App Store delivery, add the watch target to the iOS app (Embed
Watch Content).

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

A `NavigationStack` whose root is two pages of a `.verticalPage` `TabView` — the controls,
where the watch starts, and the cover on its own above them — with the queue and the volume one
push away, and the output picker one step past the volume. The same shape as the Wear client,
and each pushed screen dismissed by the system swipe. The queue is **not** one of the pages: it
is a push, so it covers them and their indicator rather than sitting beside them.

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
pointed at is readable without opening anything — and the queue and the output picker are
navigation-bar items either side of it (`.topBarLeading`, `.topBarTrailing`), so it reads
between them and the whole of the glass below belongs to the track and the transport. They are
bare glyphs rather than the filled or outlined disc the transport buttons carry: a bar item on
watchOS is a glyph, and a disc in a bar thirty-odd points tall reads as a button that did not
fit. The clock stays in its corner — that corner is the system's and not an app's to lay out. The
output button carries the AirPlay glyph, as on iOS: picking where the sound comes out is the
same question, and it is the symbol a user already reads as "output".

Previous, play/pause and next sit in one row, the play button larger because it is the one the
user reaches for without looking; they are dimmed rather than hidden when the player cannot
skip. All three are bare glyphs — the discs behind them went, and the accent colour the play
disc carried moved onto its glyph so that button is still the one the eye lands on. Skip is
`backward.end` / `forward.end` rather than the `.fill` pair: one triangle against an end bar,
which is the glyph the Wear client draws, taken as SF Symbols so it keeps the optical sizing
and the Always On treatment every other glyph gets. The played fraction is two capsules in a
`GeometryReader` at 2 pt rather than `ProgressView(.linear)`, whose height watchOS picks and
sizes for a list row.

**The column sits at the bottom of the glass**, and getting it there took measuring rather than
guessing. It is a plain `VStack` with a leading `Spacer`, `.frame(maxHeight: .infinity,
alignment: .bottom)` and `.ignoresSafeArea(.container, edges: .bottom)`, and each of those is
load-bearing:

- There used to be a `ScrollView` with `.scrollDisabled(true)`, so that the content was inset
  below watchOS's *large* navigation title. That title is gone — a bar with toolbar items in it
  is an inline one — and the scroll view was actively in the way: it proposes no height to its
  content, so neither a spacer nor `maxHeight` nor `containerRelativeFrame` had anything to
  take, and the column stayed hung under the bar whatever it was asked to do.
- Even then the column only reached ~39 pt short of the glass, because the navigation container
  keeps that strip as bottom safe area. A border drawn round the frame is what showed it. The
  column claims the strip and gives back `bottomInset` (8 pt) to stay off the curve.
- `ControlMetrics.transportLift` then pulls the transport row up under the timeline. The space
  there is not stack spacing but the slack inside the buttons' own frames — tap targets a good
  deal taller than their glyphs — so negative padding is what closes it, and the targets keep
  their size.

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

**The album page** (`AlbumArtView`) is the cover and nothing else. Unlike the one behind the
controls it is neither blurred nor darkened — there is nothing left on top of it that has to
stay readable — and only the title and the artist remain, at the bottom, carrying a shadow
rather than a scrim so the cover keeps the glass to itself. The cover is also the play button:
nothing is drawn there and nowhere on it could a tap mean anything else. It sets no navigation
title, so the time is the only other thing on the screen.

**Double tap.** The one-handed primary action (`handGestureShortcut(.primaryAction)`, the
double tap of Series 9, Ultra 2 and newer) plays and pauses on both pages: the play button
carries it on the now-playing page, and the cover carries it on the album page. The gesture
reaches only a `Button` or a `Toggle`, so the cover is a `Button` with
`UnhighlightedButtonStyle` rather than a tap gesture. `primaryActionShortcut()`
(`PrimaryActionShortcut.swift`) applies it from watchOS 11 and does nothing on watchOS 10.
Give it to one control per page.

Each page sets its own `containerBackground`, and watchOS crossfades them — which is what turns
the blurred cover behind the controls into the clear one, with no shared image to animate. That
is the one place this diverges from the Wear client, where the cover is drawn once behind both
pages and the blur and scrim are faded off it as the pager moves.

**Queue rows carry their covers** (`QueueArtwork`), fetched at row size through
`ArtworkLoader.thumbnail`, which decodes down with ImageIO rather than handing back a full
cover for the GPU to shrink — a megabyte a row on a watch — and keeps its own LRU by the
screenful, with a miss kept as a nil so a track the server has no cover for is not fetched
again every time its row scrolls back. A music note stands in while the fetch is in flight and
stays when there is nothing to show, so rows keep one height and the list does not reflow under
the thumb.

## Always On

`@Environment(\.isLuminanceReduced)` is the signal, and the now-playing screen takes out of
itself everything a dimmed screen must not do: no cover art (a full-screen image is the worst
thing an OLED can hold up for an hour), no ticking progress, and outlined buttons instead of
filled ones — a ring of lit pixels rather than a disc of them. Every size stays as it was, so
dimming and waking never reflow what the user was looking at.

**The screen is not navigated away from on the way into Always On**, which is where this
diverges from the Wear client: Wear pops back to the now-playing root, because its ambient
screen can outlive the interaction by hours. watchOS keeps the user's own screen visible and
hands it straight back on the wake, and moving them somewhere else would be the surprise. That
includes which page is up.

The album page is the **one** place the cover is held through Always On, on both clients: that
page is nothing but the cover, so taking it away would leave an empty screen rather than a
quieter one. Everything above about a full-screen image on an OLED still applies — it is a
deliberate trade for a page the user has chosen to sit on.

The session is held through Always On and torn down only on `.background`: an always-on screen
that froze on whatever was playing when the wrist dropped would be telling the user a lie.
Which of `.active` and `.inactive` watchOS reports while dimmed is not worth depending on, so
both keep the session.

Burn-in is the system's to handle. watchOS shifts the display itself; what it asks of an app
is to avoid solid bright areas, and the dimmed layout does that whatever the panel reports.

## Not implemented

Browsing the library, search, favourites, shuffle and repeat, queue reordering, complications,
Smart Stack widgets, and the Now Playing media controls. The watch is a remote for what is
already playing. It gets the server from the iOS app, then it talks to Music Assistant itself.

## Strings

English only, inline in the SwiftUI sources as `LocalizedStringKey`s. Lokalise covers
`composeApp/src/commonMain/composeResources` only, so neither watch client is translated;
`LOCALIZATION_PREFERS_STRING_CATALOGS` is on, so a String Catalog can pick them up the day
someone wires it up.
