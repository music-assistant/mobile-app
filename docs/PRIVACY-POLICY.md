# Privacy Policy — Music Assistant Mobile App

**Last updated: 9 June 2026**

**The short version:** We don't collect your data. The app talks to *your* Music
Assistant server — which you own and control — and to nothing else of ours except a
lightweight connection broker used only when you connect remotely. There are no
analytics, no ads, no tracking, and no crash reports sent to us.

This policy applies to the official Music Assistant mobile app on **Android** and **iOS**.

---

## Who we are

This is the official mobile client for the [Music Assistant](https://music-assistant.io)
project — an open-source media library manager that you run on your own hardware (a
Raspberry Pi, NAS, home server, etc.). The app is published and maintained by the Music
Assistant project.

If you have any questions about this policy or your privacy, contact us at:

- **Email:** `<PLACEHOLDER — add the project contact email before publishing>`
- **Project:** https://github.com/music-assistant

---

## The short version

- **We collect nothing about you.** No analytics, no usage profiling, no advertising
  identifiers, no telemetry.
- **No ads, ever.**
- **No third-party data sharing.** The app contains no analytics, crash-reporting, or
  advertising SDKs.
- **Your music data stays on your server.** Your library, listening, and playback all
  live on the Music Assistant server *you* run. The app is just a remote control for it.
- **What the app stores stays on your device** (and goes to your own server). Uninstalling
  the app removes it.

---

## What the app stores on your device

The app saves a small amount of configuration and cache data in your device's standard
app storage (Android `SharedPreferences`, iOS `UserDefaults`, and the app cache
directory). None of it is sent to us. All of it is removed when you uninstall the app.

| Category | What is stored | Why |
|---|---|---|
| **Connection & sign-in** | Server host, port, TLS flag, WebRTC remote ID; an access token for each server you connect to; your last/preferred connection method | To reconnect to your server without re-entering details each time |
| **Connection history** | The last 10 servers you connected to (host/port or remote ID, plus a timestamp) | To let you quickly reconnect to a previous server |
| **Device identity** | A device name you choose, and a randomly-generated ID for the optional Sendspin local playback feature | To identify this device to *your* server |
| **App preferences** | Theme, list/grid view modes, sort orders, tab and Android Auto/CarPlay layout, default tap actions, last selected player, Sendspin settings (port, codec, TLS, delay, etc.) | To remember how you like the app set up |
| **Image cache** | Album/artist/playlist artwork, up to ~256 MB on disk, plus an in-memory colour cache | To display artwork quickly and reduce network use |
| **Diagnostic logs** | Recent app logs and, if a crash occurs, a crash log — written to the app's local cache | For on-device troubleshooting (see "Logs & diagnostics" below) |

> **A note on the access token:** the token that lets the app talk to your server is
> stored in your device's standard app storage. As with most apps, this storage is not
> additionally encrypted by the app, so treat your device's lock screen as your first
> line of defence.

---

## What the app sends, and to whom

### 1. Your own Music Assistant server

This is the only place your activity goes. Depending on what you do in the app, it sends
your server:

- Your access token on each request (and your username/password **only** the one time
  you sign in, to obtain that token);
- Your chosen device name;
- Library browsing and search queries;
- Playback and queue commands (play/pause, seek, volume, add/remove/reorder, favourite,
  mark played, etc.);
- Your playback preferences (shuffle, repeat, playback speed, audio settings).

This server is **yours**. We don't operate it and we don't receive any of this data.

### 2. The WebRTC signalling server (`signaling.music-assistant.io`)

Used **only when you connect to your server remotely** (WebRTC mode) rather than directly
on your local network. To set up that connection, the app exchanges a few technical
messages with our signalling broker. It sees:

- Your server's **remote ID** (a code derived from your server's security certificate);
- Connection-setup data (SDP and ICE candidates — standard WebRTC handshake information);
- Keep-alive pings.

