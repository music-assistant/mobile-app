import Foundation

/// A native artwork response with the exact bytes, MIME type, and repository version
/// needed to report a decode failure without evicting a newer replacement.
struct NativeArtworkPayload<Token> {
    let data: Data
    let mimeType: String?
    let token: Token

    init(data: Data, mimeType: String?, token: Token) {
        self.data = data
        self.mimeType = mimeType
        self.token = token
    }
}

/// Shared native decode policy. SVG/XML is intentionally unsupported by UIImage;
/// those responses remain reusable. A supported-raster decode failure reports only
/// the returned version token for invalidation.
struct NativeArtworkDecoder {
    static func decode<Token, Image>(
        _ result: NativeArtworkPayload<Token>?,
        decode: (Data) -> Image?,
        invalidate: (Token) -> Void
    ) -> Image? {
        guard let result else { return nil }
        guard !isUnsupportedNativeFormat(data: result.data, mimeType: result.mimeType) else {
            return nil
        }
        guard let image = decode(result.data) else {
            guard isRecognizedRaster(data: result.data, mimeType: result.mimeType) else {
                return nil
            }
            invalidate(result.token)
            return nil
        }
        return image
    }

    private static func isUnsupportedNativeFormat(data: Data, mimeType: String?) -> Bool {
        let normalized = normalizedMimeType(mimeType)
        let mimeIsXML = normalized == "image/svg+xml" || normalized == "image/svg" ||
            normalized == "text/xml" || normalized == "application/xml"
        return mimeIsXML || isXMLPrefix(data)
    }

    private static func isRecognizedRaster(data: Data, mimeType: String?) -> Bool {
        if let normalized = normalizedMimeType(mimeType) {
            let rasterMIMEs = ["image/jpeg", "image/png", "image/gif", "image/webp", "image/bmp",
                               "image/tiff", "image/heic", "image/heif"]
            if rasterMIMEs.contains(normalized) {
                return true
            }
        }
        let bytes = Array(data.prefix(12))
        return bytes.starts(with: [0xFF, 0xD8, 0xFF]) ||
            bytes.starts(with: [0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A]) ||
            bytes.starts(with: [0x47, 0x49, 0x46, 0x38]) ||
            (bytes.count >= 12 && Array(bytes[0...3]) == [0x52, 0x49, 0x46, 0x46] && Array(bytes[8...11]) == [0x57, 0x45, 0x42, 0x50]) ||
            bytes.starts(with: [0x42, 0x4D]) ||
            bytes.starts(with: [0x49, 0x49, 0x2A, 0x00]) || bytes.starts(with: [0x4D, 0x4D, 0x00, 0x2A])
    }

    private static func normalizedMimeType(_ mimeType: String?) -> String? {
        guard let mimeType else { return nil }
        return mimeType
            .split(separator: ";", maxSplits: 1, omittingEmptySubsequences: true)
            .first
            .map { $0.trimmingCharacters(in: .whitespacesAndNewlines).lowercased() }
    }

    // Sniff a bounded UTF-8 prefix; an incomplete trailing scalar is harmless.
    private static func isXMLPrefix(_ data: Data) -> Bool {
        let prefix = data.prefix(4096)
        guard let string = String(data: prefix, encoding: .utf8) else { return false }
        let trimmed = string
            .trimmingCharacters(in: .whitespacesAndNewlines)
            .replacingOccurrences(of: "\u{FEFF}", with: "")
            .trimmingCharacters(in: .whitespacesAndNewlines)
            .lowercased()
        return trimmed.hasPrefix("<svg") || trimmed.hasPrefix("<?xml")
    }
}
