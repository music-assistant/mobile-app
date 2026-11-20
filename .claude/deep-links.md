# Deep Links, App Links and Universal Links

This document is the developer reference for the links that open the app. The URL contract for users is in
`docs/app-documentation/deep-links.md`. Update both documents when the contract changes.

## Link Types

| Link | Handler | Purpose |
|---|---|---|
| `musicassistant://auth/callback?code=…&state=…` | `AuthManager.handleOAuthCallbackUrl` | OAuth sign-in callback. Refer to `.claude/settings-screen.md`. |
| `musicassistant://app/<route>` | `DeepLinkBus.handle` | Page and play links (custom scheme). |
| `https://www.music-assistant.io/app/<route>` | `DeepLinkBus.handle` | Page and play links (Android App Link, iOS Universal Link). |
| Google App Actions `actions.intent.PLAY_MEDIA` | `VoicePlayDispatchActivity` | Voice playback on Android. Refer to `docs/ANDROID-AUTO.md`, section 4. |

## Flow

1. The platform gives the URL to one entry point:
   - Android: `MainActivity.handleIncomingUri` (`androidApp/`), for a new intent and a cold start.
   - iOS: `handleIncomingURL` (`iosApp/iosApp/iOSApp.swift`). `.onOpenURL` gets the custom scheme.
     `.onContinueUserActivity(NSUserActivityTypeBrowsingWeb)` gets a Universal Link. Before `KmpState.isReady`,
     the URL waits in `PendingURL`.
2. The entry point gives the URL to `handleOAuthCallbackUrl` first. If that function does not take the URL, the
   entry point gives it to `DeepLinkBus.handle`.
3. `DeepLinkBus` (`composeApp/.../api/DeepLinkBus.kt`) parses the URL into a `DeepLinkDestination`. It ignores a
   URL that it does not know. It does not check the host, because the OS filters the domain.
4. `MainNavRoot` applies the destination only while the session is `Authenticated`. Then it calls
   `DeepLinkBus.consume`.

`DeepLinkBus` keeps the last destination in a `StateFlow`, not in a one-shot channel. During a cold start,
`MainNavigationRoot` is composed, removed and composed again while the session state resolves. A channel gives
the link to the first instance, and that instance is removed. The retained value survives, and the instance that
stays on screen applies it once.

## Routes

| Route | `DeepLinkDestination` | Action in `MainNavRoot` |
|---|---|---|
| none, `home` | `Home` | Selects back stack 0 (`MainNav.Landing`) and resets it. |
| `library` | `Library(null)` | Selects and resets back stack 0, collapses the players pager, and opens the drawer. The wide layout has no drawer, because its rail shows the sidebar. |
| `library/<category>` | `Library(mediaType)` | Also pushes `MainNav.LibraryList(mediaType)` on back stack 0. |
| `search` | `Search` | Selects back stack 1 (`MainNav.Search`) and resets it. |

`MainNavRoot` has two back stacks only, `Landing` and `Search`. The library has no page of its own, because the
sidebar holds the library categories. `MainNavRoot` gives its `DrawerState` to `AdaptiveNavigationBarLayout`.
| `players[?player=]` | `Players` | Expands the players pager. `HomeScreenViewModel.selectPlayerByIdOrName` selects the player. |
| `play?uri=[&player=][&option=]` | `Play` | `HomeScreenViewModel.playDeepLink`. Does not expand the pager. |

`LIBRARY_ROUTES` in `DeepLinkBus` maps the category names. It is an explicit table, so a change to a UI enum does
not change a public URL. A known route with an invalid argument (an unknown category, a play link without a media
URI, an unknown `option`) is ignored, and the app does not go to a different page.

## Player Lookup

- `findByIdOrName` uses the selectable players only. It compares without case, and an id match wins over a name
  match.
- `findPlayTarget` also takes `local` (`DeepLinkBus.LOCAL_PLAYER`) for the built-in player, and null for the
  selected player. The play link and the players link (`selectPlayerByIdOrName`) both use it.
- The lookup waits up to `DEEP_LINK_PLAYER_TIMEOUT` (10 s) for the player list. A failed play link goes to
  `deepLinkPlayFailures`, and `MainNavRoot` shows a toast (`deep_link_no_player_selected` or
  `deep_link_player_not_found`).
- A play request goes through `PlayDispatcher`, like every other `play_media`.

## Copy Play Link

`DeepLinkBus.playLink` makes the shareable `https` link. `copyPlayLinkMenuOption` (`CopyPlayLinkMenu.kt`) puts it on
the clipboard. The item page menu gives a link without a player. The expanded player menu (`PlayersPager`) gives
the current item and the player id, or `local` for the built-in player.

## Domain Association

The app claims `https://www.music-assistant.io/app` and `/app/*` only.

- Android: the `autoVerify` intent filter in `androidApp/src/main/AndroidManifest.xml`. The path scope keeps the
  rest of the site in the browser. `assetlinks.json` cannot set paths.
- iOS: `applinks:www.music-assistant.io` in `CarPlay.entitlements` and `NoCarPlay.entitlements`. The
  `apple-app-site-association` file limits the paths to `/app/*`.
- The staging copies of the two association files are in `.well-known/`. Refer to `.well-known/README.md` for the
  hosting rules.

Do not add the apex domain `music-assistant.io`. The apex redirects to `www`, and Android and iOS do not follow a
redirect when they get the association file. Before Android 12, one host that fails makes all hosts of the
intent filter fail.

## Add a Route

1. Add a `DeepLinkDestination` subtype and its parse branch in `DeepLinkBus.handle`.
2. Add the action in the `when` block of `MainNavRoot`.
3. Add test cases to `DeepLinkBusTest`.
4. Update the comment on the `musicassistant://app` intent filter in the manifest.
5. Update this document and `docs/app-documentation/deep-links.md`.

## Test

Do these steps on a device that is signed in to a server.

```bash
adb shell am start -a android.intent.action.VIEW -d "musicassistant://app/library/albums"
```

```bash
xcrun simctl openurl booted "musicassistant://app/library/albums"
```

To check the Android App Link verification:

```bash
adb shell pm get-app-links io.music_assistant.client
```

The unit tests are `DeepLinkBusTest`, `FindPlayTargetTest` and `FindByIdOrNameTest` in `composeApp/src/commonTest`.