It **never** sees your credentials, your library, your searches, or your playback — those
travel over the encrypted peer-to-peer channel directly between your device and your
server, which the broker cannot read. The broker only helps the two ends find each other.

### 3. Album artwork from the internet

When some artwork is hosted on a public service (for example, a streaming provider's
image CDN) and you're connected in WebRTC mode, the app fetches that image **directly**
from that public address. In that case the image host sees your device's IP address and
user-agent, as with loading any image on the web. Artwork stored on your own server is
fetched from your server, not third parties.

### 4. Links you tap

If you tap a link in the app (for example "Learn More", or the web-client link), your
device's browser opens a Music Assistant web page (`music-assistant.io` /
`app.music-assistant.io`). These open only when you choose to tap them.

---

## What we do NOT collect

- ❌ Analytics or usage statistics
- ❌ Location data
- ❌ Contacts, calendars, photos, or files
- ❌ Microphone audio
- ❌ Advertising identifiers
- ❌ Any personal profile about you

---

## Permissions, and why the app asks for them

### Android

| Permission | Why |
|---|---|
| `INTERNET` | To connect to your Music Assistant server |
| `WAKE_LOCK` | To keep audio playing reliably while the screen is off |
| `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_MEDIA_PLAYBACK` | To play audio in the background with a media notification |
| `CAMERA` | Optional — to scan a QR code when you set up a remote (WebRTC) connection (you can also enter the details manually). The camera is active only while the scanner is open; nothing is recorded, stored, or transmitted. |

### iOS

| Item | Why |
|---|---|
| Local Network | To find and connect to your Music Assistant server on your local network |
| Background audio | To keep playing music when the app is in the background |
| Siri | Optional — to search, play, and like/dislike tracks hands-free, including from CarPlay |
| Camera | Optional — to scan a QR code when you set up a remote (WebRTC) connection (you can also enter the details manually). The camera is active only while the scanner is open; nothing is recorded, stored, or transmitted. |
| Microphone usage string *(present but **not used**)* | Required only because an included framework (WebRTC) references microphone APIs. The app does **not** access your microphone. |

---

## Logs & diagnostics

The app keeps a short, rolling buffer of recent logs on your device to help with
troubleshooting, and writes a crash log locally if it crashes. **These logs stay on your
device and are not sent anywhere automatically.** If *you* decide to share them (for
example to report a bug), the app sanitises them first to remove sensitive details such
as tokens, then hands them to your system's share sheet so you choose where they go.

---

## Children's privacy

The app is not directed at children and does not knowingly collect any personal
information from anyone, including children.

---

## Data retention & deletion

- The app stores everything described above **locally on your device**. Uninstalling the
  app removes all of it.
- You can sign out / disconnect a server in the app to remove its stored access token.
- Any data about your music and activity lives on **your** Music Assistant server; manage
  or delete it there.

---

## Changes to this policy

If we change this policy, we'll update the "Last updated" date above and publish the new
version at the same location. Significant changes will be noted in the app's release notes.

---

## Contact

- **Email:** `<PLACEHOLDER — add the project contact email before publishing>`
- **Project & issues:** https://github.com/music-assistant

---

## Appendix — Google Play Data Safety answers

For transcription into the Play Console Data Safety form:

- **Does your app collect or share any of the required user data types?** No.
- **Data shared with third parties:** None.
- **Data collected:** None. (Configuration and cache are stored only on the device and
  are not transmitted to the developer.)
- **Is all user data encrypted in transit?** Yes when you use TLS/WebRTC; the app
  supports encrypted connections to your server, and the WebRTC peer channel is encrypted.
- **Can users request data deletion?** Data lives on the device and on the user's own
  server; uninstalling removes on-device data.
- **Uses advertising / advertising ID:** No.
- **Tracking (as defined by Play):** No.

## Appendix — Apple privacy "nutrition label"

- **Data Used to Track You:** None.
- **Data Linked to You:** None.
- **Data Not Linked to You:** None.
- Summary: **Data Not Collected.**
