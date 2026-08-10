import Foundation
import CarPlay
import ComposeApp
import os.log

/// CarPlay lifecycle markers. Logged at `.default` so they survive a
/// post-drive sysdiagnose (`.info`/`.debug` are in-memory only).
private let cpLog = OSLog(subsystem: "io.music-assistant.mobile-client", category: "CarPlay")

class CarPlaySceneDelegate: UIResponder, CPTemplateApplicationSceneDelegate {

    var interfaceController: CPInterfaceController?

    // MARK: - Readiness state
    //
    // `isReady` mirrors `serviceClient.isReadyForCommands` so synchronous
    // tap-time checks don't have to await the StateFlow. Updated by the
    // `readinessSubscription` callback on the main thread.
    private var isReady: Bool = false
    /// True only when this connection passed the local-player gate and attached
    /// to the shared external-consumer lifecycle.
    private var didAttachExternalConsumer: Bool = false
    private var readinessSubscription: Cancellable?

    /// Monotonic connection generation, bumped on every connect AND
    /// disconnect. Async completions capture it at dispatch and re-check on
    /// delivery: on a rapid disconnect → reconnect, connection A's in-flight
    /// `loadCarPlayStrings` completion would otherwise see connection B's
    /// non-nil `interfaceController`, pass the guard, and double-subscribe
    /// (leaking A's channel subscriptions when B's completion overwrites
    /// them).
    private var connectionGen: Int = 0

    // Localized CarPlay strings, resolved from the shared Compose catalog once
    // per connect (see didConnect). Always set before any template is built.
    private var strings: CarPlayStrings?

    // MARK: - Now Playing buttons (shuffle/repeat)
    //
    // Created once per connection and NEVER rebuilt — installing fresh button
    // instances on state changes is the flicker/lock-up class this design
    // exists to avoid. State flows one way per concern:
    //   - Track channel: profile flips (music ↔ long-form) swap which retained
    //     instances are installed; long-form content gets no shuffle/repeat.
    //   - Modes channel: enablement only.
    // Selected state is never written here: CarPlay renders it from
    // MPRemoteCommandCenter's currentShuffleType/currentRepeatType, whose sole
    // writer is NowPlayingCoordinator's modes handler.
    private var shuffleButton: CPNowPlayingShuffleButton?
    private var repeatButton: CPNowPlayingRepeatButton?
    private var trackSubscription: Cancellable?
    private var modesSubscription: Cancellable?
    /// nil = nothing applied yet this connection, so the first emission
    /// always installs.
    private var nowPlayingButtonsInstalled: Bool?
    /// Last enablement pushed to the buttons; nil until the first modes
    /// emission so the initial value always applies.
    private var nowPlayingButtonsEnabled: Bool?

    // Weakly held so a connectivity restore can re-fire the homepage fetch
    // without retaining the template after CarPlay disconnects.
    private weak var libraryTemplate: CPListTemplate?
    private weak var libraryBrowseSection: CPListSection?

    /// Monotonic id for in-flight Library recommendation fetches so a slow
    /// first attempt (fired before auth) can't overwrite a faster second
    /// attempt (fired by `refreshLibraryOnReconnect` once readiness flipped).
    private var recommendationsFetchGen: Int = 0

    /// Shared completion handler for CarPlay template operations.
    private let logTemplateError: (Bool, Error?) -> Void = { _, error in
        if let error = error {
            os_log("CP: template error: %{public}@",
                   log: cpLog, type: .error, "\(error)")
        }
    }

    // MARK: - CPTemplateApplicationSceneDelegate

