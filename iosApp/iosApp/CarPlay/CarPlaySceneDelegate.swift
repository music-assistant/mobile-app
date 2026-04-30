import Foundation
import CarPlay
import ComposeApp

class CarPlaySceneDelegate: UIResponder, CPTemplateApplicationSceneDelegate {

    var interfaceController: CPInterfaceController?

    // MARK: - CPTemplateApplicationSceneDelegate

    func templateApplicationScene(_ templateApplicationScene: CPTemplateApplicationScene, didConnect interfaceController: CPInterfaceController) {
        self.interfaceController = interfaceController
        print("CP: Connected to CarPlay")
        KmpHelper.shared.onExternalConsumerActive()
        setupTemplates()
    }

    func templateApplicationScene(_ templateApplicationScene: CPTemplateApplicationScene, didDisconnectInterfaceController interfaceController: CPInterfaceController) {
        self.interfaceController = nil
        KmpHelper.shared.onExternalConsumerInactive()
        print("CP: Disconnected from CarPlay")
    }

    private func setupTemplates() {
        let libraryTemplate = createLibraryTemplate()
        interfaceController?.setRootTemplate(libraryTemplate, animated: true, completion: nil)
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

        // Async load recommendation folders
        loadRecommendations(for: libraryList, browseSection: browseSection)

        return libraryList
    }

    // MARK: - Data Loading Helpers

    private func loadRecommendations(for template: CPListTemplate, browseSection: CPListSection) {
        CarPlayContentManager.shared.fetchRecommendationFolders { [weak self] (folders: [AppMediaItem.RecommendationFolder]) in
            guard let self = self else { return }

            if folders.isEmpty {
                let emptyItem = CPListItem(text: "No recommendations", detailText: nil)
                let emptySection = CPListSection(items: [emptyItem], header: nil, sectionIndexTitle: nil)
                template.updateSections([browseSection, emptySection])
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

                let row = CPListImageRowItem(text: folder.title, images: images)
                row.listImageRowHandler = { [weak self] _, index, completion in
                    if index < displayItems.count {
                        CarPlayContentManager.shared.playItem(displayItems[index])
                        self?.interfaceController?.pushTemplate(CPNowPlayingTemplate.shared, animated: true, completion: nil)
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
        let imageSize = CGSize(width: 100, height: 100)
        let manager = CarPlayContentManager.shared
        let categories: [(title: String, fetcher: (@escaping ([CPListItem]) -> Void) -> Void)] = [
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

    private func pushCategoryTemplate(
        title: String,
        fetcher: (@escaping ([CPListItem]) -> Void) -> Void
    ) {
        let template = CPListTemplate(title: title, sections: [])
        let loadingItem = CPListItem(text: "Loading...", detailText: nil)
        template.updateSections([CPListSection(items: [loadingItem])])

        self.interfaceController?.pushTemplate(template, animated: true, completion: nil)

        fetcher { [weak self] items in
            self?.attachHandlers(to: items)
            template.updateSections([CPListSection(items: items)])
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

    private func handleItemSelection(_ item: CPSelectableListItem) {
        if let cpListItem = item as? CPListItem,
           let mediaItem = cpListItem.userInfo as? AppMediaItem {
            CarPlayContentManager.shared.playItem(mediaItem)
            self.interfaceController?.pushTemplate(CPNowPlayingTemplate.shared, animated: true, completion: nil)
        }
    }
}

