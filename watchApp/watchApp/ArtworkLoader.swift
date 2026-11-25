import Foundation
import ImageIO
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

    /// Row-sized covers, which are a different job from the one behind the screen: decoded
    /// down by ImageIO rather than at full size, and kept by the screenful rather than one
    /// at a time. A miss is kept as a nil, so a track the server has no cover for is not
    /// fetched again every time its row scrolls back into view.
    private var thumbnails: [URL: UIImage?] = [:]
    private var thumbnailRecency: [URL] = []

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

    /// A row-sized cover, or nil when there is none to be had.
    func thumbnail(for urlString: String?) async -> UIImage? {
        guard let urlString, let url = URL(string: urlString) else { return nil }
        if let hit = thumbnails[url] {
            touchThumbnail(url)
            return hit
        }
        let image = await load(url).flatMap { Self.downsample($0, to: thumbnailPixels) }
        thumbnails[url] = image
        touchThumbnail(url)
        while thumbnailRecency.count > thumbnailEntries {
            thumbnails.removeValue(forKey: thumbnailRecency.removeFirst())
        }
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

    private func touchThumbnail(_ url: URL) {
        thumbnailRecency.removeAll { $0 == url }
        thumbnailRecency.append(url)
    }

    /// Decodes straight to the size a row needs. `UIImage(data:)` would hand back the full
    /// cover and leave the GPU to shrink it, which is a megabyte a row on a watch.
    private static func downsample(_ data: Data, to maxPixels: Int) -> UIImage? {
        let source = CGImageSourceCreateWithData(
            data as CFData,
            [kCGImageSourceShouldCache: false] as CFDictionary
        )
        guard let source else { return nil }
        let options: [CFString: Any] = [
            kCGImageSourceCreateThumbnailFromImageAlways: true,
            kCGImageSourceCreateThumbnailWithTransform: true,
            kCGImageSourceShouldCacheImmediately: true,
            kCGImageSourceThumbnailMaxPixelSize: maxPixels,
        ]
        guard let image = CGImageSourceCreateThumbnailAtIndex(source, 0, options as CFDictionary)
        else { return nil }
        return UIImage(cgImage: image)
    }

    private static let requestTimeout: TimeInterval = 15
    private let maxBytes = 8 * 1024 * 1024
    private let cacheEntries = 12
    private let thumbnailPixels = 96
    private let thumbnailEntries = 64
}