    func templateApplicationScene(_ templateApplicationScene: CPTemplateApplicationScene, didConnect interfaceController: CPInterfaceController) {
        self.interfaceController = interfaceController
        os_log("CP: didConnect", log: cpLog, type: .default)
        // Reset per-session state for a clean reconnect.
        connectionGen += 1
        recommendationsFetchGen = 0
        isReady = false
        didAttachExternalConsumer = false

        // If the local player is disabled, refuse CarPlay entirely (no attach, no reconnect, no browse).
        guard KmpHelper.shared.isLocalPlayerEnabled() else {
            os_log("CP: local player disabled — refusing CarPlay attachment", log: cpLog, type: .default)
            self.interfaceController = nil
            return
        }
        didAttachExternalConsumer = true
        KmpHelper.shared.onExternalConsumerActive()
        // Resolve localized strings before building templates: CarPlay template
        // titles are immutable after construction, so the active-locale strings
        // must be known up front. Subscribing to readiness inside the completion
        // preserves the subscribe-before-setupTemplates ordering that drives the
        // initial Library fetch (see handleReadinessChange / createLibraryTemplate).
        // `(Boolean) -> Unit` from Kotlin exposes as `KotlinBoolean`; unbox.
        // The generation check (not just `interfaceController != nil`) rejects
        // completions from a connection that has since been torn down.
        let gen = connectionGen
        KmpHelper.shared.loadCarPlayStrings { [weak self] loaded in
            guard let self = self, self.connectionGen == gen,
                  self.interfaceController != nil else { return }
            self.strings = loaded
            self.readinessSubscription = KmpHelper.shared.observeReadiness { [weak self] ready in
                self?.handleReadinessChange(ready.boolValue)
            }
            self.setupNowPlayingButtons()
            self.setupTemplates()
        }
    }

    func templateApplicationScene(_ templateApplicationScene: CPTemplateApplicationScene, didDisconnectInterfaceController interfaceController: CPInterfaceController) {
        // Invalidate any in-flight didConnect completions for this connection.
        connectionGen += 1
        // Cancel subscriptions before tearing down state to avoid the
        // callbacks racing with a nil interfaceController.
        readinessSubscription?.cancel()
        readinessSubscription = nil
        trackSubscription?.cancel()
        trackSubscription = nil
        modesSubscription?.cancel()
        modesSubscription = nil
        // The now-playing template is a process-lifetime singleton; leave it
        // empty rather than holding this connection's button instances.
        CPNowPlayingTemplate.shared.updateNowPlayingButtons([])
        shuffleButton = nil
        repeatButton = nil
        nowPlayingButtonsInstalled = nil
        nowPlayingButtonsEnabled = nil
        libraryTemplate = nil
        libraryBrowseSection = nil
        self.interfaceController = nil
        if didAttachExternalConsumer {
            KmpHelper.shared.onExternalConsumerInactive()
            didAttachExternalConsumer = false
        }
        os_log("CP: didDisconnect", log: cpLog, type: .default)
    }

    // MARK: - Readiness handling

    /// Called from the Kotlin readiness subscription. The closure already runs
    /// on `Dispatchers.Main` (UIKit main thread on iOS), so direct UI updates
    /// are safe — no `DispatchQueue.main.async` hop needed.
    private func handleReadinessChange(_ ready: Bool) {
        let wasReady = isReady
        isReady = ready
        os_log("CP: handleReadinessChange wasReady=%{public}@ ready=%{public}@",
               log: cpLog, type: .default,
               String(wasReady), String(ready))
        guard interfaceController != nil else {
            os_log("CP: handleReadinessChange ignored — no interfaceController",
                   log: cpLog, type: .default)
            return
        }

        if !wasReady && ready {
            // Reconnected — re-fire the Library fetch. Drilldowns re-fetch
            // when re-entered, so only the top-level needs refreshing.
            refreshLibraryOnReconnect()
        }
    }

    private func refreshLibraryOnReconnect() {
        guard
            let template = libraryTemplate,
            let browseSection = libraryBrowseSection
        else {
            os_log("CP: refreshLibraryOnReconnect skipped — template/section nil",
                   log: cpLog, type: .default)
            return
        }
        os_log("CP: refreshLibraryOnReconnect firing loadRecommendations",
               log: cpLog, type: .default)
        loadRecommendations(for: template, browseSection: browseSection)
    }

