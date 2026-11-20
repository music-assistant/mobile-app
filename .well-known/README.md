# `.well-known/` — App Links / Universal Links association files

These files are **staging copies**. They do nothing in this repo. They must be
served from the website that answers `https://www.music-assistant.io`, at
exactly:

| File | Must be reachable at | Content-Type |
|---|---|---|
| `assetlinks.json` | `https://www.music-assistant.io/.well-known/assetlinks.json` | `application/json` |
| `apple-app-site-association` | `https://www.music-assistant.io/.well-known/apple-app-site-association` | `application/json` (no extension) |

Requirements for both: HTTPS, HTTP 200, **no redirect**, valid cert. The app
claims the `www` host only: the apex redirects to `www`, and neither OS follows
a redirect for these files. See `.claude/deep-links.md`.

## `assetlinks.json` (Android App Links)

`sha256_cert_fingerprints` lists the signing certs of the APKs users actually
install.

Verify after hosting:
`adb shell pm get-app-links io.music_assistant.client` → look for `verified`.
Until then, Android also works via the manual toggle (Settings → Apps → Music
Assistant → *Open by default* → *Add link*), which needs no file.

## `apple-app-site-association` (iOS Universal Links)

`appID` is `<TeamID>.<bundleId>` = `5J5R9QK64D.io.music-assistant.client`.
`paths` is scoped to `/app/*` so the rest of the site is untouched.

Two non-code prerequisites on the Apple side:
1. The **Associated Domains** capability must be enabled on the App ID in the
   Apple Developer portal, then the provisioning profile regenerated (the
   `applinks:` entitlement in `iosApp/iosApp/CarPlay.entitlements` fails signing
   otherwise).
2. iOS has **no manual override** — until this file is live and fetched, the
   `https://…/app/…` links open Safari instead of the app. Apple's CDN caches
   the file, so reinstall the app to force a refetch during testing.
