import XCTest

final class NativeArtworkPresentationTests: XCTestCase {
    func test_native_decode_failure_invalidates_failed_version_carplay() {
        native_decode_failure_invalidates_failed_version_carplay()
    }

    func native_decode_failure_invalidates_failed_version_carplay() {
        var invalidated: [String] = []
        let failed = NativeArtworkPayload(data: Data([1, 2, 3]), mimeType: "image/png", token: "old")
        XCTAssertNil(NativeArtworkDecoder.decode(
            failed,
            decode: { _ in Optional<String>.none },
            invalidate: { invalidated.append($0) }
        ))
        XCTAssertEqual(invalidated, ["old"])

        invalidated.removeAll()
        let svgMIME = NativeArtworkPayload(data: Data("not raster".utf8), mimeType: "image/svg+xml", token: "svg")
        XCTAssertNil(NativeArtworkDecoder.decode(svgMIME, decode: { _ in Optional<String>.none }, invalidate: { invalidated.append($0) }))
        let sniffedSVG = NativeArtworkPayload(data: Data("  <?xml version=\"1.0\"?><svg/>".utf8), mimeType: "image/png", token: "svg-sniff")
        XCTAssertNil(NativeArtworkDecoder.decode(sniffedSVG, decode: { _ in Optional<String>.none }, invalidate: { invalidated.append($0) }))
        XCTAssertTrue(invalidated.isEmpty)

        var storedVersion = "new"
        let old = NativeArtworkPayload(data: Data([9]), mimeType: "image/png", token: "old")
        XCTAssertNil(NativeArtworkDecoder.decode(old, decode: { _ in Optional<String>.none }, invalidate: { token in if token == storedVersion { invalidated.append(token) } }))
        XCTAssertTrue(invalidated.isEmpty)
        storedVersion = "old"
        XCTAssertNil(NativeArtworkDecoder.decode(failed, decode: { _ in Optional<String>.none }, invalidate: { token in if token == storedVersion { invalidated.append(token) } }))
        XCTAssertEqual(invalidated, ["old"])

        invalidated.removeAll()
        let unknown = NativeArtworkPayload(data: Data([1, 2, 3]), mimeType: "image/x-custom", token: "unknown")
        XCTAssertNil(NativeArtworkDecoder.decode(unknown, decode: { _ in Optional<String>.none }, invalidate: { invalidated.append($0) }))
        XCTAssertTrue(invalidated.isEmpty)

        let recognizedRaster = NativeArtworkPayload(data: Data([0xFF, 0xD8, 0xFF]), mimeType: nil, token: "jpeg")
        XCTAssertNil(NativeArtworkDecoder.decode(recognizedRaster, decode: { _ in Optional<String>.none }, invalidate: { invalidated.append($0) }))
        XCTAssertEqual(invalidated, ["jpeg"])
    }

    func test_native_decode_failure_invalidates_failed_version_nowplaying() {
        native_decode_failure_invalidates_failed_version_nowplaying()
    }

    func native_decode_failure_invalidates_failed_version_nowplaying() {
        var invalidated: [Int] = []
        let failed = NativeArtworkPayload(data: Data([1]), mimeType: "image/jpeg", token: 7)
        XCTAssertNil(NativeArtworkDecoder.decode(failed, decode: { _ in Optional<String>.none }, invalidate: { invalidated.append($0) }))
        XCTAssertEqual(invalidated, [7])

        invalidated.removeAll()
        let svgMIME = NativeArtworkPayload(data: Data("<svg/>".utf8), mimeType: "image/svg+xml", token: 8)
        let xmlSniff = NativeArtworkPayload(data: Data("\n<svg xmlns=\"x\"/>".utf8), mimeType: nil, token: 9)
        XCTAssertNil(NativeArtworkDecoder.decode(svgMIME, decode: { _ in Optional<String>.none }, invalidate: { invalidated.append($0) }))
        XCTAssertNil(NativeArtworkDecoder.decode(xmlSniff, decode: { _ in Optional<String>.none }, invalidate: { invalidated.append($0) }))
        XCTAssertTrue(invalidated.isEmpty)

        var currentToken = 11
        let old = NativeArtworkPayload(data: Data([2]), mimeType: "image/png", token: 10)
        XCTAssertNil(NativeArtworkDecoder.decode(old, decode: { _ in Optional<String>.none }, invalidate: { token in if token == currentToken { invalidated.append(token) } }))
        XCTAssertTrue(invalidated.isEmpty)
        currentToken = 10
        XCTAssertNil(NativeArtworkDecoder.decode(old, decode: { _ in Optional<String>.none }, invalidate: { token in if token == currentToken { invalidated.append(token) } }))
        XCTAssertEqual(invalidated, [10])
    }

    func test_truncated_raster_signatures_with_generic_mime_invalidate() {
        let truncatedPNG = Data([0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0x00])
        let truncatedJPEG = Data([0xFF, 0xD8, 0xFF, 0xE0, 0x00, 0x10, 0x4A, 0x46, 0x49, 0x46])
        var invalidated: [String] = []

        for (data, token) in [(truncatedPNG, "png"), (truncatedJPEG, "jpeg")] {
            let payload = NativeArtworkPayload(data: data, mimeType: "application/octet-stream", token: token)
            XCTAssertNil(NativeArtworkDecoder.decode(payload, decode: { _ in Optional<String>.none }, invalidate: { invalidated.append($0) }))
        }

        XCTAssertEqual(invalidated, ["png", "jpeg"])
    }

    func test_generic_mime_does_not_change_successful_decode() {
        let payload = NativeArtworkPayload(data: Data([0x89, 0x50, 0x4E, 0x47]), mimeType: "application/octet-stream", token: "valid")
        var invalidated: [String] = []

        let image = NativeArtworkDecoder.decode(
            payload,
            decode: { _ in "decoded" },
            invalidate: { invalidated.append($0) }
        )

        XCTAssertEqual(image, "decoded")
        XCTAssertTrue(invalidated.isEmpty)
    }
}
