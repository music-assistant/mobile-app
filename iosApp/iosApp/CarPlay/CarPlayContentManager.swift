import Foundation
import CarPlay
import ComposeApp

// MARK: - Image Loader

class CarPlayImageLoader {
    static let shared = CarPlayImageLoader()

    private let cache = NSCache<NSString, UIImage>()
    private let session = URLSession.shared

    func loadImage(from urlString: String, completion: @escaping (UIImage?) -> Void) {
        let cacheKey = urlString as NSString
        if let cached = cache.object(forKey: cacheKey) {
            completion(cached)
            return
        }

        guard let url = URL(string: urlString) else {
            completion(nil)
            return
        }

        session.dataTask(with: url) { [weak self] data, _, _ in
            guard let data = data, let image = UIImage(data: data) else {
                DispatchQueue.main.async { completion(nil) }
                return
            }
            self?.cache.setObject(image, forKey: cacheKey)
            DispatchQueue.main.async { completion(image) }
        }.resume()
    }
}

/// Manages data fetching for CarPlay using KmpHelper
class CarPlayContentManager {
    static let shared = CarPlayContentManager()

    // MARK: - API Calls

    func fetchRecommendations(completion: @escaping ([CPListItem]) -> Void) {
        KmpHelper.shared.fetchRecommendations { items in
            completion(items.compactMap { self.mapToCPListItem($0) })
        }
    }
    
    func fetchPlaylists(completion: @escaping ([CPListItem]) -> Void) {
        KmpHelper.shared.fetchPlaylists { items in
            completion(items.compactMap { self.mapToCPListItem($0) })
        }
    }
    
    func fetchAlbums(completion: @escaping ([CPListItem]) -> Void) {
        KmpHelper.shared.fetchAlbums { items in
            completion(items.compactMap { self.mapToCPListItem($0) })
        }
    }
    
    func fetchArtists(completion: @escaping ([CPListItem]) -> Void) {
        KmpHelper.shared.fetchArtists { items in
            completion(items.compactMap { self.mapToCPListItem($0) })
        }
    }

    func fetchAudiobooks(completion: @escaping ([CPListItem]) -> Void) {
        KmpHelper.shared.fetchAudiobooks { items in
            completion(items.compactMap { self.mapToCPListItem($0) })
        }
    }

    func fetchTracks(completion: @escaping ([CPListItem]) -> Void) {
        KmpHelper.shared.fetchTracks { items in
            completion(items.compactMap { self.mapToCPListItem($0) })
        }
    }

    func fetchPodcasts(completion: @escaping ([CPListItem]) -> Void) {
        KmpHelper.shared.fetchPodcasts { items in
            completion(items.compactMap { self.mapToCPListItem($0) })
        }
    }

    func fetchRadioStations(completion: @escaping ([CPListItem]) -> Void) {
        KmpHelper.shared.fetchRadioStations { items in
            completion(items.compactMap { self.mapToCPListItem($0) })
        }
    }

    func fetchRecommendationFolders(completion: @escaping ([AppMediaItem.RecommendationFolder]) -> Void) {
        KmpHelper.shared.fetchRecommendationFolders { folders in
            completion(Array(folders))
        }
    }

    func search(query: String, completion: @escaping ([CPListItem]) -> Void) {
        KmpHelper.shared.search(query: query) { items in
             completion(items.compactMap { self.mapToCPListItem($0) })
        }
    }
    
    // MARK: - Action Handling

    func playItem(_ item: AppMediaItem) {
        KmpHelper.shared.playMediaItem(item: item)
    }
    
    // MARK: - Helpers
    
    private func mapToCPListItem(_ item: AppMediaItem) -> CPListItem? {
        let title = item.name
        let subtitle = item.subtitle

        let listItem = CPListItem(text: title, detailText: subtitle)
        listItem.userInfo = item

        // Set type-appropriate placeholder icon
        let iconName: String
        if item is AppMediaItem.Audiobook {
            iconName = "book.fill"
        } else if item is AppMediaItem.RadioStation {
            iconName = "radio.fill"
        } else if item is AppMediaItem.Album {
            iconName = "square.stack"
        } else if item is AppMediaItem.Playlist {
            iconName = "music.note.list"
        } else if item is AppMediaItem.Artist {
            iconName = "person.2.crop.square.stack"
        } else {
            iconName = "music.note"
        }
        listItem.setImage(UIImage(systemName: iconName))

        // Load artwork asynchronously
        let serverUrl = KmpHelper.shared.getServerUrl()
        if let imageUrl = item.imageInfo?.url(serverUrl: serverUrl) {
            CarPlayImageLoader.shared.loadImage(from: imageUrl) { image in
                if let image = image {
                    listItem.setImage(image)
                }
            }
        }

        return listItem
    }
}
