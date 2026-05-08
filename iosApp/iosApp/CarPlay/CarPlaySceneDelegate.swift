import Foundation
import CarPlay
import ComposeApp
import os.log

/// CarPlay lifecycle markers. Logged at `.default` so they survive a
/// post-drive sysdiagnose (`.info`/`.debug` are in-memory only).
private let cpLog = OSLog(subsystem: "io.music-assistant.client", category: "CarPlay")

class CarPlaySceneDelegate: UIResponder, CPTemplateApplicationSceneDelegate {

    var interfaceController: CPInterfaceController?

    // MARK: - Readiness state
    //
    // `isReady` mirrors `serviceClient.isReadyForCommands` so synchronous
    // tap-time checks don't have to await the StateFlow. Updated by the
    // `readinessSubscription` callback on the main thread.
    private var isReady: Bool = false
    private var readinessSubscription: Cancellable?

    // Weakly held so a connectivity restore can re-fire the homepage fetch
    // without retaining the template after CarPlay disconnects.
    private weak var libraryTemplate: CPListTemplate?
    private weak var libraryBrowseSection: CPListSection?

    // One-shot subscription that pushes Now Playing the first time
    // local-player state becomes non-null after `setupTemplates()` runs.
    // Cancelled either on first push or on disconnect.
    private var initialPushSubscription: Cancellable?

    /// Monotonic id for in-flight Library recommendation fetches so a slow
    /// first attempt (fired before auth) can't overwrite a faster second
    /// attempt (fired by `refreshLibraryOnReconnect` once readiness flipped).
    private var recommendationsFetchGen: Int = 0

    // MARK: - CPTemplateApplicationSceneDelegate

    func templateApplicationScene(_ templateApplicationScene: CPTemplateApplicationScene, didConnect interfaceController: CPInterfaceController) {
        self.interfaceController = interfaceController
        os_log("CP: didConnect", log: cpLog, type: .default)
        // Reset per-session state for a clean reconnect.
        recommendationsFetchGen = 0
        isReady = false
        KmpHelper.shared.onExternalConsumerActive()
        // The initial fetch is driven from handleReadinessChange's first
        // emission, not from setupTemplates — see createLibraryTemplate.
        // `(Boolean) -> Unit` from Kotlin exposes as `KotlinBoolean`; unbox.
        readinessSubscription = KmpHelper.shared.observeReadiness { [weak self] ready in
            self?.handleReadinessChange(ready.boolValue)
        }
        setupTemplates()
    }

    func templateApplicationScene(_ templateApplicationScene: CPTemplateApplicationScene, didDisconnectInterfaceController interfaceController: CPInterfaceController) {
        // Cancel subscriptions before tearing down state to avoid the
        // callbacks racing with a nil interfaceController.
        readinessSubscription?.cancel()
        readinessSubscription = nil
        initialPushSubscription?.cancel()
        initialPushSubscription = nil
        libraryTemplate = nil
        libraryBrowseSection = nil
        self.interfaceController = nil
        KmpHelper.shared.onExternalConsumerInactive()
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
              interfaceController.presentedTemplate == nil
        else { return }
        let alert = CPAlertTemplate(
            titleVariants: ["Not connected — try again when reconnected"],
            actions: [
                CPAlertAction(title: "OK", style: .default) { [weak self] _ in
                    self?.interfaceController?.dismissTemplate(animated: true, completion: nil)
                }
            ]
        )
        interfaceController.presentTemplate(alert, animated: true, completion: nil)
    }

    /// Pushes Library root and arms a one-shot Now Playing push for when
    /// the local player gains a track (subscription-based because the
    /// local-player value is cleared on backgrounded disconnect).
    private func setupTemplates() {
        let libraryTemplate = createLibraryTemplate()
        interfaceController?.setRootTemplate(libraryTemplate, animated: true) { [weak self] _, _ in
            self?.subscribeToLocalPlayerForInitialPush()
        }
    }

    private func subscribeToLocalPlayerForInitialPush() {
        // No lock needed — KmpHelper.mainScope is pinned to Dispatchers.Main.
        var hasPushed = false
        initialPushSubscription = KmpHelper.shared.observeLocalPlayerPresence { [weak self] present in
            guard let self = self, !hasPushed, present.boolValue else { return }
            // If the user has navigated past Library root, pushing Now
            // Playing on top of their drilldown would be jarring. Identity
            // comparison is safe because we hold a weak reference to the
            // same template instance set as root.
            if self.interfaceController?.topTemplate === self.libraryTemplate {
                self.interfaceController?.pushTemplate(
                    CPNowPlayingTemplate.shared,
                    animated: false,
                    completion: nil
                )
            }
            hasPushed = true
            self.initialPushSubscription?.cancel()
            self.initialPushSubscription = nil
        }
    }

