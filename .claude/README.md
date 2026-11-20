# Documentation Index

This directory holds the developer documentation of the Music Assistant KMP client. `CLAUDE.md` imports
`project.md`, and `project.md` imports the four core documents below. So Claude Code loads the core documents
in every session.

## Core Documents

| Document | Content |
|---|---|
| [project.md](project.md) | Build and test commands, feature list, links to the feature documents |
| [architecture.md](architecture.md) | Patterns, data layer rules, session lifecycle, Sendspin, Android services |
| [project-structure.md](project-structure.md) | Modules, source sets, packages, key files |
| [dependencies.md](dependencies.md) | Libraries and their purpose |
| [guidelines.md](guidelines.md) | Code location, Kotlin and Compose conventions |

## Feature Documents

| Document | Content |
|---|---|
| [deep-links.md](deep-links.md) | `musicassistant://app` routes, App Links, Universal Links, OAuth callback |
| [settings-screen.md](settings-screen.md) | Server connection, sign-in methods, local player settings |
| [webrtc-completion-summary.md](webrtc-completion-summary.md) | Remote access over WebRTC, data channels, Sendspin over WebRTC |
| [ios_audio_pipeline.md](ios_audio_pipeline.md) | Audio output of the local player on iOS |
| [volume-control.md](volume-control.md) | Media session volume, volume keys for a remote player |
| [carplay.md](carplay.md) | CarPlay templates and Siri media intents |
| [feature-parity-report.md](feature-parity-report.md) | Feature comparison with the web frontend (server 2.10 and 2.11) |
| [party-guest-investigation.md](party-guest-investigation.md) | Investigation of the Party plugin guest side |

## Other Documentation

| Location | Content |
|---|---|
| `sendspin-kmp/README.md` | Sendspin player library: public API, wire versions, release |
| `docs/app-documentation/` | User guide, published with MkDocs (`mkdocs.yml`) |
| `docs/ANDROID-AUTO.md` | Android Auto and voice playback, developer reference |
| `docs/IOS-BUILD-INSTRUCTIONS.md` | iOS build setup |
| `docs/DEV-ENVIRONMENT.md` | Development environment setup |
| `docs/plans/sendspin-encryption.md` | Plan of the encrypted Sendspin protocol. The plan shipped, so the code is the reference. |
| `.well-known/README.md` | Hosting rules of the association files for App Links and Universal Links |

## Maintenance Rules

1. Change the document of a feature in the same change as the code.
2. Add a new document to this index and to the "UI Documentation" list in `project.md`.
3. Describe the current code. Put the history in the commit message, not in a document.
4. Do not write a "Last Updated" date. Git keeps the date.
5. Delete a document that describes code that no longer exists. Git keeps the old text.
6. Write prose to ASD-STE100 rules.
