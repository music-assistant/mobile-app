import AuthenticationServices
import ComposeApp
import UIKit

/// Presents the provider's OAuth page in an in-app `ASWebAuthenticationSession`.
///
/// Named for the mechanism rather than the role because `OAuthHandler` is now the
/// exported Obj-C protocol from the Kotlin framework — a class of that name in this
/// target would shadow it and break conformance.
///
/// Staying in-app is the point. The previous implementation handed the URL to Safari,
/// which backgrounded the app; returning by deep link then foregrounded it and triggered
/// a transport reconnect that raced the token delivery. A session keeps the app
/// foregrounded and the websocket connected for the whole flow, and it uses the Safari
/// engine no matter which browser the user has set as default — Chrome silently blocks
/// the JS-initiated custom-scheme redirect this flow depends on.
final class OAuthWebSession: NSObject, OAuthHandler, ASWebAuthenticationPresentationContextProviding {
    /// The session cancels itself when deallocated, so it must be held for its lifetime.
    private var session: ASWebAuthenticationSession?

    /// The session reports dismissal through its completion handler, so the app must not
    /// also infer abandonment from foreground events.
    var reportsCancellation: Bool { true }

    /// The external label is required: Kotlin exports this as the selector
    /// `openOAuthUrlUrl:`, which `openOAuthUrl(_ url:)` would not satisfy.
    func openOAuthUrl(url: String) {
        // `startOAuthFlow` is a plain non-suspend method on the shared AuthCoordinator
        // interface, so a future caller could reach it off the main thread. UIKit cannot.
        if Thread.isMainThread {
            present(url)
        } else {
            DispatchQueue.main.async { [weak self] in self?.present(url) }
        }
    }

    private func present(_ url: String) {
        guard let authURL = URL(string: url) else {
            fail("Malformed authorization URL")
            return
        }
        // Resolve the anchor before starting: `presentationAnchor(for:)` cannot express
        // failure, and handing back an empty UIWindow makes the session fail opaquely.
        guard anchor() != nil else {
            fail("No window available to present the login page")
            return
        }

        // A fresh session per attempt — a second start() on the same instance returns false.
        let session = ASWebAuthenticationSession(
            url: authURL,
            callback: .customScheme(KmpHelper.shared.oauthCallbackScheme())
        ) { [weak self] callbackURL, error in
            self?.session = nil
            self?.finish(callbackURL: callbackURL, error: error)
        }
        session.presentationContextProvider = self
        // Share Safari's cookies: a user already signed in to their Home Assistant gets a
        // one-tap approve instead of retyping credentials and MFA. The cost is the system
        // consent alert on each attempt, whose Cancel arrives as .canceledLogin below.
        session.prefersEphemeralWebBrowserSession = false
        self.session = session

        // start() returning false never invokes the completion handler, so an unhandled
        // false leaves the user on a spinner that nothing can clear.
        guard session.start() else {
            self.session = nil
            fail("Could not present the login page")
            return
        }
    }

    private func finish(callbackURL: URL?, error: Error?) {
        if let callbackURL {
            // Parsing and dispatch live in Kotlin so all three delivery paths — this
            // session, the iOS deep link and Android's launch intent — behave identically.
            let handled = KmpHelper.shared.authManager
                .handleOAuthCallbackUrl(urlString: callbackURL.absoluteString)
            if !handled {
                // The session only ever calls back on our own scheme, so this means the
                // callback shape changed under us. Surface it rather than hang.
                fail("Unexpected OAuth callback")
            }
            return
        }

        // A dismissal is not a failure: drop back to the login screen without an error.
        if let error = error as? ASWebAuthenticationSessionError, error.code == .canceledLogin {
            cancel(nil)
            return
        }
        fail(error?.localizedDescription ?? "Login was not completed")
    }

    private func fail(_ reason: String) {
        NativeLog.shared.error(tag: "OAuthWebSession", message: reason)
        cancel(reason)
    }

    private func cancel(_ reason: String?) {
        KmpHelper.shared.authManager.cancelOAuthFlow(reason: reason)
    }

    /// The app registers a `CPTemplateApplicationScene`, which is not a `UIWindowScene`,
    /// and `UIApplicationSupportsMultipleScenes` is true — so filter by type rather than
    /// taking the first connected scene.
    private func anchor() -> UIWindow? {
        let windowScenes = UIApplication.shared.connectedScenes.compactMap { $0 as? UIWindowScene }
        let scene = windowScenes.first { $0.activationState == .foregroundActive }
            ?? windowScenes.first { $0.activationState == .foregroundInactive }
        return scene?.keyWindow
    }

    func presentationAnchor(for session: ASWebAuthenticationSession) -> ASPresentationAnchor {
        // Non-optional by contract. present() already refused to start without a window,
        // so reaching the fallback means the window went away mid-presentation.
        anchor() ?? ASPresentationAnchor()
    }
}
