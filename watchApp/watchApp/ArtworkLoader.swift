import Foundation
import UIKit

/// Fetches and decodes cover art.
///
/// The watch shows one cover at a time, so this is a plain fetch-and-decode behind a small
/// LRU rather than an image pipeline. The server's imageproxy needs no token.
///
/// There is no colour scoring here, as there is on Wear OS: the cover goes behind the screen
/// blurred, so the album is read off the background itself, and the controls keep the app's
/// accent colour the way watchOS expects them to.
actor ArtworkLoader {
    static let shared = ArtworkLoader()

    private var cache: [URL: UIImage] = [:]
    private var recency: [URL] = []

    private let session: URLSession = {
        let configuration = URLSessionConfiguration.default
        configuration.timeoutIntervalForRequest = ArtworkLoader.requestTimeout
        configuration.waitsForConnectivity = false
        return URLSession(configuration: configuration)
    }()

    /// The cover at [urlString], or nil when there is none, it will not load, or it will not
    /// decode. Every failure is the same to the caller: the screen draws its plain background.
    func artwork(for urlString: String?) async -> UIImage? {
        guard let urlString, let url = URL(string: urlString) else { return nil }
        if let hit = cache[url] {
            touch(url)
            return hit
        }
        guard let data = await load(url), let image = UIImage(data: data) else { return nil }
        store(image, for: url)
        return image
    }

    private func load(_ url: URL) async -> Data? {
        guard let (data, response) = try? await session.data(from: url) else { return nil }
        if let http = response as? HTTPURLResponse, !(200..<300).contains(http.statusCode) {
            return nil
        }
        return data.isEmpty || data.count > maxBytes ? nil : data
    }

    private func store(_ image: UIImage, for url: URL) {
        cache[url] = image
        touch(url)
        while recency.count > cacheEntries {
            cache.removeValue(forKey: recency.removeFirst())
        }
    }

    private func touch(_ url: URL) {
        recency.removeAll { $0 == url }
        recency.append(url)
    }

    private static let requestTimeout: TimeInterval = 15
    private let maxBytes = 8 * 1024 * 1024
    private let cacheEntries = 12
}