    /// Transient alert when the user taps while the transport is down.
    /// Idempotent — CarPlay throws on stacked presentations.
    private func showOfflineAlert() {
        guard let interfaceController = interfaceController,
              let strings = strings,
              interfaceController.presentedTemplate == nil
        else { return }
        let alert = CPAlertTemplate(
            titleVariants: [strings.offlineAlert],
            actions: [
                CPAlertAction(title: strings.ok, style: .default) { [weak self] _ in
                    self?.interfaceController?.dismissTemplate(animated: true, completion: nil)
                }
            ]
        )
        interfaceController.presentTemplate(alert, animated: true, completion: nil)
    }

    // MARK: - Now Playing buttons

    /// Creates the shuffle/repeat button instances for this connection and
    /// subscribes to the channels that drive them. StateFlow replay delivers
    /// the current values immediately, so a late connect (audiobook mid-play,
    /// dynamic playlist) starts with the correct profile and enablement.
    private func setupNowPlayingButtons() {
        dispatchPrecondition(condition: .onQueue(.main))
        // Taps deliberately skip the `isReady` gate the navigation taps use:
        // the lock-screen shuffle/repeat targets don't gate either, and the
        // buttons are only enabled while a live queue publishes modes, so a
        // not-ready tap is unreachable in practice.
        shuffleButton = CPNowPlayingShuffleButton { _ in
            NowPlayingCoordinator.shared.dispatchCarCommand("toggle_shuffle")
        }
        repeatButton = CPNowPlayingRepeatButton { _ in
            NowPlayingCoordinator.shared.dispatchCarCommand("toggle_repeat")
        }
        // Fresh CPNowPlayingButtons default to enabled. Start disabled so the
        // track replay can't install enabled-looking buttons for the runloop
        // gap before the modes replay delivers the real enablement.
        shuffleButton?.isEnabled = false
        repeatButton?.isEnabled = false
        nowPlayingButtonsInstalled = nil
        nowPlayingButtonsEnabled = nil

        trackSubscription = KmpHelper.shared.observeNowPlayingTrack { [weak self] track in
            // Long-form content swaps prev/next for ±N skips in the command
            // center; its CarPlay surface likewise drops shuffle/repeat.
            // A nil track keeps the music profile (buttons present, and the
            // modes channel's nil disables them).
            self?.applyNowPlayingButtons(installed: track?.isLongFormContent != true)
        }
        modesSubscription = KmpHelper.shared.observeNowPlayingModes { [weak self] modes in
            guard let self else { return }
            let enabled = modes?.togglesEnabled == true
            guard self.nowPlayingButtonsEnabled != enabled else { return }
            self.nowPlayingButtonsEnabled = enabled
            self.shuffleButton?.isEnabled = enabled
            self.repeatButton?.isEnabled = enabled
            // CarPlay snapshots button state at presentation: a property write
            // on an already-installed instance does not re-render. Re-present
            // the SAME retained instances (not new ones) to publish the change.
            if self.nowPlayingButtonsInstalled == true {
                self.presentNowPlayingButtons()
            }
        }
    }

    /// Installs or removes the retained button instances, only on profile
    /// change — never rebuilding them.
    private func applyNowPlayingButtons(installed: Bool) {
        dispatchPrecondition(condition: .onQueue(.main))
        guard nowPlayingButtonsInstalled != installed else { return }
        let hadApplied = nowPlayingButtonsInstalled != nil
        nowPlayingButtonsInstalled = installed
        // First emission of the long-form profile: the template is already
        // empty (didDisconnect teardown / fresh process), so there is nothing
        // to remove — record the state without a misleading "removed" log or
        // a no-op empty update.
        guard installed || hadApplied else { return }
        os_log("CP: now-playing buttons %{public}@",
               log: cpLog, type: .default, installed ? "installed" : "removed")
        presentNowPlayingButtons()
    }