    // MARK: - UI Construction

    // Match Material Design icons from HomeScreen.kt LibraryRow
    private static let categoryIcons: [(name: String, symbol: String)] = [
        ("Artists", "mic.fill"),                          // Icons.Default.Mic
        ("Albums", "opticaldisc.fill"),                   // Icons.Default.Album
        ("Tracks", "music.note"),                         // Icons.Default.MusicNote
        ("Playlists", "list.bullet.rectangle.fill"),      // Icons.AutoMirrored.Filled.FeaturedPlayList
        ("Audiobooks", "book.fill"),                      // Icons.AutoMirrored.Filled.MenuBook
        ("Podcasts", "antenna.radiowaves.left.and.right"), // Icons.Default.Podcasts
        ("Radio", "radio.fill"),                          // Icons.Default.Radio
    ]

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

    private func createLibraryTemplate() -> CPListTemplate {
        // "Browse" item pushes a CPGridTemplate with all categories
        let browseIcon = Self.dynamicCategoryImage(symbol: "square.grid.2x2.fill", size: CPListItem.maximumImageSize)
        let browseItem = CPListItem(text: "Browse", detailText: "Artists, Albums, Tracks & more", image: browseIcon)
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
        let loadingItem = CPListItem(text: "Loading...", detailText: nil)
        let loadingSection = CPListSection(
            items: [loadingItem],
            header: nil,
            sectionIndexTitle: nil
        )

        let libraryList = CPListTemplate(title: "Library", sections: [browseSection, loadingSection])

        // Weak refs feed `refreshLibraryOnReconnect`, which is the sole
        // path that fires `loadRecommendations` — including the initial
        // fetch (driven by the readiness sub's first emission).
        self.libraryTemplate = libraryList
        self.libraryBrowseSection = browseSection

        return libraryList
    }

    // MARK: - Data Loading Helpers

