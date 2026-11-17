import ComposeApp
import Foundation
import Network

/// One browse session for the connect screen. The TXT record of a `_mass._tcp` service holds the
/// server info with its `base_url`, so the browser reads it and resolves nothing.
/// The service type must be listed under `NSBonjourServices` in Info.plist.
final class MassServiceBrowser: NSObject, NativeServiceBrowser {
    private var browser: NWBrowser?

    func start(serviceType: String, onChange: @escaping ([[String: String]]) -> Void) {
        let browser = NWBrowser(
            for: .bonjourWithTXTRecord(type: serviceType, domain: nil),
            using: NWParameters()
        )
        browser.browseResultsChangedHandler = { results, _ in
            onChange(results.compactMap { result in
                guard case .bonjour(let txt) = result.metadata else { return nil }
                return txt.dictionary
            })
        }
        browser.stateUpdateHandler = { state in
            if case .failed(let error) = state {
                NativeLog.shared.warn(tag: "MassServiceBrowser", message: "browser .failed: \(error)")
            }
        }
        self.browser = browser
        browser.start(queue: .main)
    }

    func cancel() {
        browser?.browseResultsChangedHandler = nil
        browser?.stateUpdateHandler = nil
        browser?.cancel()
        browser = nil
    }
}