    /// Pushes the current retained instances (or none) to the template.
    /// The sole `updateNowPlayingButtons` caller besides disconnect teardown.
    private func presentNowPlayingButtons() {
        dispatchPrecondition(condition: .onQueue(.main))
        let buttons: [CPNowPlayingButton] = nowPlayingButtonsInstalled == true
            ? [shuffleButton, repeatButton].compactMap { $0 }
            : []
        CPNowPlayingTemplate.shared.updateNowPlayingButtons(buttons)
    }

    /// Set Library as the initial home screen / Root
    private func setupTemplates() {
        guard let strings = strings else { return }
        let libraryTemplate = createLibraryTemplate(strings)
        interfaceController?.setRootTemplate(libraryTemplate, animated: true, completion: logTemplateError)
    }

    // MARK: - UI Construction

    // App theme colors from Color.kt, adaptive for light/dark mode
    // Light: primaryContainer ≈ #E0DEFF, primary = #575992
    // Dark:  primaryContainer ≈ #404378, primary = #C0C1FF
    private static let cardBackground = UIColor { trait in
        trait.userInterfaceStyle == .dark
            ? UIColor(red: 0x40/255.0, green: 0x43/255.0, blue: 0x78/255.0, alpha: 1.0)
            : UIColor(red: 0xE0/255.0, green: 0xDE/255.0, blue: 0xFF/255.0, alpha: 1.0)
    }
    private static let iconTint = UIColor { trait in
        trait.userInterfaceStyle == .dark
            ? UIColor(red: 0xC0/255.0, green: 0xC1/255.0, blue: 0xFF/255.0, alpha: 1.0)
            : UIColor(red: 0x57/255.0, green: 0x59/255.0, blue: 0x92/255.0, alpha: 1.0)
    }

    private static func renderCategoryImage(symbol symbolName: String, size: CGSize, traits: UITraitCollection) -> UIImage {
        let tintColor = iconTint.resolvedColor(with: traits)
        let symbol = UIImage(systemName: symbolName)!

        // Use scale: 0 to automatically match the screen scale
        let format = UIGraphicsImageRendererFormat()
        format.scale = 0 // Use screen scale
        format.opaque = false

        let renderer = UIGraphicsImageRenderer(size: size, format: format)
        return renderer.image { ctx in
            // No background - transparent
            // Make the symbol much larger - use 80% of the available space
            let symbolConfig = UIImage.SymbolConfiguration(pointSize: size.height * 0.80, weight: .semibold)
            let configured = symbol.withConfiguration(symbolConfig)
                .withTintColor(tintColor, renderingMode: .alwaysOriginal)
            let symbolSize = configured.size
            let origin = CGPoint(
                x: (size.width - symbolSize.width) / 2,
                y: (size.height - symbolSize.height) / 2
            )
            configured.draw(at: origin)
        }
    }

    /// Creates a dynamic UIImage that re-renders for light/dark mode automatically
    private static func dynamicCategoryImage(symbol: String, size: CGSize) -> UIImage {
        let light = renderCategoryImage(symbol: symbol, size: size,
            traits: UITraitCollection(userInterfaceStyle: .light))
        let dark = renderCategoryImage(symbol: symbol, size: size,
            traits: UITraitCollection(userInterfaceStyle: .dark))

        let asset = UIImageAsset()
        asset.register(light, with: UITraitCollection(userInterfaceStyle: .light))
        asset.register(dark, with: UITraitCollection(userInterfaceStyle: .dark))
        return asset.image(with: UITraitCollection.current)
    }

