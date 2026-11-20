# Deep Links

A deep link opens a page of the app or plays a media item. You can use a deep link in a home screen shortcut, a
Home Assistant automation, an NFC tag, a QR code or a message.

## Link Forms

The app accepts two forms of each link. The two forms do the same thing.

| Form | Example |
|---|---|
| Custom scheme | `musicassistant://app/library/albums` |
| Web link | `https://www.music-assistant.io/app/library/albums` |

Use the web link to share a link with a different person. If the app is installed, the web link opens the app. If
the app is not installed, the web link opens the website.

Use the `www` host. The app does not claim `music-assistant.io` without `www`.

## Pages

| Path | Result |
|---|---|
| `/app` or `/app/home` | Opens the Home page. |
| `/app/library` | Opens the Home page and the sidebar with the library categories. |
| `/app/library/<category>` | Opens one library list, for example Albums. |
| `/app/search` | Opens Search. |
| `/app/players` | Opens the players pager over the current tab. |
| `/app/players?player=<player>` | Opens the players pager and selects the player. |
| `/app/play?uri=<uri>` | Plays a media item. Refer to [Play a Media Item](#play-a-media-item). |

The library categories are `artists`, `albums`, `tracks`, `playlists`, `audiobooks`, `podcasts`, `radios` and
`genres`. The app ignores a link with a different category.

The `player` value is the player id, the player name, or `local` for the built-in player of this device. The app compares the value without case. If an id and a
name match, the app uses the id. The app uses only the players that it shows in the players pager.

## Play a Media Item

`/app/play` plays a media item on a player. It does not open the players pager.

| Parameter | Need | Value |
|---|---|---|
| `uri` | Yes | The Music Assistant URI of the media item, for example `library://radio/12`. |
| `player` | No | The player id, the player name, or `local` for the built-in player of this device. Without a value, the app uses the selected player. |
| `option` | No | `play`, `replace`, `next`, `add` or `replace_next`. The default is `replace` ("Play now"). |

Encode the URI value, because it contains `:` and `/`. For example:

```
musicassistant://app/play?uri=library%3A%2F%2Fradio%2F12&player=Kitchen&option=add
```

The app ignores a play link without `uri`, a `uri` that is not a Music Assistant URI, or an unknown `option`.

The app waits up to 10 seconds for the player. If the player does not come, the app shows a message and plays nothing.

### Copy a Play Link

The app makes a play link for you:

1. Open the menu of an item page, or the menu of the expanded player.
2. Select **Copy play link**.

The link from an item page has no `player`, so it plays on the selected player. The link from the expanded player
plays the current item on that player. For the built-in player, the link contains `player=local`, because the id
of the built-in player is different on each device.

## Behavior

- The app must be signed in to a server. If the app is not signed in, it keeps the link and applies it after the
  sign-in.
- The app keeps only the last link. A new link replaces a link that the app did not apply.
- The app ignores a link that it does not know. It does not show an error.

## Platform Notes

- **Android**: Android opens the web link in the app after it checks the domain. If the check fails, open
  Settings → Apps → Music Assistant → Open by default, and add the link.
- **iOS**: iOS has no manual setting for the web link. If iOS opens the web link in Safari, use the custom scheme.