    private func loadRecommendations(for template: CPListTemplate, browseSection: CPListSection) {
        recommendationsFetchGen += 1
        let myGen = recommendationsFetchGen
        os_log("CP: loadRecommendations gen=%{public}d", log: cpLog, type: .default, myGen)
        CarPlayContentManager.shared.fetchRecommendationFolders { [weak self] (folders: [AppMediaItem.RecommendationFolder]?) in
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
                    CPListSection(items: [self.disconnectedRow()], header: nil, sectionIndexTitle: nil),
                ])
                return
            }

            if folders.isEmpty {
                template.updateSections([
                    browseSection,
                    self.emptyStateSection(text: "No recommendations"),
                ])
                return
            }

            let serverUrl = KmpHelper.shared.getServerUrl()
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
                        self.interfaceController?.pushTemplate(CPNowPlayingTemplate.shared, animated: true, completion: nil)
                    }
                    completion()
                }

                let section = CPListSection(items: [row], header: nil, sectionIndexTitle: nil)
                sections.append(section)

                // Load artwork asynchronously
                for (i, item) in displayItems.enumerated() {
                    guard let imageUrl = item.imageInfo?.url(serverUrl: serverUrl) else { continue }
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

    private func pushBrowseGrid() {
        // Gate at entry. The grid template's category fetchers would otherwise
        // spin indefinitely on a dead transport.
        guard isReady else { showOfflineAlert(); return }
        let imageSize = CGSize(width: 100, height: 100)
        let manager = CarPlayContentManager.shared
        let categories: [(title: String, fetcher: (@escaping ([CPListItem]?) -> Void) -> Void)] = [
            ("Artists",    manager.fetchArtists),
            ("Albums",     manager.fetchAlbums),
            ("Tracks",     manager.fetchTracks),
            ("Playlists",  manager.fetchPlaylists),
            ("Audiobooks", manager.fetchAudiobooks),
            ("Podcasts",   manager.fetchPodcasts),
            ("Radio",      manager.fetchRadioStations),
        ]
        let buttons = zip(Self.categoryIcons, categories).map { icon, category -> CPGridButton in
            let image = Self.dynamicCategoryImage(symbol: icon.symbol, size: imageSize)
            return CPGridButton(titleVariants: [icon.name], image: image) { [weak self] _ in
                self?.pushCategoryTemplate(title: category.title, fetcher: category.fetcher)
            }
        }
        let gridTemplate = CPGridTemplate(title: "Browse", gridButtons: buttons)
        self.interfaceController?.pushTemplate(gridTemplate, animated: true, completion: nil)
    }

    /// Mirrors `pushDrilldown`'s shape but targets the simpler
    /// "list of CPListItem" fetchers used for Browse → Category.
    private func pushCategoryTemplate(
        title: String,
        fetcher: (@escaping ([CPListItem]?) -> Void) -> Void
    ) {
        let template = CPListTemplate(title: title, sections: [])
        let loadingItem = CPListItem(text: "Loading...", detailText: nil)
        template.updateSections([CPListSection(items: [loadingItem])])

        self.interfaceController?.pushTemplate(template, animated: true, completion: nil)

        fetcher { [weak self] items in
            guard let self = self else { return }
            if let items = items {
                if items.isEmpty {
                    template.updateSections([emptyStateSection(text: "No \(title.lowercased())")])
                } else {
                    self.attachHandlers(to: items)
                    template.updateSections([CPListSection(items: items)])
                }
            } else {
                template.updateSections([CPListSection(items: [disconnectedRow()])])
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

    /// Type-aware dispatch: container items (Artist, Album, Playlist) drill
    /// in to their contained items; leaf items (Track, RadioStation, Podcast,
    /// Audiobook, PodcastEpisode) play and push Now Playing.
    private func handleItemSelection(_ item: CPSelectableListItem) {
        // Drop offline taps with a visible alert.
        guard isReady else { showOfflineAlert(); return }
        guard
            let cpListItem = item as? CPListItem,
            let mediaItem = cpListItem.userInfo as? AppMediaItem
        else { return }

        if let artist = mediaItem as? AppMediaItem.Artist {
            pushAlbumsForArtist(artist)
        } else if let album = mediaItem as? AppMediaItem.Album {
            pushTracksForAlbum(album)
        } else if let playlist = mediaItem as? AppMediaItem.Playlist {
            pushTracksForPlaylist(playlist)
        } else {
            // Track / RadioStation / Podcast / Audiobook / PodcastEpisode —
            // leaf-level items play immediately.
            CarPlayContentManager.shared.playItem(mediaItem)
            playAndShowNowPlaying()
        }
    }

    /// `popToRoot` first, then push Now Playing — CarPlay caps the template
    /// stack at 5, and Browse → Category → Artist → Albums → Tracks already
    /// fills it. A naive push from the leaf crashes with a hierarchy-depth
    /// exception.
    private func playAndShowNowPlaying() {
        guard let interfaceController = interfaceController else { return }
        os_log("CP: playAndShowNowPlaying — popToRoot then push NowPlaying",
               log: cpLog, type: .default)
        interfaceController.popToRootTemplate(animated: false) { [weak self] _, _ in
            self?.interfaceController?.pushTemplate(
                CPNowPlayingTemplate.shared,
                animated: true,
                completion: nil
            )
        }
    }

    // MARK: - Drilldown push helpers

    private func pushAlbumsForArtist(_ artist: AppMediaItem.Artist) {
        pushDrilldown(
            title: "Albums by \(artist.displayName)",
            emptyText: "No albums for \(artist.displayName)"
        ) { completion in
            CarPlayContentManager.shared.fetchAlbumsForArtist(artist, completion: completion)
        }
    }

    private func pushTracksForAlbum(_ album: AppMediaItem.Album) {
        pushDrilldown(
            title: album.displayName,
            emptyText: "No tracks in this album"
        ) { completion in
            CarPlayContentManager.shared.fetchTracksForAlbum(album, completion: completion)
        }
    }

    private func pushTracksForPlaylist(_ playlist: AppMediaItem.Playlist) {
        pushDrilldown(
            title: playlist.displayName,
            emptyText: "No tracks in this playlist"
        ) { completion in
            CarPlayContentManager.shared.fetchTracksForPlaylist(playlist, completion: completion)
        }
    }

    /// Push a loading template, fire `fetcher`, swap rows in on result —
    /// empty-state on `[]`, disconnected row on `nil` (timeout).
    private func pushDrilldown(
        title: String,
        emptyText: String,
        fetcher: @escaping (@escaping ([CPListItem]?) -> Void) -> Void
    ) {
        let template = CPListTemplate(title: title, sections: [])
        let loadingItem = CPListItem(text: "Loading...", detailText: nil)
        template.updateSections([CPListSection(items: [loadingItem])])
        self.interfaceController?.pushTemplate(template, animated: true, completion: nil)

        fetcher { [weak self] items in
            guard let self = self else { return }
            if let items = items {
                if items.isEmpty {
                    template.updateSections([emptyStateSection(text: emptyText)])
                } else {
                    self.attachHandlers(to: items)
                    template.updateSections([CPListSection(items: items)])
                }
            } else {
                template.updateSections([CPListSection(items: [disconnectedRow()])])
            }
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
    private func disconnectedRow() -> CPListItem {
        let item = CPListItem(
            text: "Disconnected — reconnecting…",
            detailText: nil,
            image: UIImage(systemName: "wifi.exclamationmark")
        )
        item.handler = nil
        return item
    }
}