    private func createLibraryTemplate(_ strings: CarPlayStrings) -> CPListTemplate {
        // "Browse" item pushes a CPGridTemplate with all categories
        let browseIcon = Self.dynamicCategoryImage(symbol: "square.grid.2x2.fill", size: CPListItem.maximumImageSize)
        let browseItem = CPListItem(text: strings.browse, detailText: strings.browseSubtitle, image: browseIcon)
        browseItem.handler = { [weak self] _, completion in
            self?.pushBrowseGrid()
            completion()
        }

        let browseSection = CPListSection(
            items: [browseItem],
            header: nil,
            sectionIndexTitle: nil
        )

        // Section 2+: Recommendation folders (starts with loading placeholder)
        let loadingItem = CPListItem(text: strings.loading, detailText: nil)
        let loadingSection = CPListSection(
            items: [loadingItem],
            header: nil,
            sectionIndexTitle: nil
        )

        let libraryList = CPListTemplate(title: strings.library, sections: [browseSection, loadingSection])

        // Weak refs feed `refreshLibraryOnReconnect`, which is the sole
        // path that fires `loadRecommendations` — including the initial
        // fetch (driven by the readiness sub's first emission).
        self.libraryTemplate = libraryList
        self.libraryBrowseSection = browseSection

        return libraryList
    }

    // MARK: - Data Loading Helpers

    private func loadRecommendations(for template: CPListTemplate, browseSection: CPListSection) {
        guard let strings = strings else { return }
        recommendationsFetchGen += 1
        let myGen = recommendationsFetchGen
        os_log("CP: loadRecommendations gen=%{public}d", log: cpLog, type: .default, myGen)
        CarPlayContentManager.shared.fetchRecommendationFolders { [weak self] (folders: [RecommendationFolder]?) in
            guard let self = self else { return }
            // Drop completions from superseded calls so a slow first attempt
            // can't overwrite a faster second attempt's results.
            guard myGen == self.recommendationsFetchGen else {
                os_log("CP: loadRecommendations gen=%{public}d superseded, dropping result",
                       log: cpLog, type: .default, myGen)
                return
            }

            // nil means the fetch timed out. Show a disconnected affordance —
            // `handleReadinessChange` re-fires this method when readiness
            // flips back to true.
            guard let folders = folders else {
                template.updateSections([
                    browseSection,
                    CPListSection(items: [self.disconnectedRow(strings)], header: nil, sectionIndexTitle: nil),
                ])
                return
            }

            if folders.isEmpty {
                template.updateSections([
                    browseSection,
                    self.emptyStateSection(text: strings.empty),
                ])
                return
            }

            let imageSize = CPListImageRowItem.maximumImageSize
            let maxImages = 8

            // Placeholder image
            let placeholder: UIImage = {
                let renderer = UIGraphicsImageRenderer(size: imageSize)
                return renderer.image { ctx in
                    UIColor.secondarySystemBackground.setFill()
                    ctx.fill(CGRect(origin: .zero, size: imageSize))
                    if let symbol = UIImage(systemName: "music.note") {
                        let config = UIImage.SymbolConfiguration(pointSize: imageSize.height * 0.35, weight: .medium)
                        let tinted = symbol.withConfiguration(config)
                            .withTintColor(.secondaryLabel, renderingMode: .alwaysOriginal)
                        let s = tinted.size
                        tinted.draw(at: CGPoint(x: (imageSize.width - s.width) / 2, y: (imageSize.height - s.height) / 2))
                    }
                }
            }()

            // Build one CPListImageRowItem per folder
            var sections: [CPListSection] = [browseSection]

            for folder in folders {
                let folderItems = (folder.items ?? []).filter { $0.uri != nil }
                guard !folderItems.isEmpty else { continue }

                let displayItems = Array(folderItems.prefix(maxImages))
                var images = Array(repeating: placeholder, count: displayItems.count)

                let row = CPListImageRowItem(text: folder.displayName, images: images)
                row.listImageRowHandler = { [weak self] _, index, completion in
                    guard let self = self else { completion(); return }
                    // Recommendation rows retain "tap to play" for every type;
                    // gate only on connectivity, not on item type.
                    guard self.isReady else {
                        self.showOfflineAlert()
                        completion()
                        return
                    }
                    if index < displayItems.count {
                        CarPlayContentManager.shared.playItem(displayItems[index])
                        self.pushNowPlayingTemplate(animated: true)
                    }
                    completion()
                }

                let section = CPListSection(items: [row], header: nil, sectionIndexTitle: nil)
                sections.append(section)

                // Load artwork asynchronously
                for (i, item) in displayItems.enumerated() {
                    guard let imageUrl = item.image(type: ImageType.thumb)?.url else { continue }
                    CarPlayImageLoader.shared.loadImage(from: imageUrl) { image in
                        guard let image = image else { return }
                        images[i] = image
                        row.update(images)
                    }
                }
            }

            template.updateSections(sections)
        }
    }

