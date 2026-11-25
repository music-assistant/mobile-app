package io.music_assistant.client.watchlink

import co.touchlab.kermit.Logger
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCObjectVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.value
import platform.Foundation.NSError
import platform.WatchConnectivity.WCSession
import platform.WatchConnectivity.WCSessionActivationState
import platform.WatchConnectivity.WCSessionActivationStateActivated
import platform.WatchConnectivity.WCSessionDelegateProtocol
import platform.darwin.NSObject
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue

/**
 * Sends the [WatchLinkState] to the watchOS app as the WatchConnectivity application context.
 *
 * The system keeps the last context and hands it to the watch app when it next runs, also if
 * the watch was off when the phone sent it. Sending needs an activated session, a paired watch
 * and the watch app installed, so the link keeps the last state and sends it again once all
 * three hold. It is the only `WCSession` delegate of the iOS app.
 */
class WatchConnectivityLink : WatchLink {
    private val delegate = Delegate()

    /** The last state, sent again on activation and when the watch app gets installed. */
    private var pending: WatchLinkState? = null

    init {
        if (WCSession.isSupported()) {
            WCSession.defaultSession.delegate = delegate
            WCSession.defaultSession.activateSession()
        }
    }

    override fun publish(state: WatchLinkState) {
        pending = state
        sendPending()
    }

    @OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
    private fun sendPending() {
        val state = pending ?: return
        if (!WCSession.isSupported()) return
        val session = WCSession.defaultSession
        if (session.activationState != WCSessionActivationStateActivated) return
        if (!session.paired || !session.watchAppInstalled) return
        val context: Map<Any?, Any?> = WatchLinkContract.toMap(state).mapKeys { it.key }
        memScoped {
            val error = alloc<ObjCObjectVar<NSError?>>()
            val sent = session.updateApplicationContext(context, error.ptr)
            if (!sent) log.w { "Could not send the watch config: ${error.value?.localizedDescription}" }
        }
    }

    private inner class Delegate : NSObject(), WCSessionDelegateProtocol {
        override fun session(
            session: WCSession,
            activationDidCompleteWithState: WCSessionActivationState,
            error: NSError?,
        ) {
            error?.let { log.w { "WatchConnectivity activation failed: ${it.localizedDescription}" } }
            dispatch_async(dispatch_get_main_queue()) { sendPending() }
        }

        /** Fires when the watch app gets installed or the user pairs a different watch. */
        override fun sessionWatchStateDidChange(session: WCSession) {
            dispatch_async(dispatch_get_main_queue()) { sendPending() }
        }

        override fun sessionDidBecomeInactive(session: WCSession) = Unit

        /** The user switched watches: activate again for the new one. */
        override fun sessionDidDeactivate(session: WCSession) {
            session.activateSession()
        }
    }

    private companion object {
        val log = Logger.withTag("WatchLink")
    }
}
