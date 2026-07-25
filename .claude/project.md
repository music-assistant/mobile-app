# Music Assistant KMP Client

Cross-platform music player client for [Music Assistant Server](https://github.com/music-assistant/server).
Built with Kotlin Multiplatform + Compose Multiplatform for Android and iOS.

## Quick Commands

```bash
# Android
./gradlew :androidApp:assembleDebug
./gradlew :androidApp:installDebug

# iOS - open in Xcode
open iosApp/iosApp.xcodeproj

# Tests
./gradlew :androidApp:testDebug
```

## Features

- Queue management and playback control for Music Assistant players
- Library browsing (Artists, Albums, Tracks, Playlists)
- Authentication: login/pass, OAuth, long-lived access token
- Remote access via WebRTC (planned - see `.claude/webrtc-implementation-plan.md`)
- Built-in player with Sendspin protocol support
- Local music playback (platform-specific)
- Android Auto / CarPlay support

## Architecture

@import .claude/architecture.md
@import .claude/project-structure.md
@import .claude/dependencies.md
@import .claude/guidelines.md
@import .claude/testing.md

## UI Documentation

- **Settings Screen**: See `.claude/settings-screen.md` for complete documentation on server connection, authentication flows, and local player configuration
- **CarPlay & Siri**: See `.claude/carplay.md` for CarPlay architecture, template constraints, and the Siri media-domain integration (`INPlayMediaIntent` donation, `INUpdateMediaAffinityIntent`, `INSearchForMediaIntent`)

## Planned Features

- **WebRTC Remote Access**: See `.claude/webrtc-implementation-plan.md` for comprehensive implementation plan, protocol details, and architecture design
- **Android TV / Google TV**: Not yet supported upstream — no leanback launcher, camera is a
  hard-required manifest feature (blocks the TV Play Store listing), and no shared Compose
  screen has D-pad/keyboard focus handling. A first pass (manifest eligibility, TV landscape
  lock, QR-scan camera gating, player-switcher D-pad focus fix) lives on branch
  `claude/music-assistant-android-tv-95iu6l`. Two things confirmed by running it on a real
  Google TV emulator, worth knowing before touching this area again:
  - **Manifest merger gotcha**: `ktor-client-webrtc` bundles its own
    `<uses-feature android:name="android.hardware.camera"/>` with no `required` attribute
    (defaults to `true`). The manifest merger takes the most restrictive value across all
    merged manifests, so the library's implicit `required="true"` silently wins over the
    app's own `required="false"` unless the app's declaration adds
    `tools:node="replace"`. Always check the *merged* manifest output when changing
    `<uses-feature>`/`<uses-permission>` — a library dependency can override it invisibly.
  - **D-pad focus fix is incomplete**: initial focus landing on the player-switcher dialog
    works, but D-pad row-to-row navigation within the list is still broken, and it's an open
    question whether the player switcher (a touch-oriented drag-to-reorder list) is the right
    UI for TV at all vs. a simpler TV-specific picker.

## Upstream & Repository Relationship

This repo is a fork/downstream of the official **`music-assistant/mobile-app`** — that is the
correct upstream to file issues/PRs against, not `music-assistant/server` (that's the
separate backend project this app talks to; easy to conflate the two by name). When working
from a Claude Code session already scoped to this fork, note that a GitHub-scoped session
generally can't attach a repo from a *different* owner (e.g. `music-assistant/mobile-app`)
alongside it — cross-owner repo attachment is typically blocked once a session already holds
a repo from another owner. Interacting with the upstream org's issues/PRs directly needs a
separate session seeded with `music-assistant/mobile-app` from the start.

## Contribution Hygiene — read before opening any PR

Claude-authored process/decision documentation must never appear in a PR diff — upstream
(`music-assistant/mobile-app`) **or** against this repo's own `main`. Maintainers reviewing a
PR want the code change, not a narrated history of how an AI agent worked through it. This
covers, at minimum:

- Any `docs/ANDROID-TV.md`-style working note (branch-local scratch doc, not real project
  docs — drop the file/commit entirely before the PR).
- The session-added content in this file: the *Android TV / Google TV* bullet above and this
  whole *Upstream & Repository Relationship* / *Contribution Hygiene* section.
- All of `.claude/testing.md` (added this session).
- The *Manifest gotcha* note under WebRTC KMP in `.claude/dependencies.md` (added this
  session).

These are genuinely useful to keep on this fork/branch for future Claude Code sessions —
just strip them (don't cherry-pick the commits that only touch these docs, or manually drop
the added hunks) when assembling a PR branch, whether that PR targets upstream or this repo's
own `main`.