    // MARK: - Navigation Helpers

    /// Centralize template pushes to keep track of how many we have and not go
    /// over five, a hard-coded CarPlay limit
    private func safePushTemplate(_ template: CPTemplate, animated: Bool) {
        guard let interfaceController = interfaceController else { return }
        if interfaceController.templates.count >= 5 {
            interfaceController.popToRootTemplate(animated: false) { [weak interfaceController] _, _ in
                interfaceController?.pushTemplate(template, animated: animated, completion: self.logTemplateError)
            }
            return
        }
        interfaceController.pushTemplate(template, animated: animated, completion: logTemplateError)
    }

    /// Safely navigate to the singleton `CPNowPlayingTemplate`
    private func pushNowPlayingTemplate(animated: Bool) {
        guard let interfaceController = interfaceController else { return }
        if interfaceController.topTemplate === CPNowPlayingTemplate.shared {
            return
        }
        if interfaceController.templates.contains(where: { $0 === CPNowPlayingTemplate.shared }) {
            interfaceController.pop(
                to: CPNowPlayingTemplate.shared,
                animated: animated,
                completion: logTemplateError
            )
            return
        }
        safePushTemplate(CPNowPlayingTemplate.shared, animated: animated)
    }

    private func pushBrowseGrid() {
        // Gate at entry. The grid template's category fetchers would otherwise
        // spin indefinitely on a dead transport.
        guard isReady else { showOfflineAlert(); return }
        guard let strings = strings else { return }
        let imageSize = CGSize(width: 100, height: 100)
        let manager = CarPlayContentManager.shared

        // Full catalog of CarPlay-supported browse categories, keyed by LibraryCategory.name.
        // Symbols match Material Design icons from HomeScreen.kt LibraryRow;
        // titles come from the shared catalog so they follow the app locale.
        typealias CategoryEntry = (title: String, symbol: String, fetcher: (@escaping ([CPListItem]?) -> Void) -> Void)
        let allCategories: [String: CategoryEntry] = [
            "ARTISTS":    (strings.artists,    "mic.fill",                          manager.fetchArtists),
            "ALBUMS":     (strings.albums,     "opticaldisc.fill",                  manager.fetchAlbums),
            "PLAYLISTS":  (strings.playlists,  "list.bullet.rectangle.fill",        manager.fetchPlaylists),
            "PODCASTS":   (strings.podcasts,   "antenna.radiowaves.left.and.right", manager.fetchPodcasts),
            "RADIOS":     (strings.radio,      "radio.fill",                        manager.fetchRadioStations),
            "AUDIOBOOKS": (strings.audiobooks, "book.fill",                         manager.fetchAudiobooks),
        ]

        // Apply the user's Car Tabs ordering and visibility (Settings → Car → Tabs).
        // Falls back to the full default set when no config is stored.
        let configuredNames = manager.carBrowseCategories()
        let categories: [CategoryEntry] = configuredNames.compactMap { allCategories[$0] }

        let buttons = categories.map { category -> CPGridButton in
            let image = Self.dynamicCategoryImage(symbol: category.symbol, size: imageSize)
            return CPGridButton(titleVariants: [category.title], image: image) { [weak self] _ in
                self?.pushCategoryTemplate(title: category.title, fetcher: category.fetcher)
            }
        }
        let gridTemplate = CPGridTemplate(title: strings.browse, gridButtons: buttons)
        self.safePushTemplate(gridTemplate, animated: true)
    }

