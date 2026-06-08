import Foundation
import CarPlay
import ComposeApp

// MARK: - Image Loader

class CarPlayImageLoader {
    static let shared = CarPlayImageLoader()

    private let cache = NSCache<NSString, UIImage>()

    // Routes through KmpHelper so `mawebrtc://` synthetic URLs (WebRTC mode) resolve via
    // the data-channel HTTP proxy instead of failing in URLSession.
    func loadImage(from urlString: String, completion: @escaping (UIImage?) -> Void) {
        let cacheKey = urlString as NSString
        if let cached = cache.object(forKey: cacheKey) {
            completion(cached)
            return
        }

        _ = KmpHelper.shared.loadArtworkBytes(urlString: urlString) { [weak self] data in
            guard let data = data as Data?, let image = UIImage(data: data) else {
                DispatchQueue.main.async { completion(nil) }
                return
            }
            self?.cache.setObject(image, forKey: cacheKey)
            DispatchQueue.main.async { completion(image) }
        }
    }
}

/// Manages data fetching for CarPlay using KmpHelper
class CarPlayContentManager {
    static let shared = CarPlayContentManager()

    // MARK: - API Calls
    // Fetchers return `[CPListItem]?`: nil = timeout, [] = empty result.

    private func mapItems(_ items: [AppMediaItem]?) -> [CPListItem]? {
        guard let items = items else { return nil }
        return items.compactMap { self.mapToCPListItem($0) }
    }

    func fetchRecommendations(completion: @escaping ([CPListItem]?) -> Void) {
        KmpHelper.shared.fetchRecommendations { items in
            completion(self.mapItems(items))
        }
    }

    func fetchPlaylists(completion: @escaping ([CPListItem]?) -> Void) {
        KmpHelper.shared.fetchPlaylists { items in
            completion(self.mapItems(items))
        }
    }

    func fetchAlbums(completion: @escaping ([CPListItem]?) -> Void) {
        KmpHelper.shared.fetchAlbums { items in
            completion(self.mapItems(items))
        }
    }

    func fetchArtists(completion: @escaping ([CPListItem]?) -> Void) {
        KmpHelper.shared.fetchArtists { items in
            completion(self.mapItems(items))
        }
    }

    func fetchAudiobooks(completion: @escaping ([CPListItem]?) -> Void) {
        KmpHelper.shared.fetchAudiobooks { items in
            completion(self.mapItems(items))
        }
    }

    func fetchTracks(completion: @escaping ([CPListItem]?) -> Void) {
        KmpHelper.shared.fetchTracks { items in
            completion(self.mapItems(items))
        }
    }

    func fetchPodcasts(completion: @escaping ([CPListItem]?) -> Void) {
        KmpHelper.shared.fetchPodcasts { items in
            completion(self.mapItems(items))
        }
    }

    func fetchRadioStations(completion: @escaping ([CPListItem]?) -> Void) {
        KmpHelper.shared.fetchRadioStations { items in
            completion(self.mapItems(items))
        }
    }

    /// Returns nil on timeout. Empty array means "server answered, no folders."
    func fetchRecommendationFolders(completion: @escaping ([RecommendationFolder]?) -> Void) {
        KmpHelper.shared.fetchRecommendationFolders { folders in
            guard let folders = folders else { completion(nil); return }
            completion(Array(folders))
        }
    }

    func search(query: String, completion: @escaping ([CPListItem]?) -> Void) {
        KmpHelper.shared.search(query: query) { items in
            completion(self.mapItems(items))
        }
    }

    // MARK: - Drilldown fetchers
    // Same nil-on-timeout contract as the library fetchers above.

    func fetchAlbumsForArtist(
        _ artist: Artist,
        completion: @escaping ([CPListItem]?) -> Void
    ) {
        KmpHelper.shared.fetchAlbumsByArtist(artist: artist) { items in
            completion(self.mapItems(items))
        }
    }

    func fetchTracksForAlbum(
        _ album: Album,
        completion: @escaping ([CPListItem]?) -> Void
    ) {
        KmpHelper.shared.fetchTracksByAlbum(album: album) { items in
            completion(self.mapItems(items))
        }
    }

    func fetchTracksForPlaylist(
        _ playlist: Playlist,
        completion: @escaping ([CPListItem]?) -> Void
    ) {
        KmpHelper.shared.fetchTracksByPlaylist(playlist: playlist) { items in
            completion(self.mapItems(items))
        }
    }

    // MARK: - Action Handling

    func playItem(_ item: AppMediaItem) {
        play(item, option: .play)
    }

    // Donations train Siri's "play X in Music Assistant" model — fire on
    // any user-initiated dispatch (immediate or queued), since intent to
    // play is what matters, not whether the RPC ultimately landed.
    private func play(_ item: AppMediaItem, option: QueueOption) {
        guard KmpHelper.shared.playOnLocalPlayer(item: item, option: option) else { return }
        SiriIntentHandler.donatePlayed(item)
    }

    // MARK: - Configurable car actions (shared with Android Auto via SettingsRepository)

    /// Ordered, CarPlay-supported bulk-action names configured for a browsable container's kind.
    func bulkActionNames(for item: AppMediaItem) -> [String] {
        KmpHelper.shared.carBulkActionNames(item: item)
    }

    /// Dispatch a named bulk action (DefaultClickAction.name) onto the container. False on no-op.
    @discardableResult
    func playBulkAction(_ item: AppMediaItem, actionName: String) -> Bool {
        guard KmpHelper.shared.playCarAction(item: item, actionName: actionName) else { return false }
        SiriIntentHandler.donatePlayed(item)
        return true
    }

    /// Dispatch the per-kind configured tap action. Returns the dispatched action name (so the
    /// caller can decide whether to push Now Playing), or nil on failure.
    @discardableResult
    func playWithDefault(_ item: AppMediaItem) -> String? {
        guard let name = KmpHelper.shared.playCarDefaultTap(item: item) else { return nil }
        SiriIntentHandler.donatePlayed(item)
        return name
    }
    
    // MARK: - Helpers
    
    private func mapToCPListItem(_ item: AppMediaItem) -> CPListItem? {
        let title = item.displayName
        let subtitle = item.subtitle

        let listItem = CPListItem(text: title, detailText: subtitle)
        listItem.userInfo = item

        // Set type-appropriate placeholder icon
        let iconName: String
        if item is Audiobook {
            iconName = "book.fill"
        } else if item is RadioStation {
            iconName = "radio.fill"
        } else if item is Album {
            iconName = "square.stack"
        } else if item is Playlist {
            iconName = "music.note.list"
        } else if item is Artist {
            iconName = "person.2.crop.square.stack"
        } else {
            iconName = "music.note"
        }
        listItem.setImage(UIImage(systemName: iconName))

        // Load artwork asynchronously
        if let imageUrl = item.image(type: ImageType.thumb)?.url {
            CarPlayImageLoader.shared.loadImage(from: imageUrl) { image in
                if let image = image {
                    listItem.setImage(image)
                }
            }
        }

        return listItem
    }
}
