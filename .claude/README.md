# Documentation Index

This directory contains project documentation for the Music Assistant KMP Client.

**Last Updated:** 2026-02-08

## Quick Navigation

| Category | Documents |
|----------|-----------|
| **Architecture** | [architecture.md](#architecture), [project-structure.md](#project-structure) |
| **Development** | [guidelines.md](#guidelines), [dependencies.md](#dependencies) |
| **Features** | [WebRTC Remote Access](#webrtc-remote-access), [Sendspin](#sendspin-protocol), [Settings](#settings-screen), [iOS Audio](#ios-audio-pipeline), [Volume Control](#volume-control) |
| **Getting Started** | [project.md](#project-overview) |

---

## Core Documentation

### Project Overview
**[project.md](project.md)**
- Quick start guide
- Feature list
- Build commands (Android, iOS)
- Project description and goals

### Architecture
**[architecture.md](architecture.md)**
- MVVM + Unidirectional Data Flow pattern
- Dependency injection (Koin)
- Navigation (Navigation3)
- Expect/Actual pattern usage
- State management with StateFlow
- **UI Architecture**: HomeScreen/HomeScreenViewModel (current), MainScreen/MainViewModel (deprecated)
- Sendspin integration overview
- Android services integration

### Project Structure
**[project-structure.md](project-structure.md)**
- Source set organization (commonMain, androidMain, iosMain)
- Package organization
- Feature module pattern
- Platform-specific code guidelines

### Dependencies
**[dependencies.md](dependencies.md)**
- UI: Compose Multiplatform, Material3, Icons
- Networking: Ktor, WebSocket, WebRTC
- Data: kotlinx.serialization, multiplatform-settings
- DI & Architecture: Koin, Navigation3
- Media: Coil 3, ExoPlayer, AVPlayer
- Utilities: Kermit logging, Reorderable, mDNS

### Development Guidelines
**[guidelines.md](guidelines.md)**
- Code location rules (commonMain preference)
- Kotlin conventions (StateFlow, sealed interfaces)
- Compose conventions (Material3, remember, koinViewModel)
- File organization
- Logging with Kermit
- State wrappers (DataState<T>)
- Testing platform notes

---

## Feature Documentation

### WebRTC Remote Access

**[webrtc-completion-summary.md](webrtc-completion-summary.md)** ⭐ **COMPLETE - PRODUCTION READY**
- **Status**: ✅ Fully functional on Android (updated 2026-02-08)
- WebRTC peer-to-peer connections for remote access
- Cloud signaling server integration (`wss://signaling.music-assistant.io/ws`)
- DTLS-encrypted data channels for secure communication
- Full API compatibility (authentication, library, playback)
- **Critical fix**: TEXT message transmission via native API (webrtc-kmp limitation workaround)
- Implementation phases: Foundation → Signaling → Peer Connection → Integration → Testing
- Known issues: iOS stubs only (Android production-ready), optional Sendspin channel not implemented
- Architecture decisions and production-quality fixes documented

### Sendspin Protocol

**[sendspin-status.md](sendspin-status.md)** ⭐ **PRIMARY REFERENCE**
- **Current implementation status** (updated 2026-01-16)
- Platform support breakdown:
  - Android: PCM, Opus, FLAC (all working)
  - iOS: PCM, Opus, FLAC via MPV (all working)
- Auto-reconnect and network resilience features
- Recent additions and changelog
- Performance metrics
- Known issues and limitations

**[sendspin-resilient-architecture.md](sendspin-resilient-architecture.md)**
- Auto-reconnect implementation details
- Network resilience design (keepalive settings)
- Connection state management
- What was implemented vs. what wasn't
- Testing results and benefits

### Settings Screen

**[settings-screen.md](settings-screen.md)**
- Complete Settings screen documentation (updated Jan 2026)
- UI states (disconnected, connected, authenticated)
- Section components (server connection, authentication, local player)
- Authentication flow (builtin, OAuth)
- Logout vs. disconnect behavior
- Error handling strategies
- Sendspin integration (local player configuration)
- Testing scenarios

### iOS Audio Pipeline

**[ios_audio_pipeline.md](ios_audio_pipeline.md)**
- MPV-based audio pipeline for iOS (completed 2026-01-14)
- FLAC, Opus, PCM codec support
- Custom stream protocol implementation
- RingBuffer architecture
- Codec configuration
- Known issues and future work

### Volume Control

**[volume-control.md](volume-control.md)**
- MediaSession local playback mode
- System volume integration
- Bidirectional sync (device ↔ server)
- Preventing volume drift and circular updates
- Service cleanup requirements

---

## Documentation Maintenance

### Recent Changes (2026-01-16)

**Deleted (Outdated/Contradictory):**
- ~~`sendspin-integration-design.md`~~ - Original 66KB design doc (superseded by status doc)
- ~~`sendspin-integration-guide.md`~~ - Integration guide (superseded by status doc)
- ~~`sendspin-android-services-integration.md`~~ - Contradictory proposed design
- ~~`connection-service-design.md`~~ - Never implemented

**Why deleted?**
- **Redundancy**: Multiple overlapping documents caused confusion
- **Outdated**: Implementation evolved significantly from original designs
- **Contradictory**: Some docs had "proposed design" + "this wasn't implemented" sections
- **Single source of truth**: `sendspin-status.md` is now the authoritative reference

### Maintenance Guidelines

1. **Update sendspin-status.md** when Sendspin features change
2. **Update settings-screen.md** when Settings UI changes
3. **Update architecture.md** when architectural patterns change
4. **Update dependencies.md** when adding/removing libraries
5. **Keep this README current** when documentation structure changes

### Git History

Deleted documentation is preserved in git history:
```bash
# View deleted file
git show HEAD~1:.claude/sendspin-integration-design.md

# See deletion commit
git log --all --full-history -- ".claude/sendspin-integration-design.md"
```

---

## Contributing to Documentation

### Adding New Documentation

1. Create file in `.claude/` directory
2. Use clear, descriptive filename
3. Add to this README under appropriate category
4. Include "Last Updated" date at top of document
5. Cross-reference related docs

### Updating Existing Documentation

1. Update "Last Updated" date
2. Add changelog entry if significant changes
3. Update cross-references if structure changed
4. Consider if README needs updating

### Documentation Standards

- **Markdown format**: Use GitHub-flavored markdown
- **Code blocks**: Include language hints for syntax highlighting
- **Status indicators**: Use ✅ (working), ⚠️ (partial), ❌ (not implemented)
- **Cross-references**: Link to related docs
- **Examples**: Include code examples where helpful
- **Clear structure**: Use headers, tables, and lists effectively

---

## Quick Links to Root Documentation

- [Main README](../README.md) - Project README with features and installation
- [CHANGELOG](../CHANGELOG.md) - Project changelog (started 2026-01-08)
- [CLAUDE.md](../CLAUDE.md) - Claude Code integration (imports project.md)

---

**Questions about documentation?** Check git history or file an issue.

**Need to find something?** Use grep/ripgrep:
```bash
# Search all documentation
rg "sendspin" .claude/

# Find files mentioning a topic
grep -r "authentication" .claude/
```