    /// Mirrors `pushDrilldown`'s shape but targets the simpler
    /// "list of CPListItem" fetchers used for Browse → Category.
    private func pushCategoryTemplate(
        title: String,
        fetcher: (@escaping ([CPListItem]?) -> Void) -> Void
    ) {
        guard let strings = strings else { return }
        let template = CPListTemplate(title: title, sections: [])
        let loadingItem = CPListItem(text: strings.loading, detailText: nil)
        template.updateSections([CPListSection(items: [loadingItem])])

        self.safePushTemplate(template, animated: true)

        fetcher { [weak self] items in
            guard let self = self else { return }
            if let items = items {
                if items.isEmpty {
                    template.updateSections([self.emptyStateSection(text: strings.empty)])
                } else {
                    self.attachHandlers(to: items)
                    template.updateSections([CPListSection(items: items)])
                }
            } else {
                template.updateSections([CPListSection(items: [self.disconnectedRow(strings)])])
            }
        }
    }

    // MARK: - Item Selection

    private func attachHandlers(to items: [CPListItem]) {
        for item in items {
            item.handler = { [weak self] listItem, completion in
                self?.handleItemSelection(listItem)
                completion()
            }
        }
    }

    /// Type-aware dispatch: container items (Artist, Album, Playlist, Podcast) drill
    /// in to their contained items; leaf items (Track, RadioStation, Audiobook,
    /// PodcastEpisode) play and push Now Playing.
    private func handleItemSelection(_ item: CPSelectableListItem) {
        // Drop offline taps with a visible alert.
        guard isReady else { showOfflineAlert(); return }
        guard
            let cpListItem = item as? CPListItem,
            let mediaItem = cpListItem.userInfo as? AppMediaItem
        else { return }

        if let artist = mediaItem as? Artist {
            pushAlbumsForArtist(artist)
        } else if let album = mediaItem as? Album {
            pushTracksForAlbum(album)
        } else if let playlist = mediaItem as? Playlist {
            pushTracksForPlaylist(playlist)
        } else if let podcast = mediaItem as? Podcast {
            pushEpisodesForPodcast(podcast)
        } else {
            // Track / RadioStation / Audiobook / PodcastEpisode — leaf items run the per-kind
            // configured tap action. Only push Now Playing when it actually starts playback
            // (a configured "add to queue" tap is non-disruptive).
            let dispatched = CarPlayContentManager.shared.playWithDefault(mediaItem)
            if let name = dispatched, Self.actionStartsPlayback(name) {
                pushNowPlayingTemplate(animated: true)
            }
        }
    }

    // MARK: - Drilldown push helpers

    private func pushAlbumsForArtist(_ artist: Artist) {
        guard let strings = strings else { return }
        pushDrilldown(
            title: strings.albumsByArtist(name: artist.displayName),
            bulkActionParent: artist
        ) { completion in
            CarPlayContentManager.shared.fetchAlbumsForArtist(artist, completion: completion)
        }
    }

    private func pushTracksForAlbum(_ album: Album) {
        pushDrilldown(
            title: album.displayName,
            bulkActionParent: album
        ) { completion in
            CarPlayContentManager.shared.fetchTracksForAlbum(album, completion: completion)
        }
    }

    private func pushTracksForPlaylist(_ playlist: Playlist) {
        pushDrilldown(
            title: playlist.displayName,
            bulkActionParent: playlist
        ) { completion in
            CarPlayContentManager.shared.fetchTracksForPlaylist(playlist, completion: completion)
        }
    }

    private func pushEpisodesForPodcast(_ podcast: Podcast) {
        pushDrilldown(
            title: podcast.displayName,
            bulkActionParent: podcast
        ) { completion in
            CarPlayContentManager.shared.fetchEpisodesForPodcast(podcast, completion: completion)
        }
    }

