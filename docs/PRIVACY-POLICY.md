# Privacy Policy — Music Assistant Mobile App

**Last updated: 25 July 2026**

This policy applies to the official Music Assistant mobile app on **Android** and **iOS**.

---

## The short version

- **We collect nothing about you.** No analytics, no usage profiling, no telemetry, no advertising identifiers.
- **No ads, ever.**
- **No third-party data sharing.** The app ships with **no** analytics, crash-reporting, or advertising SDKs.
- **Your music data stays on your server.** Your library, listening, and playback all live on the Music Assistant server *you* run. The app is just a remote control for it.
- **What the app stores stays on your device.** Your access token, your server's address, and your connection history are excluded from the Android system backup, so they never reach your cloud account. See **[Backups](#backups)**.

The app talks to *your* Music Assistant server — which you own and control — and to **nothing else of ours** except a lightweight connection broker used only when you connect remotely.

---

## Who we are

This is the official mobile client for the [Music Assistant](https://music-assistant.io) project — an open-source media library manager that you run on your own hardware (a Raspberry Pi, NAS, home server, etc.). The app is published and maintained by the Music Assistant project.

**Questions about this policy or your privacy?** See **[Contact](#contact)** below.

---

## What the app stores on your device

The app saves a small amount of configuration and cache data in your device's standard app storage (Android `SharedPreferences`, iOS `UserDefaults`, and the app cache directory). **None of it is sent to us, and all of it is removed when you uninstall the app.**

| Category | What is stored | Why |
|---|---|---|
| **Connection & sign-in** | Server host, port, TLS flag, WebRTC remote ID; an access token for each server you connect to; your last/preferred connection method | To reconnect to your server without re-entering details each time |
| **Connection history** | The last 10 servers you connected to (host/port or remote ID) | To let you quickly reconnect to a previous server |
| **Device identity** | An auto-generated client identifier; plus, for the optional Sendspin local-playback feature, a device name **you** choose and a randomly-generated ID | To identify this device to *your* server |
| **App preferences** | Theme, list/grid view modes, sort orders, tab and Android Auto/CarPlay layout, default tap actions, last selected player, Sendspin settings (port, codec, TLS, delay, etc.) | To remember how you like the app set up |
| **Image cache** | Album/artist/playlist artwork, up to ~256 MB on disk, plus an in-memory colour cache | To display artwork quickly and reduce network use |
| **Diagnostic logs** | Recent app logs and, if a crash occurs, a crash log — written to the app's local cache | For on-device troubleshooting (see **[Logs & diagnostics](#logs--diagnostics)**) |

> **A note on the access token.** The token that lets the app talk to your server is stored in your device's standard app storage. As with most apps, this storage is **not additionally encrypted** by the app, so treat your device's lock screen as your first line of defence. It is held in a separate file that the Android system backup **excludes**, so the token stays on this device — see **[Backups](#backups)**.

---

## What the app sends, and to whom

### 1. Your own Music Assistant server

**This is the only place your activity goes.** Depending on what you do in the app, it sends your server:

- Your **access token** on each request (and your **username/password only once**, when you first sign in, to obtain that token — the password is never stored);
- Your chosen **device name**;
- **Library browsing and search** queries;
- **Playback and queue commands** (play/pause, seek, volume, add/remove/reorder, favourite, mark played, etc.);
- Your **playback preferences** (shuffle, repeat, playback speed, audio settings).

This server is **yours**. We don't operate it and we don't receive any of this data.

### 2. The WebRTC signalling server (`signaling.music-assistant.io`)

Used **only when you connect to your server remotely** (WebRTC mode) rather than directly on your local network. To set up that connection, the app exchanges a few technical messages with our signalling broker. The broker also hands the app a list of **STUN/TURN servers** — with short-lived credentials — that help the two ends find a network path to each other. **The broker sees only:**

- Your server's **remote ID** (a code derived from your server's security certificate);
- **Connection-setup data** (SDP and ICE candidates — standard WebRTC handshake information);
- **Keep-alive pings.**

It **never** sees your credentials, your library, your searches, or your playback — those travel over a **separately encrypted** channel that the broker cannot read.

**When a relay is used.** WebRTC first tries to open a **direct** path between your device and your server. If your network does not allow one — common behind strict or carrier-grade NAT — the connection falls back to a **TURN relay server**, and your encrypted traffic passes through that relay instead. The relay forwards **encrypted** packets and **cannot read them**; it does see the **IP addresses of both ends** and how much data it forwards. The app does not choose or configure these servers itself — it uses the list the broker provides.

### 3. Album artwork from the internet

When some artwork is hosted on a public service (for example, a streaming provider's image CDN) and you're connected in WebRTC mode, the app fetches that image **directly** from that public address. In that case the image host sees your device's IP address and user-agent, as with loading any image on the web. **Artwork stored on your own server is fetched from your server, not from third parties.**

### 4. Links you tap

If you tap an external link — such as **"Learn More"** (which opens `music-assistant.io`) or a link to **your own server's** web page (e.g. player/DSP settings) — your device's browser opens it. **These open only when you choose to tap them.**

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
| `CAMERA` | **Optional** — to scan a QR code when you set up a remote (WebRTC) connection (you can also enter the details manually). The camera is active only while the scanner is open; **nothing is recorded, stored, or transmitted.** |

### iOS

| Item | Why |
|---|---|
| **Local Network** | To find and connect to your Music Assistant server on your local network |
| **Background audio** | To keep playing music when the app is in the background |
| **Siri** | **Optional** — to search, play, and like/dislike tracks hands-free, including from CarPlay |
| **Camera** | **Optional** — to scan a QR code when you set up a remote (WebRTC) connection (you can also enter the details manually). The camera is active only while the scanner is open; **nothing is recorded, stored, or transmitted.** |
| **Microphone usage string** *(present but **not used**)* | Required only because an included framework (WebRTC) references microphone APIs. The app does **not** access your microphone. |

---

## Logs & diagnostics

The app keeps a short, rolling buffer of recent logs on your device to help with troubleshooting, and writes a crash log locally if it crashes. **These logs stay on your device and are never sent anywhere automatically.**

If *you* decide to share them (for example, to report a bug), the app first **sanitises** them — redacting server addresses, IP addresses, connection IDs, and email addresses — and then hands them to your system's share sheet, so **you** choose where they go. Access tokens are never written to the logs in the first place.

---

## Children's privacy

The app is not directed at children and does not knowingly collect any personal information from anyone, including children.

---

## Data retention & deletion

- The app stores everything described above **locally on your device**. Uninstalling the app removes it from the device. See **[Backups](#backups)** for what a system backup does and does not copy.
- You can **sign out / disconnect** a server in the app to remove its stored access token.
- Any data about your music and activity lives on **your** Music Assistant server; manage or delete it there.

---

## Backups

Your phone's operating system can back app data up so that you can restore it onto a new device. This backup goes to **your own** cloud account, never to us.

**Android.** The app separates its stored data into two files, and tells the system to **exclude** the sensitive one:

| Backed up | Never backed up |
|---|---|
| Theme, view modes, sort orders, tab and Android Auto layout, tap actions, Sendspin playback settings | **Access tokens**, your server's **host, port and TLS setting**, its **remote ID**, your **connection history**, and any custom Sendspin host |

So a restored phone keeps how you like the app set up, but asks you to sign in to your server again. The exclusion covers **both** a cloud backup and a direct phone-to-phone transfer.

**iOS.** iOS gives an app no equivalent control over what a backup takes, so app data — the access token included — is part of your iCloud or your computer backup. Apple encrypts an iCloud backup, and the backup belongs to **your** Apple account, not to us. To keep the app out of it, open **Settings → [your name] → iCloud → Manage Account Storage → Backups**, and turn the app off.

To disable backup for the app on Android, open **Settings → Google → Backup**, or turn device backup off.

---

## Changes to this policy

If we change this policy, we'll update the **"Last updated"** date above and publish the new version at the same location. Significant changes will be noted in the app's release notes.

---

## Contact

- **Email:** `app@music-assistant.io`
- **Project & issues:** https://github.com/music-assistant

---

## Appendix — Google Play Data Safety answers

For transcription into the Play Console Data Safety form:

- **Does your app collect or share any of the required user data types?** No — the developer neither receives nor stores any user data. (See the note below on third-party artwork hosts.)
- **Data collected (by the developer):** None. Configuration and cache are stored only on the device and are never transmitted to the developer.
- **Data shared with third parties:** None by the developer. **Note:** in remote (WebRTC) mode, album artwork hosted on public services is loaded **directly** from those services. As with any app or browser loading a web image, the image host then sees your device's **IP address and user-agent** for that request. This data is **not received, stored, or processed by us**, is used only to serve the image, and does not occur when you connect over your local network.
- **Is all user data encrypted in transit?** Yes. Remote (WebRTC) connections are always encrypted. Local-network connections to your own server use TLS when your server offers it; the choice of server and transport is yours.
- **Can users request data deletion?** Data lives on the device and on the user's own server; uninstalling removes on-device data.
- **Uses advertising / advertising ID:** No.
- **Tracking (as defined by Play):** No.

## Appendix — Apple privacy "nutrition label"

- **Data Used to Track You:** None.
- **Data Linked to You:** None.
- **Data Not Linked to You:** None.
- Summary: **Data Not Collected.**