    /// Push a loading template, fire `fetcher`, swap rows in on result —
    /// empty-state on `[]`, disconnected row on `nil` (timeout).
    private func pushDrilldown(
        title: String,
        bulkActionParent: AppMediaItem,
        fetcher: @escaping (@escaping ([CPListItem]?) -> Void) -> Void
    ) {
        guard let strings = strings else { return }
        let template = CPListTemplate(title: title, sections: [])
        let loadingItem = CPListItem(text: strings.loading, detailText: nil)
        template.updateSections([CPListSection(items: [loadingItem])])
        self.safePushTemplate(template, animated: true)

        fetcher { [weak self] items in
            guard let self = self else { return }
            if let items = items {
                if items.isEmpty {
                    template.updateSections([self.emptyStateSection(text: strings.empty)])
                } else {
                    self.attachHandlers(to: items)
                    let prefix = self.bulkActionRows(for: bulkActionParent)
                    template.updateSections([CPListSection(items: prefix + items)])
                }
            } else {
                template.updateSections([CPListSection(items: [self.disconnectedRow(strings)])])
            }
        }
    }

    // MARK: - Bulk actions

    private func bulkActionRows(for parent: AppMediaItem) -> [CPListItem] {
        guard let strings = strings else { return [] }
        // Rows are user-configured per browsable kind (Settings → Car), shared with Android Auto.
        return CarPlayContentManager.shared.bulkActionNames(for: parent).map { name in
            let row = CPListItem(
                text: strings.bulkActionTitle(name: name),
                detailText: nil,
                image: UIImage(systemName: Self.bulkActionSymbol(name))
            )
            row.handler = { [weak self] _, completion in
                self?.handleBulkAction(parent: parent, actionName: name)
                completion()
            }
            return row
        }
    }

    private func handleBulkAction(parent: AppMediaItem, actionName: String) {
        guard isReady else { showOfflineAlert(); return }
        let dispatched = CarPlayContentManager.shared.playBulkAction(parent, actionName: actionName)
        // Push Now Playing only for actions that start playback; queue-additive actions are
        // non-disruptive so the user stays on the drilldown to keep stacking adds.
        if dispatched && Self.actionStartsPlayback(actionName) {
            pushNowPlayingTemplate(animated: true)
        }
    }

    // DefaultClickAction.name values mirrored from the shared Kotlin enum. Presentation only —
    // dispatch and gating live in Kotlin (KmpHelper / CarActionPrefs).
    private static func actionStartsPlayback(_ name: String) -> Bool {
        switch name {
        case "ADD_TO_QUEUE", "INSERT_NEXT": return false
        default: return true // PLAY_NOW, INSERT_NEXT_AND_PLAY, START_RADIO
        }
    }

    private static func bulkActionSymbol(_ name: String) -> String {
        switch name {
        case "ADD_TO_QUEUE": return "text.badge.plus"
        case "INSERT_NEXT": return "text.insert"
        case "INSERT_NEXT_AND_PLAY": return "play.circle"
        case "START_RADIO": return "dot.radiowaves.left.and.right"
        default: return "play.fill" // PLAY_NOW
        }
    }

    // MARK: - Shared affordance rows

    /// Non-tappable row — server answered, no results.
    private func emptyStateSection(text: String) -> CPListSection {
        let item = CPListItem(text: text, detailText: nil)
        item.handler = nil
        return CPListSection(items: [item], header: nil, sectionIndexTitle: nil)
    }

    /// Non-tappable row — fetcher timed out. Library auto-recovers via the
    /// readiness sub; drilldowns require the user to back out and re-enter.
    private func disconnectedRow(_ strings: CarPlayStrings) -> CPListItem {
        let item = CPListItem(
            text: strings.disconnected,
            detailText: nil,
            image: UIImage(systemName: "wifi.exclamationmark")
        )
        item.handler = nil
        return item
    }
}
